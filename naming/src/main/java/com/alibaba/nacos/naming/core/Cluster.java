/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.naming.core;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.HealthCheckStatus;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class Cluster extends com.alibaba.nacos.api.naming.pojo.Cluster implements Cloneable {

    private static final String CLUSTER_NAME_SYNTAX = "[0-9a-zA-Z-]+";

    /**
     * in fact this is CIDR(Classless Inter-Domain Routing). for naming it 'submask' it has historical reasons
     */
    private String submask = "0.0.0.0/0";
    /**
     * a addition for same site routing, can group multiple sites into a region, like Hangzhou, Shanghai, etc.
     */
    private String sitegroup = StringUtils.EMPTY;

    private int defCkport = 80;

    private int defIPPort = -1;

    @JSONField(name = "nodegroup")
    private String legacySyncConfig;

    @JSONField(name = "healthChecker")
    private AbstractHealthChecker healthChecker = new AbstractHealthChecker.Tcp();

    @JSONField(serialize = false)
    private HealthCheckTask checkTask;

    @JSONField(serialize = false)
    private Set<IpAddress> raftIPs = new HashSet<IpAddress>();

    @JSONField(serialize = false)
    private Domain dom;

    private Map<String, Boolean> ipContains = new ConcurrentHashMap<>();

    private Map<String, String> metadata = new ConcurrentHashMap<>();

    public Cluster() {
    }

    public Cluster(String clusterName) {
        this.setName(clusterName);
    }

    public int getDefIPPort() {
        // for compatibility with old entries
        return defIPPort == -1 ? defCkport : defIPPort;
    }

    public void setDefIPPort(int defIPPort) {
        if (defIPPort == 0) {
            throw new IllegalArgumentException("defIPPort can not be 0");
        }
        this.defIPPort = defIPPort;
    }

    public List<IpAddress> allIPs() {
        return new ArrayList<IpAddress>(chooseIPs());
    }

    public List<IpAddress> allIPs(String tenant) {

        List<IpAddress> list = new ArrayList<>();
        for (IpAddress ipAddress : chooseIPs()) {
            if (ipAddress.getTenant().equals(tenant)) {
                list.add(ipAddress);
            }
        }
        return list;
    }

    public List<IpAddress> allIPs(String tenant, String app) {

        List<IpAddress> list = new ArrayList<>();
        for (IpAddress ipAddress : chooseIPs()) {
            if (ipAddress.getTenant().equals(tenant) && ipAddress.getApp().equals(app)) {
                list.add(ipAddress);
            }
        }
        return list;
    }

    public void init() {
        checkTask = new HealthCheckTask(this);
        HealthCheckReactor.scheduleCheck(checkTask);
    }

    public void destroy() {
        checkTask.setCancelled(true);
    }

    public void addIP(IpAddress ip) {
        chooseIPs().add(ip);
    }

    public void removeIP(IpAddress ip) {
        chooseIPs().remove(ip);
    }

    public HealthCheckTask getHealthCheckTask() {
        return checkTask;
    }

    public Domain getDom() {
        return dom;
    }

    public void setDom(Domain dom) {
        this.dom = dom;
    }

    public String getLegacySyncConfig() {
        return legacySyncConfig;
    }

    public void setLegacySyncConfig(String nodegroup) {
        this.legacySyncConfig = nodegroup;
    }

    @Override
    public Cluster clone() throws CloneNotSupportedException {
        super.clone();
        Cluster cluster = new Cluster();

        cluster.setHealthChecker(healthChecker.clone());
        cluster.setDom(getDom());
        cluster.raftIPs = new HashSet<IpAddress>();
        cluster.checkTask = null;
        cluster.metadata = new HashMap<>(metadata);
        return cluster;
    }

    public void updateIPs(List<IpAddress> ips) {
        HashMap<String, IpAddress> oldIPMap = new HashMap<>(raftIPs.size());

        for (IpAddress ip : this.raftIPs) {
            oldIPMap.put(ip.getDatumKey(), ip);
        }

        List<IpAddress> updatedIPs = updatedIPs(ips, oldIPMap.values());
        if (updatedIPs.size() > 0) {
            for (IpAddress ip : updatedIPs) {
                IpAddress oldIP = oldIPMap.get(ip.getDatumKey());

                if (responsible(ip)) {
                    // do not update the ip validation status of updated ips
                    // because the checker has the most precise result

                    // Only when ip is not marked, don't we update the health status of IP:
                    if (!ip.isMarked()) {
                        ip.setValid(oldIP.isValid());
                    }

                } else {
                    if (ip.isValid() != oldIP.isValid()) {
                        // ip validation status updated
                        Loggers.EVT_LOG.info("{} {SYNC} IP-{} {}:{}@{}",
                            getDom().getName(), (ip.isValid() ? "ENABLED" : "DISABLED"), ip.getIp(), ip.getPort(), getName());
                    }
                }

                if (ip.getWeight() != oldIP.getWeight()) {
                    // ip validation status updated
                    Loggers.EVT_LOG.info("{} {SYNC} {IP-UPDATED} {}->{}", getDom().getName(), oldIP.toString(), ip.toString());
                }
            }
        }

        List<IpAddress> newIPs = subtract(ips, oldIPMap.values());
        if (newIPs.size() > 0) {
            Loggers.EVT_LOG.info("{} {SYNC} {IP-NEW} cluster: {}, new ips size: {}, content: {}",
                getDom().getName(), getName(), newIPs.size(), newIPs.toString());

            for (IpAddress ip : newIPs) {
                HealthCheckStatus.reset(ip);
            }
        }

        List<IpAddress> deadIPs = subtract(oldIPMap.values(), ips);

        if (deadIPs.size() > 0) {
            Loggers.EVT_LOG.info("{} {SYNC} {IP-DEAD} cluster: {}, dead ips size: {}, content: {}",
                getDom().getName(), getName(), deadIPs.size(), deadIPs.toString());

            for (IpAddress ip : deadIPs) {
                HealthCheckStatus.remv(ip);
            }
        }

        this.raftIPs = new HashSet<IpAddress>(ips);

        StringBuilder stringBuilder = new StringBuilder();
        for (IpAddress ipAddress : raftIPs) {
            stringBuilder.append(ipAddress.toIPAddr()).append(ipAddress.isValid());
        }

        ipContains.clear();

        for (IpAddress ipAddress : raftIPs) {
            ipContains.put(ipAddress.toIPAddr(), true);
        }

    }

    public List<IpAddress> updatedIPs(Collection<IpAddress> a, Collection<IpAddress> b) {

        List<IpAddress> intersects = (List<IpAddress>) CollectionUtils.intersection(a, b);
        Map<String, IpAddress> stringIPAddressMap = new ConcurrentHashMap<>(intersects.size());

        for (IpAddress ipAddress : intersects) {
            stringIPAddressMap.put(ipAddress.getIp() + ":" + ipAddress.getPort(), ipAddress);
        }

        Map<String, Integer> intersectMap = new ConcurrentHashMap<>(a.size() + b.size());
        Map<String, IpAddress> ipAddressMap = new ConcurrentHashMap<>(a.size());
        Map<String, IpAddress> ipAddressMap1 = new ConcurrentHashMap<>(b.size());
        Map<String, IpAddress> ipAddressMap2 = new ConcurrentHashMap<>(a.size());

        for (IpAddress ipAddress : b) {
            if (stringIPAddressMap.containsKey(ipAddress.getIp() + ":" + ipAddress.getPort())) {
                intersectMap.put(ipAddress.toString(), 1);
            }
            ipAddressMap1.put(ipAddress.toString(), ipAddress);
        }


        for (IpAddress ipAddress : a) {
            if (stringIPAddressMap.containsKey(ipAddress.getIp() + ":" + ipAddress.getPort())) {

                if (intersectMap.containsKey(ipAddress.toString())) {
                    intersectMap.put(ipAddress.toString(), 2);
                } else {
                    intersectMap.put(ipAddress.toString(), 1);
                }
            }

            ipAddressMap2.put(ipAddress.toString(), ipAddress);

        }

        for (Map.Entry<String, Integer> entry : intersectMap.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            if (value == 1) {
                if (ipAddressMap2.containsKey(key)) {
                    ipAddressMap.put(key, ipAddressMap2.get(key));
                }
            }
        }

        return new ArrayList<>(ipAddressMap.values());
    }

    public List<IpAddress> subtract(Collection<IpAddress> a, Collection<IpAddress> b) {
        Map<String, IpAddress> mapa = new HashMap<>(b.size());
        for (IpAddress o : b) {
            mapa.put(o.getIp() + ":" + o.getPort(), o);
        }

        List<IpAddress> result = new ArrayList<IpAddress>();

        for (IpAddress o : a) {
            if (!mapa.containsKey(o.getIp() + ":" + o.getPort())) {
                result.add(o);
            }
        }

        return result;
    }

    public Set<IpAddress> chooseIPs() {
        return raftIPs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Cluster)) {
            return false;
        }

        return getName().equals(((Cluster) obj).getName());
    }

    public int getDefCkport() {
        return defCkport;
    }

    public void setDefCkport(int defCkport) {
        this.defCkport = defCkport;
    }

    public void update(Cluster cluster) {

        if (!healthChecker.equals(cluster.getHealthChecker())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}:, healthChecker: {} -> {}",
                cluster.getDom().getName(), cluster.getName(), healthChecker.toString(), cluster.getHealthChecker().toString());
            healthChecker = cluster.getHealthChecker();
        }

        if (defCkport != cluster.getDefCkport()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, defCkport: {} -> {}",
                cluster.getDom().getName(), cluster.getName(), defCkport, cluster.getDefCkport());
            defCkport = cluster.getDefCkport();
        }

        if (defIPPort != cluster.getDefIPPort()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, defIPPort: {} -> {}",
                cluster.getDom().getName(), cluster.getName(), defIPPort, cluster.getDefIPPort());
            defIPPort = cluster.getDefIPPort();
        }

        if (!StringUtils.equals(submask, cluster.getSubmask())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, submask: {} -> {}",
                cluster.getDom().getName(), cluster.getName(), submask, cluster.getSubmask());
            submask = cluster.getSubmask();
        }

        if (!StringUtils.equals(sitegroup, cluster.getSitegroup())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, sitegroup: {} -> {}",
                cluster.getDom().getName(), cluster.getName(), sitegroup, cluster.getSitegroup());
            sitegroup = cluster.getSitegroup();
        }

        if (isUseIPPort4Check() != cluster.isUseIPPort4Check()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, useIPPort4Check: {} -> {}",
                cluster.getDom().getName(), cluster.getName(), isUseIPPort4Check(), cluster.isUseIPPort4Check());
            setUseIPPort4Check(cluster.isUseIPPort4Check());
        }

        metadata = cluster.getMetadata();
    }

    public String getSyncKey() {
        return "";
    }

    public String getSubmask() {
        return submask;
    }

    public void setSubmask(String submask) {
        this.submask = submask;
    }

    public String getSitegroup() {
        return sitegroup;
    }

    public void setSitegroup(String sitegroup) {
        this.sitegroup = sitegroup;
    }

    public boolean responsible(IpAddress ip) {
        return Switch.isHealthCheckEnabled(dom.getName())
            && !getHealthCheckTask().isCancelled()
            && DistroMapper.responsible(getDom().getName())
            && ipContains.containsKey(ip.toIPAddr());
    }

    public void valid() {
        if (!getName().matches(CLUSTER_NAME_SYNTAX)) {
            throw new IllegalArgumentException("cluster name can only have these characters: 0-9a-zA-Z-, current: " + getName());
        }

        String[] cidrGroups = submask.split("\\|");
        for (String cidrGroup : cidrGroups) {
            String[] cidrs = cidrGroup.split(",");

            for (String cidr : cidrs) {
                if (!cidr.matches(UtilsAndCommons.CIDR_REGEX)) {
                    throw new IllegalArgumentException("malformed submask: " + submask + " for cluster: " + getName());
                }
            }
        }
    }
}
