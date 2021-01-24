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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.healthcheck.ClientBeatCheckTask;
import com.alibaba.nacos.naming.healthcheck.ClientBeatProcessor;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Record;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.Selector;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Service of Nacos server side
 *
 * <p>We introduce a 'service --> cluster --> instance' model, in which service stores a list of clusters, which
 * contain
 * a list of instances.
 *
 * <p>his class inherits from Service in API module and stores some fields that do not have to expose to client.
 *
 * @author nkorange
 */
@JsonInclude(Include.NON_NULL)
public class Service extends com.alibaba.nacos.api.naming.pojo.Service implements Record, RecordListener<Instances> {

    private static final String SERVICE_NAME_SYNTAX = "[0-9a-zA-Z@\\.:_-]+";

    @JsonIgnore
    private ClientBeatCheckTask clientBeatCheckTask = new ClientBeatCheckTask(this);

    /**
     * Identify the information used to determine how many isEmpty judgments the service has experienced.
     */
    private int finalizeCount = 0;

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

    /**
     * TODO set customized push expire time.
     */
    private long pushCacheMillis = 0L;

    private Map<String, Cluster> clusterMap = new HashMap<>();

    public Service() {
    }

    public Service(String name) {
        super(name);
    }

    @JsonIgnore
    public PushService getPushService() {
        return ApplicationUtils.getBean(PushService.class);
    }

    public long getIpDeleteTimeout() {
        return ipDeleteTimeout;
    }

    public void setIpDeleteTimeout(long ipDeleteTimeout) {
        this.ipDeleteTimeout = ipDeleteTimeout;
    }

    /**
     * Process client beat.
     *处理心跳
     * @param rsInfo metrics info of server
     */
    public void processClientBeat(final RsInfo rsInfo) {
        ClientBeatProcessor clientBeatProcessor = new ClientBeatProcessor();
        clientBeatProcessor.setService(this);
        clientBeatProcessor.setRsInfo(rsInfo);
        /**
         * 定时任务
         */
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
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return KeyBuilder.matchInstanceListKey(key, namespaceId, getName());
    }

    @Override
    public void onChange(String key, Instances value) throws Exception {

        Loggers.SRV_LOG.info("[NACOS-RAFT] datum is changed, key: {}, value: {}", key, value);


        /**
         * 遍历service下得所有instance列表
         */
        for (Instance instance : value.getInstanceList()) {

            if (instance == null) {
                // Reject this abnormal instance list:
                throw new RuntimeException("got null instance " + key);
            }

            if (instance.getWeight() > 10000.0D) {
                instance.setWeight(10000.0D);
            }

            if (instance.getWeight() < 0.01D && instance.getWeight() > 0.0D) {
                instance.setWeight(0.01D);
            }
        }

        /**
         * 更新service下得对应新注册服务得cluster下的instance列表  并触发ServiceChangeEvent
         */
        updateIPs(value.getInstanceList(), KeyBuilder.matchEphemeralInstanceListKey(key));


        /**
         * 重新计算checksum
         */
        recalculateChecksum();
    }

    @Override
    public void onDelete(String key) throws Exception {
        // ignore
    }

    /**
     * Get count of healthy instance in service.
     *
     * @return count of healthy instance
     */
    public int healthyInstanceCount() {

        int healthyCount = 0;
        for (Instance instance : allIPs()) {
            if (instance.isHealthy()) {
                healthyCount++;
            }
        }
        return healthyCount;
    }

    public boolean triggerFlag() {
        return (healthyInstanceCount() * 1.0 / allIPs().size()) <= getProtectThreshold();
    }

