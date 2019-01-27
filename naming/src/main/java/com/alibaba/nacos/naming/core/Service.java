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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.boot.SpringContext;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.healthcheck.ClientBeatCheckTask;
import com.alibaba.nacos.naming.healthcheck.ClientBeatProcessor;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.Selector;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.*;

/**
 * Service of Nacos
 * <p>
 * We introduce a 'service --> cluster --> instance' model, so service stores a list of clusters, which contains
 * a list of instances.
 * <p>
 * Typically we put some common properties between instances to service level.
 *
 * @author nkorange
 */
public class Service extends com.alibaba.nacos.api.naming.pojo.Service implements DataListener<Instances> {

    private static final String SERVICE_NAME_SYNTAX = "[0-9a-zA-Z\\.:_-]+";

    @JSONField(serialize = false)
    private ClientBeatProcessor clientBeatProcessor = new ClientBeatProcessor();

    @JSONField(serialize = false)
    private ClientBeatCheckTask clientBeatCheckTask = new ClientBeatCheckTask(this);

    private String token;
    private List<String> owners = new ArrayList<>();
    private Boolean resetWeight = false;
    private Boolean enabled = true;
    private Selector selector = new NoneSelector();
    private String namespaceId;

    /**
     * IP will be deleted if it has not send beat for some time, default timeout is 30 seconds.
     */
    private long ipDeleteTimeout = 30 * 1000;

    private volatile long lastModifiedMillis = 0L;

    private volatile String checksum;

    private Map<String, Cluster> clusterMap = new HashMap<String, Cluster>();

    @JSONField(serialize = false)
    public PushService getPushService() {
        return SpringContext.getAppContext().getBean(PushService.class);
    }

    public long getIpDeleteTimeout() {
        return ipDeleteTimeout;
    }

    public void setIpDeleteTimeout(long ipDeleteTimeout) {
        this.ipDeleteTimeout = ipDeleteTimeout;
    }

    public void processClientBeat(final RsInfo rsInfo) {
        clientBeatProcessor.setService(this);
        clientBeatProcessor.setRsInfo(rsInfo);
        HealthCheckReactor.scheduleNow(clientBeatProcessor);
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }


    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    public void setLastModifiedMillis(long lastModifiedMillis) {
        this.lastModifiedMillis = lastModifiedMillis;
    }

    public Boolean getResetWeight() {
        return resetWeight;
    }

    public void setResetWeight(Boolean resetWeight) {
        this.resetWeight = resetWeight;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    @Override
    public boolean interests(String key) {
        return KeyBuilder.matchInstanceListKey(key, namespaceId, getName());
//        return StringUtils.equals(key, UtilsAndCommons.IPADDRESS_DATA_ID_PRE + namespaceId + UtilsAndCommons.SERVICE_GROUP_CONNECTOR + getName());
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return KeyBuilder.matchInstanceListKey(key, namespaceId, getName());
//        return StringUtils.equals(key, UtilsAndCommons.IPADDRESS_DATA_ID_PRE + namespaceId + UtilsAndCommons.SERVICE_GROUP_CONNECTOR + getName());
    }

    @Override
    public void onChange(String key, Instances value) throws Exception {

        Loggers.RAFT.info("[NACOS-RAFT] datum is changed, key: {}, value: {}", key, value);

        for (Instance ip : value.getInstanceMap().values()) {

            if (ip.getWeight() > 10000.0D) {
                ip.setWeight(10000.0D);
            }

            if (ip.getWeight() < 0.01D && ip.getWeight() > 0.0D) {
                ip.setWeight(0.01D);
            }
        }

        updateIPs(value.getInstanceMap().values(), KeyBuilder.matchEphemeralInstanceListKey(key));

        recalculateChecksum();
    }

    @Override
    public void onDelete(String key) throws Exception {
        // ignore
    }

    public void updateIPs(Collection<Instance> ips, boolean ephemeral) {
        if (CollectionUtils.isEmpty(ips) && allIPs().size() > 1) {
            return;
        }

        Map<String, List<Instance>> ipMap = new HashMap<String, List<Instance>>(clusterMap.size());
        for (String clusterName : clusterMap.keySet()) {
            ipMap.put(clusterName, new ArrayList<Instance>());
        }

        for (Instance ip : ips) {
            try {
                if (ip == null) {
                    Loggers.SRV_LOG.error("[NACOS-DOM] received malformed ip: null");
                    continue;
                }

                if (StringUtils.isEmpty(ip.getClusterName())) {
                    ip.setClusterName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
                }

                // put wild ip into DEFAULT cluster
                if (!clusterMap.containsKey(ip.getClusterName())) {
                    Loggers.SRV_LOG.warn("cluster of IP not found: {}", ip);
                    continue;
                }

                List<Instance> clusterIPs = ipMap.get(ip.getClusterName());
                if (clusterIPs == null) {
                    clusterIPs = new LinkedList<>();
                    ipMap.put(ip.getClusterName(), clusterIPs);
                }

                clusterIPs.add(ip);
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[NACOS-DOM] failed to process ip: " + ip, e);
            }
        }

        for (Map.Entry<String, List<Instance>> entry : ipMap.entrySet()) {
            //make every ip mine
            List<Instance> entryIPs = entry.getValue();
            clusterMap.get(entry.getKey()).updateIPs(entryIPs, ephemeral);
        }

        setLastModifiedMillis(System.currentTimeMillis());
        getPushService().domChanged(namespaceId, getName());
        StringBuilder stringBuilder = new StringBuilder();

        for (Instance instance : allIPs()) {
            stringBuilder.append(instance.toIPAddr()).append("_").append(instance.isValid()).append(",");
        }

        Loggers.EVT_LOG.info("[IP-UPDATED] dom: {}, ips: {}", getName(), stringBuilder.toString());

    }

    public void init() {

        HealthCheckReactor.scheduleCheck(clientBeatCheckTask);

        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            entry.getValue().init();
        }
    }

