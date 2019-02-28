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
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.healthcheck.ClientBeatCheckTask;
import com.alibaba.nacos.naming.healthcheck.ClientBeatProcessor;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.raft.RaftCore;
import com.alibaba.nacos.naming.raft.RaftListener;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class VirtualClusterDomain implements Domain, RaftListener {

    private static final String DOMAIN_NAME_SYNTAX = "[0-9a-zA-Z\\.:_-]+";

    public static final int MINIMUM_IP_DELETE_TIMEOUT = 60 * 1000;

    @JSONField(serialize = false)
    private ClientBeatProcessor clientBeatProcessor = new ClientBeatProcessor();

    @JSONField(serialize = false)
    private ClientBeatCheckTask clientBeatCheckTask = new ClientBeatCheckTask(this);

    private String name;
    private String token;
    private List<String> owners = new ArrayList<>();
    private Boolean resetWeight = false;
    private Boolean enableHealthCheck = true;
    private Boolean enabled = true;
    private Boolean enableClientBeat = false;
    private Selector selector = new NoneSelector();
    private String namespaceId;

    /**
     * IP will be deleted if it has not send beat for some time, default timeout is 30 seconds.
     */
    private long ipDeleteTimeout = 30 * 1000;

    private volatile long lastModifiedMillis = 0L;

    private boolean useSpecifiedURL = false;

    private float protectThreshold = 0.0F;

    private volatile String checksum;

    private Map<String, Cluster> clusterMap = new HashMap<String, Cluster>();

    private Map<String, String> metadata = new ConcurrentHashMap<>();

    public long getIpDeleteTimeout() {
        return ipDeleteTimeout;
    }

    public void setIpDeleteTimeout(long ipDeleteTimeout) {
        this.ipDeleteTimeout = ipDeleteTimeout;
    }

    public void processClientBeat(final RsInfo rsInfo) {
        clientBeatProcessor.setDomain(this);
        clientBeatProcessor.setRsInfo(rsInfo);
        HealthCheckReactor.scheduleNow(clientBeatProcessor);
    }

    public Boolean getEnableClientBeat() {
        return enableClientBeat;
    }

    public void setEnableClientBeat(Boolean enableClientBeat) {
        this.enableClientBeat = enableClientBeat;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnableHealthCheck() {
        return enableHealthCheck;
    }

    public void setEnableHealthCheck(Boolean enableHealthCheck) {
        this.enableHealthCheck = enableHealthCheck;
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

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public VirtualClusterDomain() {

    }

    @Override
    public boolean interests(String key) {
        return StringUtils.equals(key, UtilsAndCommons.IPADDRESS_DATA_ID_PRE + namespaceId + UtilsAndCommons.SERVICE_GROUP_CONNECTOR + name);
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return StringUtils.equals(key, UtilsAndCommons.IPADDRESS_DATA_ID_PRE + namespaceId + UtilsAndCommons.SERVICE_GROUP_CONNECTOR + name);
    }

    @Override
    public void onChange(String key, String value) throws Exception {

        if (StringUtils.isEmpty(value)) {
            Loggers.SRV_LOG.warn("[NACOS-DOM] received empty iplist config for dom: {}", name);
            return;
        }

        Loggers.RAFT.info("[NACOS-RAFT] datum is changed, key: {}, value: {}", key, value);

        List<IpAddress> ips = JSON.parseObject(value, new TypeReference<List<IpAddress>>() {
        });

        for (IpAddress ip : ips) {

            if (ip.getWeight() > 10000.0D) {
                ip.setWeight(10000.0D);
            }

            if (ip.getWeight() < 0.01D && ip.getWeight() > 0.0D) {
                ip.setWeight(0.01D);
            }
        }

        updateIPs(ips);

        recalculateChecksum();
    }

    @Override
    public void onDelete(String key, String value) throws Exception {
        // ignore
    }

    public void updateIPs(List<IpAddress> ips) {
        if (CollectionUtils.isEmpty(ips) && allIPs().size() > 1) {
            return;
        }


        Map<String, List<IpAddress>> ipMap = new HashMap<String, List<IpAddress>>(clusterMap.size());
        for (String clusterName : clusterMap.keySet()) {
            ipMap.put(clusterName, new ArrayList<IpAddress>());
        }

        for (IpAddress ip : ips) {
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
                    Loggers.SRV_LOG.warn("cluster of IP not found: {}", ip.toJSON());
                    continue;
                }

                List<IpAddress> clusterIPs = ipMap.get(ip.getClusterName());
                if (clusterIPs == null) {
                    clusterIPs = new LinkedList<IpAddress>();
                    ipMap.put(ip.getClusterName(), clusterIPs);
                }

                clusterIPs.add(ip);
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[NACOS-DOM] failed to process ip: " + ip, e);
            }
        }

        for (Map.Entry<String, List<IpAddress>> entry : ipMap.entrySet()) {
            //make every ip mine
            List<IpAddress> entryIPs = entry.getValue();
            clusterMap.get(entry.getKey()).updateIPs(entryIPs);
        }
        setLastModifiedMillis(System.currentTimeMillis());
        PushService.domChanged(namespaceId, name);
        StringBuilder stringBuilder = new StringBuilder();

        for (IpAddress ipAddress : allIPs()) {
            stringBuilder.append(ipAddress.toIPAddr()).append("_").append(ipAddress.isValid()).append(",");
        }

        Loggers.EVT_LOG.info("[IP-UPDATED] dom: {}, ips: {}", getName(), stringBuilder.toString());

    }

    @Override
    public void init() {

        RaftCore.listen(this);
        HealthCheckReactor.scheduleCheck(clientBeatCheckTask);

        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            entry.getValue().init();
        }
    }

    @Override
    public void destroy() throws Exception {
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            entry.getValue().destroy();
        }

        if (RaftCore.isLeader(NetUtils.localServer())) {
            RaftCore.signalDelete(UtilsAndCommons.getIPListStoreKey(this));
        }

        HealthCheckReactor.cancelCheck(clientBeatCheckTask);

        RaftCore.unlisten(UtilsAndCommons.getIPListStoreKey(this));
    }

    @Override
    public List<IpAddress> allIPs() {
        List<IpAddress> allIPs = new ArrayList<IpAddress>();
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            allIPs.addAll(entry.getValue().allIPs());
        }

        return allIPs;
    }

    public List<IpAddress> allIPs(String tenant, String app) {

        List<IpAddress> allIPs = new ArrayList<IpAddress>();
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {

            if (StringUtils.isEmpty(app)) {
                allIPs.addAll(entry.getValue().allIPs(tenant));
            } else {
                allIPs.addAll(entry.getValue().allIPs(tenant, app));
            }
        }

        return allIPs;
    }

    public List<IpAddress> allIPs(List<String> clusters) {
        List<IpAddress> allIPs = new ArrayList<IpAddress>();
        for (String cluster : clusters) {
            Cluster clusterObj = clusterMap.get(cluster);
            if (clusterObj == null) {
                throw new IllegalArgumentException("can not find cluster: " + cluster + ", dom:" + getName());
            }

            allIPs.addAll(clusterObj.allIPs());
        }

        return allIPs;
    }

    @Override
    public List<IpAddress> srvIPs(String clientIP) {
        return srvIPs(clientIP, Collections.EMPTY_LIST);
    }

    public List<IpAddress> srvIPs(String clientIP, List<String> clusters) {
        List<IpAddress> ips;

        if (CollectionUtils.isEmpty(clusters)) {
            clusters = new ArrayList<>();
            clusters.addAll(clusterMap.keySet());
        }
        return allIPs(clusters);
    }

    public static VirtualClusterDomain fromJSON(String json) {
        try {
            VirtualClusterDomain vDom = JSON.parseObject(json, VirtualClusterDomain.class);
            for (Cluster cluster : vDom.clusterMap.values()) {
                cluster.setDom(vDom);
            }

            return vDom;
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[NACOS-DOM] parse cluster json content: {}, error: {}", json, e);
            return null;
        }
    }

    @Override
    public String toJSON() {
        return JSON.toJSONString(this);
    }

    @JSONField(serialize = false)
    public String getDomString() {
        Map<Object, Object> domain = new HashMap<Object, Object>(10);
        VirtualClusterDomain vDom = this;

        domain.put("name", vDom.getName());

        List<IpAddress> ips = vDom.allIPs();
        int invalidIPCount = 0;
        int ipCount = 0;
        for (IpAddress ip : ips) {
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
            clusters.put("sitegroup", cluster.getSitegroup());

            clustersList.add(clusters);
        }

        domain.put("clusters", clustersList);

        return JSON.toJSONString(domain);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        if (!name.matches(DOMAIN_NAME_SYNTAX)) {
            throw new IllegalArgumentException("dom name can only have these characters: 0-9a-zA-Z.:_-; current: " + name);
        }

        this.name = name;
    }

    public boolean isUseSpecifiedURL() {
        return useSpecifiedURL;
    }

    public void setUseSpecifiedURL(boolean isUseSpecifiedURL) {
        this.useSpecifiedURL = isUseSpecifiedURL;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public List<String> getOwners() {
        return owners;
    }

    @Override
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

    @Override
    public void update(Domain dom) {
        if (!(dom instanceof VirtualClusterDomain)) {
            return;
        }

        VirtualClusterDomain vDom = (VirtualClusterDomain) dom;
        if (!StringUtils.equals(token, vDom.getToken())) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, token: {} -> {}", name, token, vDom.getToken());
            token = vDom.getToken();
        }

        if (!ListUtils.isEqualList(owners, vDom.getOwners())) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, owners: {} -> {}", name, owners, vDom.getOwners());
            owners = vDom.getOwners();
        }

        if (protectThreshold != vDom.getProtectThreshold()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, protectThreshold: {} -> {}", name, protectThreshold, vDom.getProtectThreshold());
            protectThreshold = vDom.getProtectThreshold();
        }

        if (useSpecifiedURL != vDom.isUseSpecifiedURL()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, useSpecifiedURL: {} -> {}", name, useSpecifiedURL, vDom.isUseSpecifiedURL());
            useSpecifiedURL = vDom.isUseSpecifiedURL();
        }

        if (resetWeight != vDom.getResetWeight().booleanValue()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, resetWeight: {} -> {}", name, resetWeight, vDom.getResetWeight());
            resetWeight = vDom.getResetWeight();
        }

        if (enableHealthCheck != vDom.getEnableHealthCheck().booleanValue()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, enableHealthCheck: {} -> {}", name, enableHealthCheck, vDom.getEnableHealthCheck());
            enableHealthCheck = vDom.getEnableHealthCheck();
        }

        if (enableClientBeat != vDom.getEnableClientBeat().booleanValue()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, enableClientBeat: {} -> {}", name, enableClientBeat, vDom.getEnableClientBeat());
            enableClientBeat = vDom.getEnableClientBeat();
        }

        if (enabled != vDom.getEnabled().booleanValue()) {
            Loggers.SRV_LOG.info("[DOM-UPDATE] dom: {}, enabled: {} -> {}", name, enabled, vDom.getEnabled());
            enabled = vDom.getEnabled();
        }

        selector = vDom.getSelector();

        metadata = vDom.getMetadata();

        updateOrAddCluster(vDom.getClusterMap().values());
        remvDeadClusters(this, vDom);
        recalculateChecksum();
    }

    @Override
    public String getChecksum() {
        if (StringUtils.isEmpty(checksum)) {
            recalculateChecksum();
        }

        return checksum;
    }

    public synchronized void recalculateChecksum() {
        List<IpAddress> ips = allIPs();

        StringBuilder ipsString = new StringBuilder();
        ipsString.append(getDomString());

        Loggers.SRV_LOG.debug("dom to json: " + getDomString());

        if (!CollectionUtils.isEmpty(ips)) {
            Collections.sort(ips);
        }

        for (IpAddress ip : ips) {
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

    private void remvDeadClusters(VirtualClusterDomain oldDom, VirtualClusterDomain newDom) {
        Collection<Cluster> oldClusters = oldDom.getClusterMap().values();
        Collection<Cluster> newClusters = newDom.getClusterMap().values();
        List<Cluster> deadClusters = (List<Cluster>) CollectionUtils.subtract(oldClusters, newClusters);
        for (Cluster cluster : deadClusters) {
            oldDom.getClusterMap().remove(cluster.getName());

            cluster.destroy();
        }
    }

    @Override
    public float getProtectThreshold() {
        return protectThreshold;
    }

    @Override
    public void setProtectThreshold(float protectThreshold) {
        this.protectThreshold = protectThreshold;
    }

    public void addCluster(Cluster cluster) {
        clusterMap.put(cluster.getName(), cluster);
    }

    public void valid() {
        if (!name.matches(DOMAIN_NAME_SYNTAX)) {
            throw new IllegalArgumentException("dom name can only have these characters: 0-9a-zA-Z-._:, current: " + name);
        }
        for (Cluster cluster : clusterMap.values()) {
            cluster.valid();
        }
    }
}
