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
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.HealthCheckStatus;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nkorange
 */
public class Cluster extends com.alibaba.nacos.api.naming.pojo.Cluster implements Cloneable {

    private static final String CLUSTER_NAME_SYNTAX = "[0-9a-zA-Z-]+";
    /**
     * a addition for same site routing, can group multiple sites into a region, like Hangzhou, Shanghai, etc.
     */
    private String sitegroup = StringUtils.EMPTY;

    private int defCkport = 80;

    private int defIPPort = -1;

    @JSONField(serialize = false)
    private HealthCheckTask checkTask;

    @JSONField(serialize = false)
    private Set<Instance> persistentInstances = new HashSet<>();

    @JSONField(serialize = false)
    private Set<Instance> ephemeralInstances = new HashSet<>();

    @JSONField(serialize = false)
    private Service service;

    @JSONField(serialize = false)
    private volatile boolean inited = false;

    private Map<String, String> metadata = new ConcurrentHashMap<>();

    public Cluster() {
    }

    public Cluster(String clusterName) {
        this.setName(clusterName);
        validate();
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

    public List<Instance> allIPs() {
        List<Instance> allInstances = new ArrayList<>();
        allInstances.addAll(persistentInstances);
        allInstances.addAll(ephemeralInstances);
        return allInstances;
    }

    public List<Instance> allIPs(boolean ephemeral) {
        return ephemeral ? new ArrayList<>(ephemeralInstances) : new ArrayList<>(persistentInstances);
    }

    public void init() {
        if (inited) {
            return;
        }
        checkTask = new HealthCheckTask(this);
        HealthCheckReactor.scheduleCheck(checkTask);
        inited = true;
    }

    public void destroy() {
        if (checkTask != null) {
            checkTask.setCancelled(true);
        }
    }

    public HealthCheckTask getHealthCheckTask() {
        return checkTask;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
        this.setServiceName(service.getName());
    }

    @Override
    public Cluster clone() throws CloneNotSupportedException {
        super.clone();
        Cluster cluster = new Cluster();

        cluster.setHealthChecker(getHealthChecker().clone());
        cluster.setService(getService());
        cluster.persistentInstances = new HashSet<Instance>();
        cluster.checkTask = null;
        cluster.metadata = new HashMap<>(metadata);
        return cluster;
    }

    public void updateIPs(List<Instance> ips, boolean ephemeral) {

        Set<Instance> toUpdateInstances = ephemeral ? ephemeralInstances : persistentInstances;

        HashMap<String, Instance> oldIPMap = new HashMap<>(toUpdateInstances.size());

        for (Instance ip : toUpdateInstances) {
            oldIPMap.put(ip.getDatumKey(), ip);
        }

        List<Instance> updatedIPs = updatedIPs(ips, oldIPMap.values());
        if (updatedIPs.size() > 0) {
            for (Instance ip : updatedIPs) {
                Instance oldIP = oldIPMap.get(ip.getDatumKey());

                // do not update the ip validation status of updated ips
                // because the checker has the most precise result
                // Only when ip is not marked, don't we update the health status of IP:
                if (!ip.isMarked()) {
                    ip.setHealthy(oldIP.isHealthy());
                }

                if (ip.isHealthy() != oldIP.isHealthy()) {
                    // ip validation status updated
                    Loggers.EVT_LOG.info("{} {SYNC} IP-{} {}:{}@{}",
                        getService().getName(), (ip.isHealthy() ? "ENABLED" : "DISABLED"), ip.getIp(), ip.getPort(), getName());
                }

                if (ip.getWeight() != oldIP.getWeight()) {
                    // ip validation status updated
                    Loggers.EVT_LOG.info("{} {SYNC} {IP-UPDATED} {}->{}", getService().getName(), oldIP.toString(), ip.toString());
                }
            }
        }

        List<Instance> newIPs = subtract(ips, oldIPMap.values());
        if (newIPs.size() > 0) {
            Loggers.EVT_LOG.info("{} {SYNC} {IP-NEW} cluster: {}, new ips size: {}, content: {}",
                getService().getName(), getName(), newIPs.size(), newIPs.toString());

            for (Instance ip : newIPs) {
                HealthCheckStatus.reset(ip);
            }
        }

        List<Instance> deadIPs = subtract(oldIPMap.values(), ips);

        if (deadIPs.size() > 0) {
            Loggers.EVT_LOG.info("{} {SYNC} {IP-DEAD} cluster: {}, dead ips size: {}, content: {}",
                getService().getName(), getName(), deadIPs.size(), deadIPs.toString());

            for (Instance ip : deadIPs) {
                HealthCheckStatus.remv(ip);
            }
        }

        toUpdateInstances = new HashSet<>(ips);

        if (ephemeral) {
            ephemeralInstances = toUpdateInstances;
        } else {
            persistentInstances = toUpdateInstances;
        }
    }

    public List<Instance> updatedIPs(Collection<Instance> a, Collection<Instance> b) {

        List<Instance> intersects = (List<Instance>) CollectionUtils.intersection(a, b);
        Map<String, Instance> stringIPAddressMap = new ConcurrentHashMap<>(intersects.size());

        for (Instance instance : intersects) {
            stringIPAddressMap.put(instance.getIp() + ":" + instance.getPort(), instance);
        }

        Map<String, Integer> intersectMap = new ConcurrentHashMap<>(a.size() + b.size());
        Map<String, Instance> instanceMap = new ConcurrentHashMap<>(a.size());
        Map<String, Instance> instanceMap1 = new ConcurrentHashMap<>(a.size());

        for (Instance instance : b) {
            if (stringIPAddressMap.containsKey(instance.getIp() + ":" + instance.getPort())) {
                intersectMap.put(instance.toString(), 1);
            }
        }


        for (Instance instance : a) {
            if (stringIPAddressMap.containsKey(instance.getIp() + ":" + instance.getPort())) {

                if (intersectMap.containsKey(instance.toString())) {
                    intersectMap.put(instance.toString(), 2);
                } else {
                    intersectMap.put(instance.toString(), 1);
                }
            }

            instanceMap1.put(instance.toString(), instance);

        }

        for (Map.Entry<String, Integer> entry : intersectMap.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            if (value == 1) {
                if (instanceMap1.containsKey(key)) {
                    instanceMap.put(key, instanceMap1.get(key));
                }
            }
        }

        return new ArrayList<>(instanceMap.values());
    }

    public List<Instance> subtract(Collection<Instance> a, Collection<Instance> b) {
        Map<String, Instance> mapa = new HashMap<>(b.size());
        for (Instance o : b) {
            mapa.put(o.getIp() + ":" + o.getPort(), o);
        }

        List<Instance> result = new ArrayList<Instance>();

        for (Instance o : a) {
            if (!mapa.containsKey(o.getIp() + ":" + o.getPort())) {
                result.add(o);
            }
        }

        return result;
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

        if (!getHealthChecker().equals(cluster.getHealthChecker())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}:, healthChecker: {} -> {}",
                getService().getName(), getName(), getHealthChecker().toString(), cluster.getHealthChecker().toString());
            setHealthChecker(cluster.getHealthChecker());
        }