    public void destroy() throws Exception {
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            entry.getValue().destroy();
        }
        HealthCheckReactor.cancelCheck(clientBeatCheckTask);
    }

    public List<Instance> allIPs() {
        List<Instance> allIPs = new ArrayList<>();
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            allIPs.addAll(entry.getValue().allIPs());
        }

        return allIPs;
    }

    public List<Instance> allIPs(boolean ephemeral) {
        List<Instance> allIPs = new ArrayList<>();
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            allIPs.addAll(entry.getValue().allIPs(ephemeral));
        }

        return allIPs;
    }

    public List<Instance> allIPs(List<String> clusters) {
        List<Instance> allIPs = new ArrayList<>();
        for (String cluster : clusters) {
            Cluster clusterObj = clusterMap.get(cluster);
            if (clusterObj == null) {
                throw new IllegalArgumentException("can not find cluster: " + cluster + ", dom:" + getName());
            }

            allIPs.addAll(clusterObj.allIPs());
        }

        return allIPs;
    }

    public List<Instance> srvIPs(List<String> clusters) {
        if (CollectionUtils.isEmpty(clusters)) {
            clusters = new ArrayList<>();
            clusters.addAll(clusterMap.keySet());
        }
        return allIPs(clusters);
    }

    public static Service fromJSON(String json) {
        try {
            Service vDom = JSON.parseObject(json, Service.class);
            for (Cluster cluster : vDom.clusterMap.values()) {
                cluster.setDom(vDom);
            }

            return vDom;
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[NACOS-DOM] parse cluster json content: {}, error: {}", json, e);
            return null;
        }
    }

    public String toJSON() {
        return JSON.toJSONString(this);
    }

    @JSONField(serialize = false)
    public String getDomString() {
        Map<Object, Object> domain = new HashMap<Object, Object>(10);
        Service vDom = this;

        domain.put("name", vDom.getName());

        List<Instance> ips = vDom.allIPs();
        int invalidIPCount = 0;
        int ipCount = 0;
        for (Instance ip : ips) {
            if (!ip.isValid()) {
                invalidIPCount++;
            }

            ipCount++;
        }

        domain.put("ipCount", ipCount);
        domain.put("invalidIPCount", invalidIPCount);

        domain.put("owners", vDom.getOwners());
        domain.put("token", vDom.getToken());

        domain.put("protectThreshold", vDom.getProtectThreshold());

        List<Object> clustersList = new ArrayList<Object>();

        for (Map.Entry<String, Cluster> entry : vDom.getClusterMap().entrySet()) {
            Cluster cluster = entry.getValue();

            Map<Object, Object> clusters = new HashMap<Object, Object>(10);
            clusters.put("name", cluster.getName());
            clusters.put("healthChecker", cluster.getHealthChecker());
            clusters.put("defCkport", cluster.getDefCkport());
            clusters.put("defIPPort", cluster.getDefIPPort());
            clusters.put("useIPPort4Check", cluster.isUseIPPort4Check());
            clusters.put("submask", cluster.getSubmask());
            clusters.put("sitegroup", cluster.getSitegroup());

            clustersList.add(clusters);
        }

        domain.put("clusters", clustersList);

        return JSON.toJSONString(domain);
    }


    public void setName(String name) {
        if (!name.matches(SERVICE_NAME_SYNTAX)) {
            throw new IllegalArgumentException("dom name can only have these characters: 0-9a-zA-Z.:_-; current: " + name);
        }

        super.setName(name);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<String> getOwners() {
        return owners;
    }

    public void setOwners(List<String> owners) {
        this.owners = owners;
    }

    public Map<String, Cluster> getClusterMap() {
        return clusterMap;
    }

    public void setClusterMap(Map<String, Cluster> clusterMap) {
        this.clusterMap = clusterMap;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public void update(Service vDom) {

        if (!StringUtils.equals(token, vDom.getToken())) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, token: {} -> {}", getName(), token, vDom.getToken());
            token = vDom.getToken();
        }

        if (!ListUtils.isEqualList(owners, vDom.getOwners())) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, owners: {} -> {}", getName(), owners, vDom.getOwners());
            owners = vDom.getOwners();
        }

        if (getProtectThreshold() != vDom.getProtectThreshold()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, protectThreshold: {} -> {}", getName(), getProtectThreshold(), vDom.getProtectThreshold());
            setProtectThreshold(vDom.getProtectThreshold());
        }

        if (resetWeight != vDom.getResetWeight().booleanValue()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, resetWeight: {} -> {}", getName(), resetWeight, vDom.getResetWeight());
            resetWeight = vDom.getResetWeight();
        }

        if (enabled != vDom.getEnabled().booleanValue()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, enabled: {} -> {}", getName(), enabled, vDom.getEnabled());
            enabled = vDom.getEnabled();
        }

        selector = vDom.getSelector();

        setMetadata(vDom.getMetadata());

        updateOrAddCluster(vDom.getClusterMap().values());
        remvDeadClusters(this, vDom);
        recalculateChecksum();
    }

    public String getChecksum() {
        if (StringUtils.isEmpty(checksum)) {
            recalculateChecksum();
        }

        return checksum;
    }

    public synchronized void recalculateChecksum() {
        List<Instance> ips = allIPs();

        StringBuilder ipsString = new StringBuilder();
        ipsString.append(getDomString());

        Loggers.SRV_LOG.debug("dom to json: " + getDomString());

        if (!CollectionUtils.isEmpty(ips)) {
            Collections.sort(ips);
        }

        for (Instance ip : ips) {
            String string = ip.getIp() + ":" + ip.getPort() + "_" + ip.getWeight() + "_"
                + ip.isValid() + "_" + ip.getClusterName();
            ipsString.append(string);
            ipsString.append(",");
        }

        try {
            String result;
            try {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                result = new BigInteger(1, md5.digest((ipsString.toString()).getBytes(Charset.forName("UTF-8")))).toString(16);
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[NACOS-DOM] error while calculating checksum(md5)", e);
                result = RandomStringUtils.randomAscii(32);
            }

            checksum = result;
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[NACOS-DOM] error while calculating checksum(md5)", e);
            checksum = RandomStringUtils.randomAscii(32);
        }
    }

    private void updateOrAddCluster(Collection<Cluster> clusters) {
        for (Cluster cluster : clusters) {
            Cluster oldCluster = clusterMap.get(cluster.getName());
            if (oldCluster != null) {
                oldCluster.update(cluster);
            } else {
                cluster.init();
                clusterMap.put(cluster.getName(), cluster);
            }
        }
    }

    private void remvDeadClusters(Service oldDom, Service newDom) {
        Collection<Cluster> oldClusters = oldDom.getClusterMap().values();
        Collection<Cluster> newClusters = newDom.getClusterMap().values();
        List<Cluster> deadClusters = (List<Cluster>) CollectionUtils.subtract(oldClusters, newClusters);
        for (Cluster cluster : deadClusters) {
            oldDom.getClusterMap().remove(cluster.getName());

            cluster.destroy();
        }
    }

    public void addCluster(Cluster cluster) {
        clusterMap.put(cluster.getName(), cluster);
    }

    public void valid() {
        if (!getName().matches(SERVICE_NAME_SYNTAX)) {
            throw new IllegalArgumentException("dom name can only have these characters: 0-9a-zA-Z-._:, current: " + getName());
        }

        Map<String, List<String>> map = new HashMap<>(clusterMap.size());
        for (Cluster cluster : clusterMap.values()) {
            if (StringUtils.isEmpty(cluster.getSyncKey())) {
                continue;
            }
            List<String> list = map.get(cluster.getSyncKey());
            if (list == null) {
                list = new ArrayList<>();
                map.put(cluster.getSyncKey(), list);
            }

            list.add(cluster.getName());
            cluster.validate();
        }

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            List<String> list = entry.getValue();
            if (list.size() > 1) {
                String msg = "clusters' config can not be the same: " + list;
                Loggers.SRV_LOG.warn(msg);
                throw new IllegalArgumentException(msg);
            }
        }
    }
}
