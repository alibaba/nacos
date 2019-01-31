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

    private LinkedBlockingDeque<DomainKey> toBeUpdatedDomsQueue = new LinkedBlockingDeque<>(1024 * 1024);

    private Synchronizer synchronizer = new DomainStatusSynchronizer();

    /**
     * thread pool core size
     */
    private final static int DOMAIN_UPDATE_EXECUTOR_NUM = 2;

    private final Lock lock = new ReentrantLock();

    private Map<String, Condition> dom2ConditionMap = new ConcurrentHashMap<>();

    private Map<String, Lock> dom2LockMap = new ConcurrentHashMap<>();

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
     * thread pool that processes getting domain detail from other server asynchronously
     */
    private ExecutorService domainUpdateExecutor
        = Executors.newFixedThreadPool(DOMAIN_UPDATE_EXECUTOR_NUM, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.naming.domain.update.http.handler");
            t.setDaemon(true);
            return t;
        }
    });

    @PostConstruct
    public void init() {

        UtilsAndCommons.DOMAIN_SYNCHRONIZATION_EXECUTOR.schedule(new DomainReporter(), 60000, TimeUnit.MILLISECONDS);

        UtilsAndCommons.DOMAIN_UPDATE_EXECUTOR.submit(new UpdatedDomainProcessor());

        try {
            Loggers.SRV_LOG.info("listen for service meta change");
            consistencyService.listen(KeyBuilder.SERVICE_META_KEY_PREFIX, this);
        } catch (NacosException e) {
            Loggers.SRV_LOG.error("listen for service meta change failed!");
        }
    }

    public Map<String, Service> chooseDomMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    public void addUpdatedDom2Queue(String namespaceId, String domName, String serverIP, String checksum) {
        lock.lock();
        try {
            toBeUpdatedDomsQueue.offer(new DomainKey(namespaceId, domName, serverIP, checksum), 5, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            toBeUpdatedDomsQueue.poll();
            toBeUpdatedDomsQueue.add(new DomainKey(namespaceId, domName, serverIP, checksum));
            Loggers.SRV_LOG.error("[DOMAIN-STATUS] Failed to add domain to be updatd to queue.", e);
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
                putDomain(service);
                service.init();
                consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), true), service);
                consistencyService.listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), false), service);
                Loggers.SRV_LOG.info("[NEW-DOM-RAFT] {}", service.toJSON());
            }
            wakeUp(UtilsAndCommons.assembleFullServiceName(service.getNamespaceId(), service.getName()));

        } catch (Throwable e) {
            Loggers.SRV_LOG.error("[NACOS-DOM] error while processing dom update", e);
        }
    }

    @Override
    public void onDelete(String key) throws Exception {
        String namespace = KeyBuilder.getNamespace(key);
        String name = KeyBuilder.getServiceName(key);
        Service dom = chooseDomMap(namespace).remove(name);
        Loggers.RAFT.info("[RAFT-NOTIFIER] datum is deleted, key: {}", key);

        if (dom != null) {
            dom.destroy();
            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, true));
            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, false));
            consistencyService.unlisten(KeyBuilder.buildServiceMetaKey(namespace, name), dom);
            Loggers.SRV_LOG.info("[DEAD-DOM] {}", dom.toJSON());
        }
    }

    private class UpdatedDomainProcessor implements Runnable {
        //get changed domain from other server asynchronously
        @Override
        public void run() {
            String domName = null;
            String serverIP = null;

            try {
                while (true) {
                    DomainKey domainKey = null;

                    try {
                        domainKey = toBeUpdatedDomsQueue.take();
                    } catch (Exception e) {
                        Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while taking item from LinkedBlockingDeque.");
                    }

                    if (domainKey == null) {
                        continue;
                    }

                    domName = domainKey.getDomName();
                    serverIP = domainKey.getServerIP();

                    domainUpdateExecutor.execute(new DomUpdater(domainKey.getNamespaceId(), domName, serverIP));
                }
            } catch (Exception e) {
                Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while update dom: {} from {}, error: {}", domName, serverIP, e);
            }
        }
    }

    private class DomUpdater implements Runnable {

        String namespaceId;
        String domName;
        String serverIP;

        public DomUpdater(String namespaceId, String domName, String serverIP) {
            this.namespaceId = namespaceId;
            this.domName = domName;
            this.serverIP = serverIP;
        }

        @Override
        public void run() {
            try {
                updatedDom2(namespaceId, domName, serverIP);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[DOMAIN-UPDATER] Exception while update dom: {} from {}, error: {}",
                    domName, serverIP, e);
            }
        }
    }

    public void updatedDom2(String namespaceId, String domName, String serverIP) {
        Message msg = synchronizer.get(serverIP, UtilsAndCommons.assembleFullServiceName(namespaceId, domName));
        JSONObject dom = JSON.parseObject(msg.getData());

        JSONArray ipList = dom.getJSONArray("ips");
        Map<String, String> ipsMap = new HashMap<>(ipList.size());
        for (int i = 0; i < ipList.size(); i++) {

            String ip = ipList.getString(i);
            String[] strings = ip.split("_");
            ipsMap.put(strings[0], strings[1]);
        }

        Service service = getService(namespaceId, domName);

        if (service == null) {
            return;
        }

        List<Instance> instances = service.allIPs();
        for (Instance instance : instances) {

            Boolean valid = Boolean.parseBoolean(ipsMap.get(instance.toIPAddr()));
            if (valid != instance.isValid()) {
                instance.setValid(valid);
                Loggers.EVT_LOG.info("{} {SYNC} IP-{} : {}@{}",
                    domName, (instance.isValid() ? "ENABLED" : "DISABLED"),
                    instance.getIp(), instance.getPort(), instance.getClusterName());
            }
        }

        pushService.domChanged(service.getNamespaceId(), service.getName());
        StringBuilder stringBuilder = new StringBuilder();
        List<Instance> allIps = service.allIPs();
        for (Instance instance : allIps) {
            stringBuilder.append(instance.toIPAddr()).append("_").append(instance.isValid()).append(",");
        }

        Loggers.EVT_LOG.info("[IP-UPDATED] dom: {}, ips: {}", service.getName(), stringBuilder.toString());

    }

    public Set<String> getAllDomNames(String namespaceId) {
        return serviceMap.get(namespaceId).keySet();
    }

    public Map<String, Set<String>> getAllDomNames() {

        Map<String, Set<String>> namesMap = new HashMap<>(16);
        for (String namespaceId : serviceMap.keySet()) {
            namesMap.put(namespaceId, serviceMap.get(namespaceId).keySet());
        }
        return namesMap;
    }

    public List<String> getAllDomNamesList(String namespaceId) {
        if (chooseDomMap(namespaceId) == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(chooseDomMap(namespaceId).keySet());
    }

    public Map<String, Set<Service>> getResponsibleDoms() {
        Map<String, Set<Service>> result = new HashMap<>(16);
        for (String namespaceId : serviceMap.keySet()) {
            result.put(namespaceId, new HashSet<>());
            for (Map.Entry<String, Service> entry : serviceMap.get(namespaceId).entrySet()) {
                Service domain = entry.getValue();
                if (distroMapper.responsible(entry.getKey())) {
                    result.get(namespaceId).add(domain);
                }
            }
        }
        return result;
    }

    public int getResponsibleDomCount() {
        int domCount = 0;
        for (String namespaceId : serviceMap.keySet()) {
            for (Map.Entry<String, Service> entry : serviceMap.get(namespaceId).entrySet()) {
                if (distroMapper.responsible(entry.getKey())) {
                    domCount++;
                }
            }
        }
        return domCount;
    }

    public int getResponsibleIPCount() {
        Map<String, Set<Service>> responsibleDoms = getResponsibleDoms();
        int count = 0;
        for (String namespaceId : responsibleDoms.keySet()) {
            for (Service domain : responsibleDoms.get(namespaceId)) {
                count += domain.allIPs().size();
            }
        }

        return count;
    }

    public void easyRemoveDom(String namespaceId, String serviceName) throws Exception {
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
            // now validate the dom. if failed, exception will be thrown
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

        Service dom = getService(namespaceId, serviceName);

        Map<String, Instance> instanceMap = substractIpAddresses(dom, ephemeral, ips);

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
            throw new IllegalStateException("no ips found for cluster " + cluster + " in dom " + serviceName);
        }

        for (Instance instance : ips) {
            if (instance.getIp().equals(ip) && instance.getPort() == port) {
                return instance;
            }
        }

        return null;
    }

    public Map<String, Instance> updateIpAddresses(Service dom, String action, boolean ephemeral, Instance... ips) throws NacosException {

        Datum datum = consistencyService.get(KeyBuilder.buildInstanceListKey(dom.getNamespaceId(), dom.getName(), ephemeral));

        Map<String, Instance> oldInstances = new HashMap<>(16);

        if (datum != null) {
            oldInstances = ((Instances) datum.value).getInstanceMap();
        }

        Map<String, Instance> instances;
        List<Instance> currentIPs = dom.allIPs(ephemeral);
        Map<String, Instance> map = new ConcurrentHashMap<>(currentIPs.size());

        for (Instance instance : currentIPs) {
            map.put(instance.toIPAddr(), instance);
        }

        instances = setValid(oldInstances, map);

        // use HashMap for deep copy:
        HashMap<String, Instance> instanceMap = new HashMap<>(instances.size());
        instanceMap.putAll(instances);

        for (Instance instance : ips) {
            if (!dom.getClusterMap().containsKey(instance.getClusterName())) {
                Cluster cluster = new Cluster(instance.getClusterName());
                cluster.setDom(dom);
                dom.getClusterMap().put(instance.getClusterName(), cluster);
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
            throw new IllegalArgumentException("ip list can not be empty, dom: " + dom.getName() + ", ip list: "
                + JSON.toJSONString(instanceMap.values()));
        }

        return instanceMap;
    }

    public Map<String, Instance> substractIpAddresses(Service dom, boolean ephemeral, Instance... ips) throws NacosException {
        return updateIpAddresses(dom, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, ephemeral, ips);
    }

    public Map<String, Instance> addIpAddresses(Service dom, boolean ephemeral, Instance... ips) throws NacosException {
        return updateIpAddresses(dom, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, ephemeral, ips);
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

    public Service getService(String namespaceId, String domName) {
        if (serviceMap.get(namespaceId) == null) {
            return null;
        }
        return chooseDomMap(namespaceId).get(domName);
    }

    public void putDomain(Service domain) {
        if (!serviceMap.containsKey(domain.getNamespaceId())) {
            serviceMap.put(domain.getNamespaceId(), new ConcurrentHashMap<>(16));
        }
        serviceMap.get(domain.getNamespaceId()).put(domain.getName(), domain);
    }


    public List<Service> searchDomains(String namespaceId, String regex) {
        List<Service> result = new ArrayList<>();
        for (Map.Entry<String, Service> entry : chooseDomMap(namespaceId).entrySet()) {
            Service dom = entry.getValue();
            String key = dom.getName() + ":" + ArrayUtils.toString(dom.getOwners());
            if (key.matches(regex)) {
                result.add(dom);
            }
        }

        return result;
    }

    public int getDomCount() {
        int domCount = 0;
        for (String namespaceId : serviceMap.keySet()) {
            domCount += serviceMap.get(namespaceId).size();
        }
        return domCount;
    }

    public int getInstanceCount() {
        int total = 0;
        for (String namespaceId : serviceMap.keySet()) {
            for (Service domain : serviceMap.get(namespaceId).values()) {
                total += domain.allIPs().size();
            }
        }
        return total;
    }

    public Map<String, Service> getDomMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    public int getPagedDom(String namespaceId, int startPage, int pageSize, String keyword, List<Service> domainList) {

        List<Service> matchList;

        if (chooseDomMap(namespaceId) == null) {
            return 0;
        }

        if (StringUtils.isNotBlank(keyword)) {
            matchList = searchDomains(namespaceId, ".*" + keyword + ".*");
        } else {
            matchList = new ArrayList<Service>(chooseDomMap(namespaceId).values());
        }

        if (pageSize >= matchList.size()) {
            domainList.addAll(matchList);
            return matchList.size();
        }

        for (int i = 0; i < matchList.size(); i++) {
            if (i < startPage * pageSize) {
                continue;
            }

            domainList.add(matchList.get(i));

            if (domainList.size() >= pageSize) {
                break;
            }
        }

        return matchList.size();
    }

    public static class DomainChecksum {

        public String namespaceId;
        public Map<String, String> domName2Checksum = new HashMap<String, String>();

        public DomainChecksum() {
            this.namespaceId = UtilsAndCommons.DEFAULT_NAMESPACE_ID;
        }

        public DomainChecksum(String namespaceId) {
            this.namespaceId = namespaceId;
        }

        public void addItem(String domName, String checksum) {
            if (StringUtils.isEmpty(domName) || StringUtils.isEmpty(checksum)) {
                Loggers.SRV_LOG.warn("[DOMAIN-CHECKSUM] domName or checksum is empty,domName: {}, checksum: {}",
                    domName, checksum);
                return;
            }

            domName2Checksum.put(domName, checksum);
        }
    }

    private class DomainReporter implements Runnable {

        @Override
        public void run() {
            try {

                Map<String, Set<String>> allDomainNames = getAllDomNames();

                if (allDomainNames.size() <= 0) {
                    //ignore
                    return;
                }

                for (String namespaceId : allDomainNames.keySet()) {

                    DomainChecksum checksum = new DomainChecksum(namespaceId);

                    for (String domName : allDomainNames.get(namespaceId)) {
                        if (!distroMapper.responsible(domName)) {
                            continue;
                        }

                        Service domain = getService(namespaceId, domName);

                        if (domain == null) {
                            continue;
                        }

                        domain.recalculateChecksum();

                        checksum.addItem(domName, domain.getChecksum());
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
                Loggers.SRV_LOG.error("[DOMAIN-STATUS] Exception while sending domain status", e);
            } finally {
                UtilsAndCommons.DOMAIN_SYNCHRONIZATION_EXECUTOR.schedule(this, switchDomain.getDomStatusSynchronizationPeriodMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public void wakeUp(String key) {

        Lock lock = dom2LockMap.get(key);
        Condition condition = dom2ConditionMap.get(key);

        try {
            lock.lock();
            condition.signalAll();
        } catch (Exception ignore) {
        } finally {
            lock.unlock();
        }
    }

    public Lock addLockIfAbsent(String key) {

        if (dom2LockMap.containsKey(key)) {
            return dom2LockMap.get(key);
        }
        Lock lock = new ReentrantLock();
        dom2LockMap.put(key, lock);
        return lock;
    }

    public Condition addCondtion(String key) {
        Condition condition = dom2LockMap.get(key).newCondition();
        dom2ConditionMap.put(key, condition);
        return condition;
    }

    private static class DomainKey {
        private String namespaceId;
        private String domName;
        private String serverIP;

        public String getChecksum() {
            return checksum;
        }

        public String getServerIP() {
            return serverIP;
        }

        public String getDomName() {
            return domName;
        }

        public String getNamespaceId() {
            return namespaceId;
        }

        private String checksum;

        public DomainKey(String namespaceId, String domName, String serverIP, String checksum) {
            this.namespaceId = namespaceId;
            this.domName = domName;
            this.serverIP = serverIP;
            this.checksum = checksum;
        }
    }
}