        if (defCkport != cluster.getDefCkport()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, defCkport: {} -> {}",
                getService().getName(), getName(), defCkport, cluster.getDefCkport());
            defCkport = cluster.getDefCkport();
        }

        if (defIPPort != cluster.getDefIPPort()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, defIPPort: {} -> {}",
                getService().getName(), getName(), defIPPort, cluster.getDefIPPort());
            defIPPort = cluster.getDefIPPort();
        }

        if (!StringUtils.equals(sitegroup, cluster.getSitegroup())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, sitegroup: {} -> {}",
                getService().getName(), getName(), sitegroup, cluster.getSitegroup());
            sitegroup = cluster.getSitegroup();
        }

        if (isUseIPPort4Check() != cluster.isUseIPPort4Check()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, useIPPort4Check: {} -> {}",
                getService().getName(), getName(), isUseIPPort4Check(), cluster.isUseIPPort4Check());
            setUseIPPort4Check(cluster.isUseIPPort4Check());
        }

        metadata = cluster.getMetadata();
    }

    public String getSitegroup() {
        return sitegroup;
    }

    public void setSitegroup(String sitegroup) {
        this.sitegroup = sitegroup;
    }

    public boolean contains(Instance ip) {
        return persistentInstances.contains(ip) || ephemeralInstances.contains(ip);
    }

    public void validate() {
        if (!getName().matches(CLUSTER_NAME_SYNTAX)) {
            throw new IllegalArgumentException("cluster name can only have these characters: 0-9a-zA-Z-, current: " + getName());
        }
    }
}