    /**
     * Update instances.
     *
     * @param instances instances
     * @param ephemeral whether is ephemeral instance
     */
    public void updateIPs(Collection<Instance> instances, boolean ephemeral) {
        /**
         * 当前service下的cluster
         */
        Map<String, List<Instance>> ipMap = new HashMap<>(clusterMap.size());
        for (String clusterName : clusterMap.keySet()) {
            ipMap.put(clusterName, new ArrayList<>());
        }

        for (Instance instance : instances) {
            try {
                if (instance == null) {
                    Loggers.SRV_LOG.error("[NACOS-DOM] received malformed ip: null");
                    continue;
                }


                /**
                 * instance对应的ClusterName为空   则默认为DEFAULT
                 */
                if (StringUtils.isEmpty(instance.getClusterName())) {
                    instance.setClusterName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
                }


                /**
                 * instance的ClusterName在clusterMap中没有对应  则新增
                 */
                if (!clusterMap.containsKey(instance.getClusterName())) {
                    Loggers.SRV_LOG
                            .warn("cluster: {} not found, ip: {}, will create new cluster with default configuration.",
                                    instance.getClusterName(), instance.toJson());
                    Cluster cluster = new Cluster(instance.getClusterName(), this);
                    /**
                     * 初始化   ？？？？
                     */
                    cluster.init();
                    /**
                     * 存入clusterMap
                     */
                    getClusterMap().put(instance.getClusterName(), cluster);
                }


                /**
                 * 在ipMap也没有对应时   再次新增
                 */
                List<Instance> clusterIPs = ipMap.get(instance.getClusterName());
                if (clusterIPs == null) {
                    clusterIPs = new LinkedList<>();
                    ipMap.put(instance.getClusterName(), clusterIPs);
                }


                /**
                 * 将instance存入ClusterName对应的ipMap中
                 */
                clusterIPs.add(instance);
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[NACOS-DOM] failed to process ip: " + instance, e);
            }
        }

        for (Map.Entry<String, List<Instance>> entry : ipMap.entrySet()) {
            //make every ip mine
            List<Instance> entryIPs = entry.getValue();
            /**
             * 对clusterMap下的Cluster   执行updateIPs
             * 对clusterMap下的Cluster   执行updateIPs
             * 对clusterMap下的Cluster   执行updateIPs
             */
            clusterMap.get(entry.getKey()).updateIps(entryIPs, ephemeral);
        }

        setLastModifiedMillis(System.currentTimeMillis());
        getPushService().serviceChanged(this);
        StringBuilder stringBuilder = new StringBuilder();

        for (Instance instance : allIPs()) {
            stringBuilder.append(instance.toIpAddr()).append("_").append(instance.isHealthy()).append(",");
        }

        Loggers.EVT_LOG.info("[IP-UPDATED] namespace: {}, service: {}, ips: {}", getNamespaceId(), getName(),
                stringBuilder.toString());

    }

