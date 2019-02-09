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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author nkorange
 */
@Component
@DependsOn("nacosApplicationContext")
public class ServiceManager implements DataListener<Service> {

    /**
     * Map<namespace, Map<group::serviceName, Service>>
     */
    private Map<String, Map<String, Service>> serviceMap = new ConcurrentHashMap<>();

    private LinkedBlockingDeque<ServiceKey> toBeUpdatedDomsQueue = new LinkedBlockingDeque<>(1024 * 1024);

    private Synchronizer synchronizer = new DomainStatusSynchronizer();

    /**
     * thread pool core size
     */
    private final static int DOMAIN_UPDATE_EXECUTOR_NUM = 2;

    private final Lock lock = new ReentrantLock();

    private Map<String, Condition> service2ConditionMap = new ConcurrentHashMap<>();

    private Map<String, Lock> service2LockMap = new ConcurrentHashMap<>();

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

    @Autowired
    private Serializer serializer;

    /**
     * thread pool that processes getting service detail from other server asynchronously
     */
    private ExecutorService serviceUpdateExecutor
        = Executors.newFixedThreadPool(DOMAIN_UPDATE_EXECUTOR_NUM, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.naming.service.update.http.handler");
            t.setDaemon(true);
            return t;
        }
    });

    @PostConstruct
    public void init() {

        UtilsAndCommons.DOMAIN_SYNCHRONIZATION_EXECUTOR.schedule(new ServiceReporter(), 60000, TimeUnit.MILLISECONDS);

        UtilsAndCommons.DOMAIN_UPDATE_EXECUTOR.submit(new UpdatedDomainProcessor());

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
            toBeUpdatedDomsQueue.offer(new ServiceKey(namespaceId, serviceName, serverIP, checksum), 5, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            toBeUpdatedDomsQueue.poll();
            toBeUpdatedDomsQueue.add(new ServiceKey(namespaceId, serviceName, serverIP, checksum));
            Loggers.SRV_LOG.error("[DOMAIN-STATUS] Failed to add service to be updatd to queue.", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean interests(String key) {
        return KeyBuilder.matchServiceMetaKey(key);
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return KeyBuilder.matchServiceMetaKey(key);
    }

    @Override
    public void onChange(String key, Service service) throws Exception {
        try {
            if (service == null) {
                Loggers.SRV_LOG.warn("received empty push from raft, key: {}", key);
                return;
            }

            if (StringUtils.isBlank(service.getNamespaceId())) {
                service.setNamespaceId(UtilsAndCommons.DEFAULT_NAMESPACE_ID);
            }

            Loggers.RAFT.info("[RAFT-NOTIFIER] datum is changed, key: {}, value: {}", key, service);

            Service oldDom = getService(service.getNamespaceId(), service.getName());

            if (oldDom != null) {
                oldDom.update(service);
            } else {

                addLockIfAbsent(UtilsAndCommons.assembleFullServiceName(service.getNamespaceId(), service.getName()));
                putService(service);
                service.init();
                consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), true), service);
                consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), false), service);
                Loggers.SRV_LOG.info("[NEW-SERVICE] {}", service.toJSON());
            }
            wakeUp(UtilsAndCommons.assembleFullServiceName(service.getNamespaceId(), service.getName()));

        } catch (Throwable e) {
            Loggers.SRV_LOG.error("[NACOS-SERVICE] error while processing service update", e);
        }
    }

    @Override
    public void onDelete(String key) throws Exception {
        String namespace = KeyBuilder.getNamespace(key);
        String name = KeyBuilder.getServiceName(key);
        Service service = chooseServiceMap(namespace).remove(name);
        Loggers.RAFT.info("[RAFT-NOTIFIER] datum is deleted, key: {}", key);

        if (service != null) {
            service.destroy();
            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, true));
            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, false));
            consistencyService.unlisten(KeyBuilder.buildServiceMetaKey(namespace, name), service);
            Loggers.SRV_LOG.info("[DEAD-DOM] {}", service.toJSON());
        }
    }

    private class UpdatedDomainProcessor implements Runnable {
        //get changed service from other server asynchronously
        @Override
        public void run() {
            String serviceName = null;
            String serverIP = null;

            try {
                while (true) {
                    ServiceKey serviceKey = null;

                    try {
                        serviceKey = toBeUpdatedDomsQueue.take();
                    } catch (Exception e) {
                        Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while taking item from LinkedBlockingDeque.");
                    }

                    if (serviceKey == null) {
                        continue;
                    }

                    serviceName = serviceKey.getServiceName();
                    serverIP = serviceKey.getServerIP();

                    serviceUpdateExecutor.execute(new ServiceUpdater(serviceKey.getNamespaceId(), serviceName, serverIP));
                }
            } catch (Exception e) {
                Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while update service: {} from {}, error: {}", serviceName, serverIP, e);
            }
        }
    }

    private class ServiceUpdater implements Runnable {

        String namespaceId;
        String serviceName;
        String serverIP;

        public ServiceUpdater(String namespaceId, String serviceName, String serverIP) {
            this.namespaceId = namespaceId;
            this.serviceName = serviceName;
            this.serverIP = serverIP;
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

        List<Instance> instances = service.allIPs();
        for (Instance instance : instances) {

            Boolean valid = Boolean.parseBoolean(ipsMap.get(instance.toIPAddr()));
            if (valid != instance.isValid()) {
                instance.setValid(valid);
                Loggers.EVT_LOG.info("{} {SYNC} IP-{} : {}@{}",
                    serviceName, (instance.isValid() ? "ENABLED" : "DISABLED"),
                    instance.getIp(), instance.getPort(), instance.getClusterName());
            }
        }

        pushService.serviceChanged(service.getNamespaceId(), service.getName());
        StringBuilder stringBuilder = new StringBuilder();
        List<Instance> allIps = service.allIPs();
        for (Instance instance : allIps) {
            stringBuilder.append(instance.toIPAddr()).append("_").append(instance.isValid()).append(",");
        }

        Loggers.EVT_LOG.info("[IP-UPDATED] service: {}, ips: {}", service.getName(), stringBuilder.toString());

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

    public List<String> getAllDomNamesList(String namespaceId) {
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
        consistencyService.remove(KeyBuilder.buildServiceMetaKey(namespaceId, serviceName));
    }

    public void addOrReplaceService(Service service) throws Exception {
        consistencyService.put(KeyBuilder.buildServiceMetaKey(service.getNamespaceId(), service.getName()), service);
    }

    /**
     * Register an instance to a service.
     * <p>
     * This method creates service or cluster silently if they don't exist.
     *
     * @param namespaceId id of namespace
     * @param serviceName service name
     * @param instance    instance to register
     * @throws Exception any error occurred in the process
     */
    public void registerInstance(String namespaceId, String serviceName, String clusterName, Instance instance) throws Exception {

        Service service = getService(namespaceId, serviceName);
        boolean serviceUpdated = false;
        if (service == null) {
            service = new Service();
            service.setName(serviceName);
            service.setNamespaceId(namespaceId);
            // now validate the service. if failed, exception will be thrown
            service.setLastModifiedMillis(System.currentTimeMillis());
            service.recalculateChecksum();
            service.valid();
            serviceUpdated = true;
        }

        if (!service.getClusterMap().containsKey(instance.getClusterName())) {

            Cluster cluster = new Cluster();
            cluster.setName(instance.getClusterName());
            cluster.setDom(service);
            cluster.init();

            if (service.getClusterMap().containsKey(cluster.getName())) {
                service.getClusterMap().get(cluster.getName()).update(cluster);
            } else {
                service.getClusterMap().put(cluster.getName(), cluster);
            }

            service.setLastModifiedMillis(System.currentTimeMillis());
            service.recalculateChecksum();
            service.valid();
            serviceUpdated = true;
        }

        if (serviceUpdated) {
            Lock lock = addLockIfAbsent(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
            Condition condition = addCondtion(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));

            final Service finalService = service;
            GlobalExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        addOrReplaceService(finalService);
                    } catch (Exception e) {
                        Loggers.SRV_LOG.error("register or update service failed, service: {}", finalService, e);
                    }
                }
            });

            try {
                lock.lock();
                condition.await(5000, TimeUnit.MILLISECONDS);
            } finally {
                lock.unlock();
            }
        }

        if (service.allIPs().contains(instance)) {
            throw new NacosException(NacosException.INVALID_PARAM, "instance already exist: " + instance);
        }

        addInstance(namespaceId, serviceName, clusterName, instance.isEphemeral(), instance);
    }

    public void addInstance(String namespaceId, String serviceName, String clusterName, boolean ephemeral, Instance... ips) throws NacosException {

        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, ephemeral);

        Service service = getService(namespaceId, serviceName);

        Map<String, Instance> instanceMap = addIpAddresses(service, ephemeral, ips);

        Instances instances = new Instances();
        instances.setInstanceMap(instanceMap);

        consistencyService.put(key, instances);
    }

    public void removeInstance(String namespaceId, String serviceName, boolean ephemeral, Instance... ips) throws NacosException {

        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, ephemeral);

        Service service = getService(namespaceId, serviceName);

        Map<String, Instance> instanceMap = substractIpAddresses(service, ephemeral, ips);

        Instances instances = new Instances();
        instances.setInstanceMap(instanceMap);

        consistencyService.put(key, instances);
    }

    public Instance getInstance(String namespaceId, String serviceName, String cluster, String ip, int port) {
        Service service = getService(namespaceId, serviceName);
        if (service == null) {
            return null;
        }

        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);

        List<Instance> ips = service.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            return null;
        }

        for (Instance instance : ips) {
            if (instance.getIp().equals(ip) && instance.getPort() == port) {
                return instance;
            }
        }

        return null;
    }

    public Map<String, Instance> updateIpAddresses(Service service, String action, boolean ephemeral, Instance... ips) throws NacosException {

        Datum datum = consistencyService.get(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), ephemeral));

        Map<String, Instance> oldInstances = new HashMap<>(16);

        if (datum != null) {
            oldInstances = ((Instances) datum.value).getInstanceMap();
        }

        Map<String, Instance> instances;
        List<Instance> currentIPs = service.allIPs(ephemeral);
        Map<String, Instance> map = new ConcurrentHashMap<>(currentIPs.size());

        for (Instance instance : currentIPs) {
            map.put(instance.toIPAddr(), instance);
        }

        instances = setValid(oldInstances, map);

        // use HashMap for deep copy:
        HashMap<String, Instance> instanceMap = new HashMap<>(instances.size());
        instanceMap.putAll(instances);

        for (Instance instance : ips) {
            if (!service.getClusterMap().containsKey(instance.getClusterName())) {
                Cluster cluster = new Cluster(instance.getClusterName());
                cluster.setDom(service);
                service.getClusterMap().put(instance.getClusterName(), cluster);
                Loggers.SRV_LOG.warn("cluster: {} not found, ip: {}, will create new cluster with default configuration.",
                    instance.getClusterName(), instance.toJSON());
            }

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

        return instanceMap;
    }

    public Map<String, Instance> substractIpAddresses(Service service, boolean ephemeral, Instance... ips) throws NacosException {
        return updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, ephemeral, ips);
    }

    public Map<String, Instance> addIpAddresses(Service service, boolean ephemeral, Instance... ips) throws NacosException {
        return updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, ephemeral, ips);
    }

    private Map<String, Instance> setValid(Map<String, Instance> oldInstances, Map<String, Instance> map) {
        for (Instance instance : oldInstances.values()) {
            Instance instance1 = map.get(instance.toIPAddr());
            if (instance1 != null) {
                instance.setValid(instance1.isValid());
                instance.setLastBeat(instance1.getLastBeat());
            }
        }
        return oldInstances;
    }

    public Service getService(String namespaceId, String serviceName) {
        if (serviceMap.get(namespaceId) == null) {
            return null;
        }
        return chooseServiceMap(namespaceId).get(serviceName);
    }

    public void putService(Service service) {
        if (!serviceMap.containsKey(service.getNamespaceId())) {
            serviceMap.put(service.getNamespaceId(), new ConcurrentHashMap<>(16));
        }
        serviceMap.get(service.getNamespaceId()).put(service.getName(), service);
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

    public int getPagedService(String namespaceId, int startPage, int pageSize, String keyword, List<Service> serviceList) {

        List<Service> matchList;

        if (chooseServiceMap(namespaceId) == null) {
            return 0;
        }

        if (StringUtils.isNotBlank(keyword)) {
            matchList = searchServices(namespaceId, ".*" + keyword + ".*");
        } else {
            matchList = new ArrayList<Service>(chooseServiceMap(namespaceId).values());
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
            this.namespaceId = UtilsAndCommons.DEFAULT_NAMESPACE_ID;
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
                UtilsAndCommons.DOMAIN_SYNCHRONIZATION_EXECUTOR.schedule(this, switchDomain.getDomStatusSynchronizationPeriodMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public void wakeUp(String key) {

        Lock lock = service2LockMap.get(key);
        Condition condition = service2ConditionMap.get(key);

        try {
            lock.lock();
            condition.signalAll();
        } catch (Exception ignore) {
        } finally {
            lock.unlock();
        }
    }

    public Lock addLockIfAbsent(String key) {

        if (service2LockMap.containsKey(key)) {
            return service2LockMap.get(key);
        }
        Lock lock = new ReentrantLock();
        service2LockMap.put(key, lock);
        return lock;
    }

    public Condition addCondtion(String key) {
        Condition condition = service2LockMap.get(key).newCondition();
        service2ConditionMap.put(key, condition);
        return condition;
    }

    private static class ServiceKey {
        private String namespaceId;
        private String serviceName;
        private String serverIP;

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

        private String checksum;

        public ServiceKey(String namespaceId, String serviceName, String serverIP, String checksum) {
            this.namespaceId = namespaceId;
            this.serviceName = serviceName;
            this.serverIP = serverIP;
            this.checksum = checksum;
        }
    }
}
