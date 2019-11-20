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
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Core manager storing all services in Nacos
 *
 * @author nkorange
 */
@Component
@DependsOn("nacosApplicationContext")
public class ServiceManager implements RecordListener<Service> {

    /**
     * Map<namespace, Map<group::serviceName, Service>>
     */
    private Map<String, Map<String, Service>> serviceMap = new ConcurrentHashMap<>();

    private LinkedBlockingDeque<ServiceKey> toBeUpdatedServicesQueue = new LinkedBlockingDeque<>(1024 * 1024);

    private Synchronizer synchronizer = new ServiceStatusSynchronizer();

    private final Lock lock = new ReentrantLock();

    @Resource(name = "consistencyDelegate")
    private ConsistencyService consistencyService;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private ServerListManager serverListManager;

    @Autowired
    private PushService pushService;

    private final Object putServiceLock = new Object();

    @PostConstruct
    public void init() {

        UtilsAndCommons.SERVICE_SYNCHRONIZATION_EXECUTOR.schedule(new ServiceReporter(), 60000, TimeUnit.MILLISECONDS);

        UtilsAndCommons.SERVICE_UPDATE_EXECUTOR.submit(new UpdatedServiceProcessor());

        try {
            Loggers.SRV_LOG.info("listen for service meta change");
            consistencyService.listen(KeyBuilder.SERVICE_META_KEY_PREFIX, this);
        } catch (NacosException e) {
            Loggers.SRV_LOG.error("listen for service meta change failed!");
        }
    }