    /**
     * Init service.
     */
    public void init() {
        /**
         * 检查不健康的实例  并发出ServiceChangeEvent
         * 检查待删除的实例
         */
        HealthCheckReactor.scheduleCheck(clientBeatCheckTask);
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            entry.getValue().setService(this);
            /**
             * HealthCheckTask   默认tcp方式   处理Cluster下的持久化instance
             */
            entry.getValue().init();
        }
    }

    /**
     * Destroy service.
     *
     * @throws Exception exception
     */
    public void destroy() throws Exception {
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            entry.getValue().destroy();
        }
        HealthCheckReactor.cancelCheck(clientBeatCheckTask);
    }

    /**
     * Judge whether service has instance.
     *
     * @return true if no instance, otherwise false
     */
    public boolean isEmpty() {
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            final Cluster cluster = entry.getValue();
            if (!cluster.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get all instance.
     *获取在nacos注册的所有节点
     * @return list of all instance
     */
    public List<Instance> allIPs() {
        List<Instance> result = new ArrayList<>();
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            /**
             * 获取Cluster对应的节点
             */
            result.addAll(entry.getValue().allIPs());
        }

        return result;
    }

    /**
     * Get all instance of ephemeral or consistency.
     *获取当前server注册到nacos的全部节点
     * @param ephemeral whether ephemeral instance
     * @return all instance of ephemeral if @param ephemeral = true, otherwise all instance of consistency
     */
    public List<Instance> allIPs(boolean ephemeral) {
        List<Instance> result = new ArrayList<>();
        /**
         * 当前server下的Cluster
         */
        for (Map.Entry<String, Cluster> entry : clusterMap.entrySet()) {
            /**
             * 获取Cluster下的节点
             */
            result.addAll(entry.getValue().allIPs(ephemeral));
        }

        return result;
    }

    /**
     * Get all instance from input clusters.
     *查询clusters下的所有Instance
     * @param clusters cluster names
     * @return all instance from input clusters.
     */
    public List<Instance> allIPs(List<String> clusters) {
        List<Instance> result = new ArrayList<>();
        for (String cluster : clusters) {
            Cluster clusterObj = clusterMap.get(cluster);
            if (clusterObj == null) {
                continue;
            }
            /**
             * 查询clusters下的所有Instance  临时与持久化
             */
            result.addAll(clusterObj.allIPs());
        }
        return result;
    }

    /**
     * Get all instance from input clusters.
     *查询clusters下的所有Instance
     * @param clusters cluster names
     * @return all instance from input clusters, if clusters is empty, return all cluster
     */
    public List<Instance> srvIPs(List<String> clusters) {
        /**
         * clusters为空  则查询service下的全部cluster
         */
        if (CollectionUtils.isEmpty(clusters)) {
            clusters = new ArrayList<>();
            clusters.addAll(clusterMap.keySet());
        }
        /**
         * 查询clusters集合下的所有Instance  临时与持久化
         */
        return allIPs(clusters);
    }

    public String toJson() {
        return JacksonUtils.toJson(this);
    }

    /**
     * 将server转成string
     * @return
     */
    @JsonIgnore
    public String getServiceString() {
        Map<Object, Object> serviceObject = new HashMap<Object, Object>(10);
        Service service = this;

        serviceObject.put("name", service.getName());
        /**
         * 获取在nacos注册的所有节点
         */
        List<Instance> ips = service.allIPs();
        /**
         * 不健康的实例数
         */
        int invalidIpCount = 0;
        /**
         * 总实例数
         */
        int ipCount = 0;
        for (Instance ip : ips) {
            if (!ip.isHealthy()) {
                invalidIpCount++;
            }

            ipCount++;
        }

        serviceObject.put("ipCount", ipCount);
        serviceObject.put("invalidIPCount", invalidIpCount);

        serviceObject.put("owners", service.getOwners());
        serviceObject.put("token", service.getToken());

        serviceObject.put("protectThreshold", service.getProtectThreshold());

        List<Object> clustersList = new ArrayList<Object>();

        for (Map.Entry<String, Cluster> entry : service.getClusterMap().entrySet()) {
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

        serviceObject.put("clusters", clustersList);

        try {
            return JacksonUtils.toJson(serviceObject);
        } catch (Exception e) {
            throw new RuntimeException("Service toJson failed", e);
        }
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

    /**
     * Update from other service.
     *更新缓存中的Service
     * @param vDom other service
     */
    public void update(Service vDom) {
        /**
         * 更新缓存中的数据
         */
        if (!StringUtils.equals(token, vDom.getToken())) {
            Loggers.SRV_LOG.info("[SERVICE-UPDATE] service: {}, token: {} -> {}", getName(), token, vDom.getToken());
            token = vDom.getToken();
        }

        if (!ListUtils.isEqualList(owners, vDom.getOwners())) {
            Loggers.SRV_LOG.info("[SERVICE-UPDATE] service: {}, owners: {} -> {}", getName(), owners, vDom.getOwners());
            owners = vDom.getOwners();
        }

        if (getProtectThreshold() != vDom.getProtectThreshold()) {
            Loggers.SRV_LOG
                    .info("[SERVICE-UPDATE] service: {}, protectThreshold: {} -> {}", getName(), getProtectThreshold(),
                            vDom.getProtectThreshold());
            setProtectThreshold(vDom.getProtectThreshold());
        }

        if (resetWeight != vDom.getResetWeight().booleanValue()) {
            Loggers.SRV_LOG.info("[SERVICE-UPDATE] service: {}, resetWeight: {} -> {}", getName(), resetWeight,
                    vDom.getResetWeight());
            resetWeight = vDom.getResetWeight();
        }

        if (enabled != vDom.getEnabled().booleanValue()) {
            Loggers.SRV_LOG
                    .info("[SERVICE-UPDATE] service: {}, enabled: {} -> {}", getName(), enabled, vDom.getEnabled());
            enabled = vDom.getEnabled();
        }

        selector = vDom.getSelector();

        setMetadata(vDom.getMetadata());
        /**
         * 新增或修改Cluster
         */
        updateOrAddCluster(vDom.getClusterMap().values());
        /**
         * 删除Clusters
         */
        remvDeadClusters(this, vDom);

        Loggers.SRV_LOG.info("cluster size, new: {}, old: {}", getClusterMap().size(), vDom.getClusterMap().size());
        /**
         * 重新计算checksum
         */
        recalculateChecksum();
    }

    @Override
    public String getChecksum() {
        if (StringUtils.isEmpty(checksum)) {
            recalculateChecksum();
        }

        return checksum;
    }

    /**
     * Re-calculate checksum of service.
     */
    public synchronized void recalculateChecksum() {
        /**
         * 获取在nacos注册的所有节点
         */
        List<Instance> ips = allIPs();

        StringBuilder ipsString = new StringBuilder();
        /**
         * 将server转成string
         */
        ipsString.append(getServiceString());

        if (Loggers.SRV_LOG.isDebugEnabled()) {
            Loggers.SRV_LOG.debug("service to json: " + getServiceString());
        }

        if (CollectionUtils.isNotEmpty(ips)) {
            Collections.sort(ips);
        }
        /**
         * Instance转String
         */
        for (Instance ip : ips) {
            String string = ip.getIp() + ":" + ip.getPort() + "_" + ip.getWeight() + "_" + ip.isHealthy() + "_" + ip
                    .getClusterName();
            ipsString.append(string);
            ipsString.append(",");
        }
        /**
         * md5加密
         */
        checksum = MD5Utils.md5Hex(ipsString.toString(), Constants.ENCODE);
    }
    /**
     * 新增或修改Cluster
     * @param clusters
     */
    private void updateOrAddCluster(Collection<Cluster> clusters) {
        /**
         * 判断clusters中的数据是否已经在clusterMap中有对应
         * 有则修改  无则新增
         */
        for (Cluster cluster : clusters) {
            Cluster oldCluster = clusterMap.get(cluster.getName());
            if (oldCluster != null) {
                oldCluster.setService(this);
                /**
                 * 更新数据
                 */
                oldCluster.update(cluster);
            } else {
                /**
                 * 设置HealthCheckTask
                 */
                cluster.init();
                cluster.setService(this);
                clusterMap.put(cluster.getName(), cluster);
            }
        }
    }


    /**
     * 移除Clusters
     * @param oldDom
     * @param newDom
     */
    private void remvDeadClusters(Service oldDom, Service newDom) {
        Collection<Cluster> oldClusters = oldDom.getClusterMap().values();
        Collection<Cluster> newClusters = newDom.getClusterMap().values();
        /**
         * 过滤oldClusters存在但newClusters不存在的Cluster
         */
        List<Cluster> deadClusters = (List<Cluster>) CollectionUtils.subtract(oldClusters, newClusters);
        for (Cluster cluster : deadClusters) {
            /**
             * 缓存中删除
             */
            oldDom.getClusterMap().remove(cluster.getName());
            /**
             * 设置业务删除标志
             */
            cluster.destroy();
        }
    }

    public int getFinalizeCount() {
        return finalizeCount;
    }

    public void setFinalizeCount(int finalizeCount) {
        this.finalizeCount = finalizeCount;
    }

    public void addCluster(Cluster cluster) {
        clusterMap.put(cluster.getName(), cluster);
    }

    /**
     * Judge whether service is validate.
     *
     * @throws IllegalArgumentException if service is not validate
     */
    public void validate() {
        if (!getName().matches(SERVICE_NAME_SYNTAX)) {
            throw new IllegalArgumentException(
                    "dom name can only have these characters: 0-9a-zA-Z-._:, current: " + getName());
        }
        for (Cluster cluster : clusterMap.values()) {
            cluster.validate();
        }
    }
}