    public Map<String, Service> chooseServiceMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    public void addUpdatedService2Queue(String namespaceId, String serviceName, String serverIP, String checksum) {
        lock.lock();
        try {
            toBeUpdatedServicesQueue.offer(new ServiceKey(namespaceId, serviceName, serverIP, checksum), 5, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            toBeUpdatedServicesQueue.poll();
            toBeUpdatedServicesQueue.add(new ServiceKey(namespaceId, serviceName, serverIP, checksum));
            Loggers.SRV_LOG.error("[DOMAIN-STATUS] Failed to add service to be updatd to queue.", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 元数据 或非Switch
     * @param key candidate key
     * @return
     */
    @Override
    public boolean interests(String key) {
        return KeyBuilder.matchServiceMetaKey(key) && !KeyBuilder.matchSwitchKey(key);
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return KeyBuilder.matchServiceMetaKey(key) && !KeyBuilder.matchSwitchKey(key);
    }

    @Override
    public void onChange(String key, Service service) throws Exception {
        try {
            if (service == null) {
                Loggers.SRV_LOG.warn("received empty push from raft, key: {}", key);
                return;
            }

            if (StringUtils.isBlank(service.getNamespaceId())) {
                service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
            }

            Loggers.RAFT.info("[RAFT-NOTIFIER] datum is changed, key: {}, value: {}", key, service);

            /**
             * 缓存中是否有service
             */
            Service oldDom = getService(service.getNamespaceId(), service.getName());

            if (oldDom != null) {
                /**
                 * 更新缓存中的service
                 */
                oldDom.update(service);
                // re-listen to handle the situation when the underlying listener is removed:
                /**
                 * 重新监听service
                 */
                consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), true), oldDom);
                consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), false), oldDom);
            } else {
                /**
                 * 新增节点
                 */
                putServiceAndInit(service);
            }
        } catch (Throwable e) {
            Loggers.SRV_LOG.error("[NACOS-SERVICE] error while processing service update", e);
        }
    }

    @Override
    public void onDelete(String key) throws Exception {
        String namespace = KeyBuilder.getNamespace(key);
        String name = KeyBuilder.getServiceName(key);
        Service service = chooseServiceMap(namespace).get(name);
        Loggers.RAFT.info("[RAFT-NOTIFIER] datum is deleted, key: {}", key);

        // check again:
        if (service != null && !service.allIPs().isEmpty()) {
            Loggers.SRV_LOG.warn("service not empty, key: {}", key);
            return;
        }

        if (service != null) {
            service.destroy();
            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, true));

            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, false));

            consistencyService.unlisten(KeyBuilder.buildServiceMetaKey(namespace, name), service);
            Loggers.SRV_LOG.info("[DEAD-SERVICE] {}", service.toJSON());
        }

        chooseServiceMap(namespace).remove(name);
    }

    private class UpdatedServiceProcessor implements Runnable {
        //get changed service from other server asynchronously
        @Override
        public void run() {
            ServiceKey serviceKey = null;

            try {
                while (true) {
                    try {
                        serviceKey = toBeUpdatedServicesQueue.take();
                    } catch (Exception e) {
                        Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while taking item from LinkedBlockingDeque.");
                    }

                    if (serviceKey == null) {
                        continue;
                    }
                    GlobalExecutor.submitServiceUpdate(new ServiceUpdater(serviceKey));
                }
            } catch (Exception e) {
                Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while update service: {}", serviceKey, e);
            }
        }
    }

    private class ServiceUpdater implements Runnable {

        String namespaceId;
        String serviceName;
        String serverIP;

        public ServiceUpdater(ServiceKey serviceKey) {
            this.namespaceId = serviceKey.getNamespaceId();
            this.serviceName = serviceKey.getServiceName();
            this.serverIP = serviceKey.getServerIP();
        }

        @Override
        public void run() {
            try {
                updatedHealthStatus(namespaceId, serviceName, serverIP);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[DOMAIN-UPDATER] Exception while update service: {} from {}, error: {}",
                    serviceName, serverIP, e);
            }
        }
    }

    public int getPagedClusterState(String namespaceId, int startPage, int pageSize, String keyword, String containedInstance, List<RaftPeer> raftPeerList, RaftPeerSet raftPeerSet) {

        List<RaftPeer> matchList = new ArrayList<>(raftPeerSet.allPeers());

        List<RaftPeer> tempList = new ArrayList<>();
        if (StringUtils.isNotBlank(keyword)) {
            for (RaftPeer raftPeer : matchList) {
                String ip = raftPeer.ip.split(":")[0];
                if (keyword.equals(ip)) {
                    tempList.add(raftPeer);
                }
            }
            matchList = tempList;
        }

        if (pageSize >= matchList.size()) {
            raftPeerList.addAll(matchList);
            return matchList.size();
        }

        for (int i = 0; i < matchList.size(); i++) {
            if (i < startPage * pageSize) {
                continue;
            }

            raftPeerList.add(matchList.get(i));

            if (raftPeerList.size() >= pageSize) {
                break;
            }
        }

        return matchList.size();
    }

    public void updatedHealthStatus(String namespaceId, String serviceName, String serverIP) {
        Message msg = synchronizer.get(serverIP, UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
        JSONObject serviceJson = JSON.parseObject(msg.getData());

        JSONArray ipList = serviceJson.getJSONArray("ips");
        Map<String, String> ipsMap = new HashMap<>(ipList.size());
        for (int i = 0; i < ipList.size(); i++) {

            String ip = ipList.getString(i);
            String[] strings = ip.split("_");
            ipsMap.put(strings[0], strings[1]);
        }

        Service service = getService(namespaceId, serviceName);

        if (service == null) {
            return;
        }

        boolean changed = false;

        List<Instance> instances = service.allIPs();
        for (Instance instance : instances) {

            boolean valid = Boolean.parseBoolean(ipsMap.get(instance.toIPAddr()));
            if (valid != instance.isHealthy()) {
                changed = true;
                instance.setHealthy(valid);
                Loggers.EVT_LOG.info("{} {SYNC} IP-{} : {}@{}{}",
                    serviceName, (instance.isHealthy() ? "ENABLED" : "DISABLED"),
                    instance.getIp(), instance.getPort(), instance.getClusterName());
            }
        }

        if (changed) {
            pushService.serviceChanged(service);
        }

        StringBuilder stringBuilder = new StringBuilder();
        List<Instance> allIps = service.allIPs();
        for (Instance instance : allIps) {
            stringBuilder.append(instance.toIPAddr()).append("_").append(instance.isHealthy()).append(",");
        }

        if (changed && Loggers.EVT_LOG.isDebugEnabled()) {
            Loggers.EVT_LOG.debug("[HEALTH-STATUS-UPDATED] namespace: {}, service: {}, ips: {}",
                service.getNamespaceId(), service.getName(), stringBuilder.toString());
        }

    }

    public Set<String> getAllServiceNames(String namespaceId) {
        return serviceMap.get(namespaceId).keySet();
    }

    public Map<String, Set<String>> getAllServiceNames() {

        Map<String, Set<String>> namesMap = new HashMap<>(16);
        for (String namespaceId : serviceMap.keySet()) {
            namesMap.put(namespaceId, serviceMap.get(namespaceId).keySet());
        }
        return namesMap;
    }

    public Set<String> getAllNamespaces() {
        return serviceMap.keySet();
    }

    public List<String> getAllServiceNameList(String namespaceId) {
        if (chooseServiceMap(namespaceId) == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(chooseServiceMap(namespaceId).keySet());
    }

    public Map<String, Set<Service>> getResponsibleServices() {
        Map<String, Set<Service>> result = new HashMap<>(16);
        for (String namespaceId : serviceMap.keySet()) {
            result.put(namespaceId, new HashSet<>());
            for (Map.Entry<String, Service> entry : serviceMap.get(namespaceId).entrySet()) {
                Service service = entry.getValue();
                if (distroMapper.responsible(entry.getKey())) {
                    result.get(namespaceId).add(service);
                }
            }
        }
        return result;
    }

    public int getResponsibleServiceCount() {
        int serviceCount = 0;
        for (String namespaceId : serviceMap.keySet()) {
            for (Map.Entry<String, Service> entry : serviceMap.get(namespaceId).entrySet()) {
                if (distroMapper.responsible(entry.getKey())) {
                    serviceCount++;
                }
            }
        }
        return serviceCount;
    }

    public int getResponsibleInstanceCount() {
        Map<String, Set<Service>> responsibleServices = getResponsibleServices();
        int count = 0;
        for (String namespaceId : responsibleServices.keySet()) {
            for (Service service : responsibleServices.get(namespaceId)) {
                count += service.allIPs().size();
            }
        }

        return count;
    }

    public void easyRemoveService(String namespaceId, String serviceName) throws Exception {

        Service service = getService(namespaceId, serviceName);
        if (service == null) {
            throw new IllegalArgumentException("specified service not exist, serviceName : " + serviceName);
        }

        if (!service.allIPs().isEmpty()) {
            throw new IllegalArgumentException("specified service has instances, serviceName : " + serviceName);
        }

        consistencyService.remove(KeyBuilder.buildServiceMetaKey(namespaceId, serviceName));
    }

    public void addOrReplaceService(Service service) throws NacosException {
        consistencyService.put(KeyBuilder.buildServiceMetaKey(service.getNamespaceId(), service.getName()), service);
    }

    /**
     * 新增service
     * @param namespaceId
     * @param serviceName
     * @param local
     * @throws NacosException
     */
    public void createEmptyService(String namespaceId, String serviceName, boolean local) throws NacosException {
        createServiceIfAbsent(namespaceId, serviceName, local, null);
    }

    /**
     *
     * @param namespaceId
     * @param serviceName
     * @param local
     * @param cluster
     * @throws NacosException
     */
    public void createServiceIfAbsent(String namespaceId, String serviceName, boolean local, Cluster cluster) throws NacosException {
        /**
         * 缓存中查询服务是否存在
         */
        Service service = getService(namespaceId, serviceName);

        /**
         * 没有则新建
         */
        if (service == null) {

            Loggers.SRV_LOG.info("creating empty service {}:{}", namespaceId, serviceName);
            /**
             * 生成新service
             */
            service = new Service();
            service.setName(serviceName);
            service.setNamespaceId(namespaceId);
            service.setGroupName(NamingUtils.getGroupName(serviceName));
            // now validate the service. if failed, exception will be thrown
            service.setLastModifiedMillis(System.currentTimeMillis());

            /**
             * 重新计算checksum
             */
            service.recalculateChecksum();
            if (cluster != null) {
                cluster.setService(service);
                service.getClusterMap().put(cluster.getName(), cluster);
            }
            /**
             * 校验
             */
            service.validate();
            if (local) {
                /**
                 * 临时节点
                 */
                putServiceAndInit(service);
            } else {
                /**
                 * 持久化节点
                 */
                addOrReplaceService(service);
            }
        }
    }

    public void putServiceIfAbsent(Service service, boolean local, Cluster cluster) throws NacosException {
        final String namespaceId = service.getNamespaceId();
        final String serviceName = service.getName();

        if (getService(namespaceId, serviceName) != null) {
            return;
        }

        Loggers.SRV_LOG.info("creating empty service {}:{}", namespaceId, serviceName);
        // now validate the service. if failed, exception will be thrown
        service.setLastModifiedMillis(System.currentTimeMillis());
        service.recalculateChecksum();
        if (cluster != null) {
            cluster.setService(service);
            service.getClusterMap().put(cluster.getName(), cluster);
        }
        service.validate();
        if (local) {
            putServiceAndInit(service);
        } else {
            addOrReplaceService(service);
        }
    }

    /**
     * Register an instance to a service in AP mode.
     * <p>
     * This method creates service or cluster silently if they don't exist.
     *
     * @param namespaceId id of namespace
     * @param serviceName service name
     * @param instance    instance to register
     * @throws Exception any error occurred in the process
     */
    public void registerInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {

        /**
         * service不存在则新建
         */
        createEmptyService(namespaceId, serviceName, instance.isEphemeral());

        /**
         * 再次查询缓存
         */
        Service service = getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "service not found, namespace: " + namespaceId + ", service: " + serviceName);
        }

        /**
         *
         */
        addInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
    }

    public void updateInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {

        Service service = getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "service not found, namespace: " + namespaceId + ", service: " + serviceName);
        }

        if (!service.allIPs().contains(instance)) {
            throw new NacosException(NacosException.INVALID_PARAM, "instance not exist: " + instance);
        }

        addInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
    }

    /**
     * 向nacos注册实例
     * @param namespaceId
     * @param serviceName
     * @param ephemeral
     * @param ips
     * @throws NacosException
     */
    public void addInstance(String namespaceId, String serviceName, boolean ephemeral, Instance... ips) throws NacosException {

        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, ephemeral);

        Service service = getService(namespaceId, serviceName);

        /**
         * 新增ips后    当前service对应的有效的节点  之前的+新增的
         */
        List<Instance> instanceList = addIpAddresses(service, ephemeral, ips);

        Instances instances = new Instances();
        instances.setInstanceList(instanceList);

        /**
         * 新增
         */
        consistencyService.put(key, instances);
    }

    /**
     * 注销
     * @param namespaceId
     * @param serviceName
     * @param ephemeral
     * @param ips
     * @throws NacosException
     */
    public void removeInstance(String namespaceId, String serviceName, boolean ephemeral, Instance... ips) throws NacosException {
        Service service = getService(namespaceId, serviceName);
        /**
         * 注销
         */
        removeInstance(namespaceId, serviceName, ephemeral, service, ips);
    }

    /**
     * 注销
     * @param namespaceId
     * @param serviceName
     * @param ephemeral
     * @param service
     * @param ips
     * @throws NacosException
     */
    public void removeInstance(String namespaceId, String serviceName, boolean ephemeral, Service service, Instance... ips) throws NacosException {

        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, ephemeral);

        /**
         * 移除ips后  剩余的Instance列表
         */
        List<Instance> instanceList = substractIpAddresses(service, ephemeral, ips);

        Instances instances = new Instances();
        instances.setInstanceList(instanceList);

        consistencyService.put(key, instances);
    }

    /**
     * 获取Instance
     * @param namespaceId
     * @param serviceName
     * @param cluster
     * @param ip
     * @param port
     * @return
     */
    public Instance getInstance(String namespaceId, String serviceName, String cluster, String ip, int port) {
        /**
         * 本地缓存查询服务是否存在
         */
        Service service = getService(namespaceId, serviceName);
        if (service == null) {
            return null;
        }

        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);

        /**
         * 依照cluster名称  查询其下的所有Instance
         */
        List<Instance> ips = service.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            return null;
        }

        /**
         * 比对ip和port   Instance是否已存在
         */
        for (Instance instance : ips) {
            if (instance.getIp().equals(ip) && instance.getPort() == port) {
                return instance;
            }
        }

        return null;
    }

    /**
     * 获取service下有效的Instance列表  包含当前新增的Instance或移除当前待注销的Instance
     * @param service
     * @param action 新增或移除
     * @param ephemeral  临时或持久
     * @param ips
     * @return
     * @throws NacosException
     */
    public List<Instance> updateIpAddresses(Service service, String action, boolean ephemeral, Instance... ips) throws NacosException {

        /**
         * 获取service对应得Datum
         *
         * 区分临时节点和持久化节点
         *
         * DelegateConsistencyServiceImpl
         */
        Datum datum = consistencyService.get(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), ephemeral));

        Map<String, Instance> oldInstanceMap = new HashMap<>(16);
        /**
         * 获取service对应的节点  不区分集群
         */
        List<Instance> currentIPs = service.allIPs(ephemeral);
        Map<String, Instance> map = new ConcurrentHashMap<>(currentIPs.size());

        for (Instance instance : currentIPs) {
            map.put(instance.toIPAddr(), instance);
        }
        if (datum != null) {
            /**
             * 以map中的数据为准    更新datum中Instance对应的healthy和lastBeat
             */
            oldInstanceMap = setValid(((Instances) datum.value).getInstanceList(), map);
        }

        // use HashMap for deep copy:
        HashMap<String, Instance> instanceMap = new HashMap<>(oldInstanceMap.size());
        instanceMap.putAll(oldInstanceMap);

        for (Instance instance : ips) {
            /**
             * 当前instance对应的集群名称是否为新增
             */
            if (!service.getClusterMap().containsKey(instance.getClusterName())) {
                /**
                 * 新增service下的Cluster
                 */
                Cluster cluster = new Cluster(instance.getClusterName(), service);
                cluster.init();
                service.getClusterMap().put(instance.getClusterName(), cluster);
                Loggers.SRV_LOG.warn("cluster: {} not found, ip: {}, will create new cluster with default configuration.",
                    instance.getClusterName(), instance.toJSON());
            }

            /**
             * 新增/删除操作
             */
            if (UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE.equals(action)) {
                instanceMap.remove(instance.getDatumKey());
            } else {
                instanceMap.put(instance.getDatumKey(), instance);
            }

        }

        if (instanceMap.size() <= 0 && UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD.equals(action)) {
            throw new IllegalArgumentException("ip list can not be empty, service: " + service.getName() + ", ip list: "
                + JSON.toJSONString(instanceMap.values()));
        }

        /**
         * 返回service下有效的节点  未必健康
         */
        return new ArrayList<>(instanceMap.values());
    }

    /**
     * 注销    移除ips后  剩余的Instance列表
     * @param service
     * @param ephemeral
     * @param ips
     * @return
     * @throws NacosException
     */
    public List<Instance> substractIpAddresses(Service service, boolean ephemeral, Instance... ips) throws NacosException {
        /**
         * remove操作  移除ips后  剩余的Instance列表
         */
        return updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, ephemeral, ips);
    }

    /**
     * 获取service下有效的Instance列表  包含当前新增的Instance
     * @param service
     * @param ephemeral
     * @param ips
     * @return
     * @throws NacosException
     */
    public List<Instance> addIpAddresses(Service service, boolean ephemeral, Instance... ips) throws NacosException {
        /**
         * 新增  UPDATE_INSTANCE_ACTION_ADD
         */
        return updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, ephemeral, ips);
    }

    /**
     * 以map中的数据为准    更新oldInstances中Instance对应的healthy和lastBeat
     * @param oldInstances
     * @param map
     * @return
     */
    private Map<String, Instance> setValid(List<Instance> oldInstances, Map<String, Instance> map) {

        Map<String, Instance> instanceMap = new HashMap<>(oldInstances.size());
        for (Instance instance : oldInstances) {
            Instance instance1 = map.get(instance.toIPAddr());
            /**
             * 更新instance对应的healthy和lastBeat
             */
            if (instance1 != null) {
                instance.setHealthy(instance1.isHealthy());
                instance.setLastBeat(instance1.getLastBeat());
            }
            instanceMap.put(instance.getDatumKey(), instance);
        }
        return instanceMap;
    }

    /**
     * 本地缓存查询服务是否存在
     * @param namespaceId
     * @param serviceName
     * @return
     */
    public Service getService(String namespaceId, String serviceName) {
        if (serviceMap.get(namespaceId) == null) {
            return null;
        }
        /**
         * 通过namespaceId和serviceName   查询服务
         */
        return chooseServiceMap(namespaceId).get(serviceName);
    }

    public boolean containService(String namespaceId, String serviceName) {
        return getService(namespaceId, serviceName) != null;
    }

    /**
     * 将service存入缓存
     * @param service
     */
    public void putService(Service service) {
        if (!serviceMap.containsKey(service.getNamespaceId())) {
            synchronized (putServiceLock) {
                if (!serviceMap.containsKey(service.getNamespaceId())) {
                    serviceMap.put(service.getNamespaceId(), new ConcurrentHashMap<>(16));
                }
            }
        }
        serviceMap.get(service.getNamespaceId()).put(service.getName(), service);
    }

    /**
     * 临时节点
     * @param service
     * @throws NacosException
     */
    private void putServiceAndInit(Service service) throws NacosException {
        /**
         * service存入缓存
         */
        putService(service);
        /**
         * 初始化
         */
        service.init();
        consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), true), service);
        consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), false), service);
        Loggers.SRV_LOG.info("[NEW-SERVICE] {}", service.toJSON());
    }


    public List<Service> searchServices(String namespaceId, String regex) {
        List<Service> result = new ArrayList<>();
        for (Map.Entry<String, Service> entry : chooseServiceMap(namespaceId).entrySet()) {
            Service service = entry.getValue();
            String key = service.getName() + ":" + ArrayUtils.toString(service.getOwners());
            if (key.matches(regex)) {
                result.add(service);
            }
        }

        return result;
    }

    public int getServiceCount() {
        int serviceCount = 0;
        for (String namespaceId : serviceMap.keySet()) {
            serviceCount += serviceMap.get(namespaceId).size();
        }
        return serviceCount;
    }

    public int getInstanceCount() {
        int total = 0;
        for (String namespaceId : serviceMap.keySet()) {
            for (Service service : serviceMap.get(namespaceId).values()) {
                total += service.allIPs().size();
            }
        }
        return total;
    }

    public Map<String, Service> getServiceMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    public int getPagedService(String namespaceId, int startPage, int pageSize, String keyword, String containedInstance, List<Service> serviceList, boolean hasIpCount) {

        List<Service> matchList;

        if (chooseServiceMap(namespaceId) == null) {
            return 0;
        }

        if (StringUtils.isNotBlank(keyword)) {
            matchList = searchServices(namespaceId, ".*" + keyword + ".*");
        } else {
            matchList = new ArrayList<>(chooseServiceMap(namespaceId).values());
        }

        if (!CollectionUtils.isEmpty(matchList) && hasIpCount) {
            matchList = matchList.stream().filter(s -> !CollectionUtils.isEmpty(s.allIPs())).collect(Collectors.toList());
        }

        if (StringUtils.isNotBlank(containedInstance)) {

            boolean contained;
            for (int i = 0; i < matchList.size(); i++) {
                Service service = matchList.get(i);
                contained = false;
                List<Instance> instances = service.allIPs();
                for (Instance instance : instances) {
                    if (containedInstance.contains(":")) {
                        if (StringUtils.equals(instance.getIp() + ":" + instance.getPort(), containedInstance)) {
                            contained = true;
                            break;
                        }
                    } else {
                        if (StringUtils.equals(instance.getIp(), containedInstance)) {
                            contained = true;
                            break;
                        }
                    }
                }
                if (!contained) {
                    matchList.remove(i);
                    i--;
                }
            }
        }

        if (pageSize >= matchList.size()) {
            serviceList.addAll(matchList);
            return matchList.size();
        }

        for (int i = 0; i < matchList.size(); i++) {
            if (i < startPage * pageSize) {
                continue;
            }

            serviceList.add(matchList.get(i));

            if (serviceList.size() >= pageSize) {
                break;
            }
        }

        return matchList.size();
    }

    public static class ServiceChecksum {

        public String namespaceId;
        public Map<String, String> serviceName2Checksum = new HashMap<String, String>();

        public ServiceChecksum() {
            this.namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        }

        public ServiceChecksum(String namespaceId) {
            this.namespaceId = namespaceId;
        }

        public void addItem(String serviceName, String checksum) {
            if (StringUtils.isEmpty(serviceName) || StringUtils.isEmpty(checksum)) {
                Loggers.SRV_LOG.warn("[DOMAIN-CHECKSUM] serviceName or checksum is empty,serviceName: {}, checksum: {}",
                    serviceName, checksum);
                return;
            }

            serviceName2Checksum.put(serviceName, checksum);
        }
    }

    private class ServiceReporter implements Runnable {

        @Override
        public void run() {
            try {

                Map<String, Set<String>> allServiceNames = getAllServiceNames();

                if (allServiceNames.size() <= 0) {
                    //ignore
                    return;
                }

                for (String namespaceId : allServiceNames.keySet()) {

                    ServiceChecksum checksum = new ServiceChecksum(namespaceId);

                    for (String serviceName : allServiceNames.get(namespaceId)) {
                        if (!distroMapper.responsible(serviceName)) {
                            continue;
                        }

                        Service service = getService(namespaceId, serviceName);

                        if (service == null) {
                            continue;
                        }

                        service.recalculateChecksum();

                        checksum.addItem(serviceName, service.getChecksum());
                    }

                    Message msg = new Message();

                    msg.setData(JSON.toJSONString(checksum));

                    List<Server> sameSiteServers = serverListManager.getServers();

                    if (sameSiteServers == null || sameSiteServers.size() <= 0) {
                        return;
                    }

                    for (Server server : sameSiteServers) {
                        if (server.getKey().equals(NetUtils.localServer())) {
                            continue;
                        }
                        synchronizer.send(server.getKey(), msg);
                    }
                }
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[DOMAIN-STATUS] Exception while sending service status", e);
            } finally {
                UtilsAndCommons.SERVICE_SYNCHRONIZATION_EXECUTOR.schedule(this, switchDomain.getServiceStatusSynchronizationPeriodMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private static class ServiceKey {
        private String namespaceId;
        private String serviceName;
        private String serverIP;
        private String checksum;

        public String getChecksum() {
            return checksum;
        }

        public String getServerIP() {
            return serverIP;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getNamespaceId() {
            return namespaceId;
        }

        public ServiceKey(String namespaceId, String serviceName, String serverIP, String checksum) {
            this.namespaceId = namespaceId;
            this.serviceName = serviceName;
            this.serverIP = serverIP;
            this.checksum = checksum;
        }

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }
}
