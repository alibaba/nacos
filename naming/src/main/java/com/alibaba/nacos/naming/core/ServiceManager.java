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
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.persistent.simpleraft.Datum;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@Component
public class ServiceManager implements DataListener {

    /**
     * Map<namespace, Map<group::serviceName, Service>>
     */
    private Map<String, Map<String, VirtualClusterDomain>> serviceMap = new ConcurrentHashMap<>();

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
    private PushService pushService;

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

    public ServiceManager() throws NacosException {
        // wait until distro-mapper ready because domain distribution check depends on it
        // TODO may be not necessary:
        while (distroMapper.getLiveSites().size() == 0) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1L));
            } catch (InterruptedException ignore) {
            }
        }

        UtilsAndCommons.DOMAIN_SYNCHRONIZATION_EXECUTOR.schedule(new DomainReporter(), 60000, TimeUnit.MILLISECONDS);

        UtilsAndCommons.DOMAIN_UPDATE_EXECUTOR.submit(new UpdatedDomainProcessor());

        consistencyService.listen(UtilsAndCommons.DOMAINS_DATA_ID_PRE, this);
    }

    public Map<String, VirtualClusterDomain> chooseDomMap(String namespaceId) {
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
        return StringUtils.startsWith(key, UtilsAndCommons.DOMAINS_DATA_ID_PRE);
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return StringUtils.equals(key, UtilsAndCommons.DOMAINS_DATA_ID_PRE + "*");
    }

    @Override
    public void onChange(String key, String value) throws Exception {
        try {
            if (StringUtils.isEmpty(value)) {
                Loggers.SRV_LOG.warn("received empty push from raft, key: {}", key);
                return;
            }

            VirtualClusterDomain dom = VirtualClusterDomain.fromJSON(value);
            if (dom == null) {
                throw new IllegalStateException("dom parsing failed, json: " + value);
            }

            if (StringUtils.isBlank(dom.getNamespaceId())) {
                dom.setNamespaceId(UtilsAndCommons.getDefaultNamespaceId());
            }

            Loggers.RAFT.info("[RAFT-NOTIFIER] datum is changed, key: {}, value: {}", key, value);

            Domain oldDom = getService(dom.getNamespaceId(), dom.getName());

            if (oldDom != null) {
                oldDom.update(dom);
            } else {

                addLockIfAbsent(UtilsAndCommons.assembleFullServiceName(dom.getNamespaceId(), dom.getName()));
                putDomain(dom);
                dom.init();
                consistencyService.listen(UtilsAndCommons.getDomStoreKey(dom), dom);
                Loggers.SRV_LOG.info("[NEW-DOM-RAFT] {}", dom.toJSON());
            }

            wakeUp(UtilsAndCommons.assembleFullServiceName(dom.getNamespaceId(), dom.getName()));

        } catch (Throwable e) {
            Loggers.SRV_LOG.error("[NACOS-DOM] error while processing dom update", e);
        }
    }

    @Override
    public void onDelete(String key, String value) throws Exception {
        String domKey = StringUtils.removeStart(key, UtilsAndCommons.DOMAINS_DATA_ID_PRE);
        String namespace = domKey.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[0];
        String name = domKey.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[1];
        VirtualClusterDomain dom = chooseDomMap(namespace).remove(name);
        Loggers.RAFT.info("[RAFT-NOTIFIER] datum is deleted, key: {}, value: {}", key, value);

        if (dom != null) {
            dom.destroy();
            consistencyService.remove(UtilsAndCommons.getIPListStoreKey(dom));
            consistencyService.unlisten(UtilsAndCommons.getDomStoreKey(dom), dom);
            Loggers.SRV_LOG.info("[DEAD-DOM] {}", dom.toJSON());
        }
    }

    private class UpdatedDomainProcessor implements Runnable {
        //get changed domain  from other server asynchronously

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

        VirtualClusterDomain raftVirtualClusterDomain = (VirtualClusterDomain) getService(namespaceId, domName);

        if (raftVirtualClusterDomain == null) {
            return;
        }

        List<IpAddress> ipAddresses = raftVirtualClusterDomain.allIPs();
        for (IpAddress ipAddress : ipAddresses) {

            Boolean valid = Boolean.parseBoolean(ipsMap.get(ipAddress.toIPAddr()));
            if (valid != ipAddress.isValid()) {
                ipAddress.setValid(valid);
                Loggers.EVT_LOG.info("{} {SYNC} IP-{} : {}@{}",
                    domName, (ipAddress.isValid() ? "ENABLED" : "DISABLED"),
                    ipAddress.getIp(), ipAddress.getPort(), ipAddress.getClusterName());
            }
        }

        pushService.domChanged(raftVirtualClusterDomain.getNamespaceId(), raftVirtualClusterDomain.getName());
        StringBuilder stringBuilder = new StringBuilder();
        List<IpAddress> allIps = raftVirtualClusterDomain.allIPs();
        for (IpAddress ipAddress : allIps) {
            stringBuilder.append(ipAddress.toIPAddr()).append("_").append(ipAddress.isValid()).append(",");
        }

        Loggers.EVT_LOG.info("[IP-UPDATED] dom: {}, ips: {}", raftVirtualClusterDomain.getName(), stringBuilder.toString());

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

    public Map<String, Set<Domain>> getResponsibleDoms() {
        Map<String, Set<Domain>> result = new HashMap<>(16);
        for (String namespaceId : serviceMap.keySet()) {
            result.put(namespaceId, new HashSet<>());
            for (Map.Entry<String, VirtualClusterDomain> entry : serviceMap.get(namespaceId).entrySet()) {
                Domain domain = entry.getValue();
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
            for (Map.Entry<String, VirtualClusterDomain> entry : serviceMap.get(namespaceId).entrySet()) {
                if (distroMapper.responsible(entry.getKey())) {
                    domCount++;
                }
            }
        }
        return domCount;
    }

    public int getResponsibleIPCount() {
        Map<String, Set<Domain>> responsibleDoms = getResponsibleDoms();
        int count = 0;
        for (String namespaceId : responsibleDoms.keySet()) {
            for (Domain domain : responsibleDoms.get(namespaceId)) {
                count += domain.allIPs().size();
            }
        }

        return count;
    }

    public void easyRemoveDom(String namespaceId, String serviceName) throws Exception {
        Domain dom = getService(namespaceId, serviceName);
        consistencyService.remove(UtilsAndCommons.getDomStoreKey(dom));
    }

    public void addOrReplaceService(VirtualClusterDomain newDom) throws Exception {
        consistencyService.put(UtilsAndCommons.getDomStoreKey(newDom), JSON.toJSONString(newDom));
    }

    /**
     * Register an instance to a service.
     * <p>
     * This method create service or cluster silently if they don't exist.
     *
     * @param namespaceId id of namespace
     * @param serviceName service name
     * @param instance    instance to register
     * @throws Exception any error occurred in the process
     */
    public void registerInstance(String namespaceId, String serviceName, IpAddress instance) throws Exception {

        VirtualClusterDomain service = (VirtualClusterDomain) getService(namespaceId, serviceName);

        boolean serviceUpdated = false;
        if (service == null) {
            service = new VirtualClusterDomain();
            service.setName(serviceName);
            service.setNamespaceId(namespaceId);
            // now valid the dom. if failed, exception will be thrown
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
            addOrReplaceService(service);
        }

        addInstance(namespaceId, serviceName, instance);
    }

    public void addInstance(String namespaceId, String serviceName, IpAddress... ips) throws NacosException {

        String key = UtilsAndCommons.getIPListStoreKey(getService(namespaceId, serviceName));

        VirtualClusterDomain dom = (VirtualClusterDomain) getService(namespaceId, serviceName);

        Map<String, IpAddress> ipAddressMap = addIpAddresses(dom, ips);

        String value = JSON.toJSONString(ipAddressMap.values());

        consistencyService.put(key, value);
    }

    public void removeInstance(String namespaceId, String serviceName, IpAddress... ips) throws NacosException {

        String key = UtilsAndCommons.getIPListStoreKey(getService(namespaceId, serviceName));

        VirtualClusterDomain dom = (VirtualClusterDomain) getService(namespaceId, serviceName);

        Map<String, IpAddress> ipAddressMap = substractIpAddresses(dom, ips);

        String value = JSON.toJSONString(ipAddressMap.values());

        consistencyService.put(key, value);
    }

    public IpAddress getInstance(String namespaceId, String serviceName, String cluster, String ip, int port) {
        VirtualClusterDomain service = (VirtualClusterDomain) getService(namespaceId, serviceName);
        if (service == null) {
            return null;
        }

        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);

        List<IpAddress> ips = service.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            throw new IllegalStateException("no ips found for cluster " + cluster + " in dom " + serviceName);
        }

        for (IpAddress ipAddress : ips) {
            if (ipAddress.getIp().equals(ip) && ipAddress.getPort() == port) {
                return ipAddress;
            }
        }

        return null;
    }

    public Map<String, IpAddress> updateIpAddresses(VirtualClusterDomain dom, String action, IpAddress... ips) throws NacosException {

        Datum datum1 = (Datum) consistencyService.get(UtilsAndCommons.getIPListStoreKey(dom));
        String oldJson = StringUtils.EMPTY;

        if (datum1 != null) {
            oldJson = datum1.value;
        }

        List<IpAddress> ipAddresses;
        List<IpAddress> currentIPs = dom.allIPs();
        Map<String, IpAddress> map = new ConcurrentHashMap(currentIPs.size());

        for (IpAddress ipAddress : currentIPs) {
            map.put(ipAddress.toIPAddr(), ipAddress);
        }

        ipAddresses = setValid(oldJson, map);

        Map<String, IpAddress> ipAddressMap = new HashMap<String, IpAddress>(ipAddresses.size());

        for (IpAddress ipAddress : ipAddresses) {
            ipAddressMap.put(ipAddress.getDatumKey(), ipAddress);
        }

        for (IpAddress ipAddress : ips) {
            if (!dom.getClusterMap().containsKey(ipAddress.getClusterName())) {
                Cluster cluster = new Cluster(ipAddress.getClusterName());
                cluster.setDom(dom);
                dom.getClusterMap().put(ipAddress.getClusterName(), cluster);
                Loggers.SRV_LOG.warn("cluster: {} not found, ip: {}, will create new cluster with default configuration.",
                    ipAddress.getClusterName(), ipAddress.toJSON());
            }

            if (UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE.equals(action)) {
                ipAddressMap.remove(ipAddress.getDatumKey());
            } else {
                ipAddressMap.put(ipAddress.getDatumKey(), ipAddress);
            }

        }

        if (ipAddressMap.size() <= 0 && UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD.equals(action)) {
            throw new IllegalArgumentException("ip list can not be empty, dom: " + dom.getName() + ", ip list: "
                + JSON.toJSONString(ipAddressMap.values()));
        }

        return ipAddressMap;
    }

    public Map<String, IpAddress> substractIpAddresses(VirtualClusterDomain dom, IpAddress... ips) throws NacosException {
        return updateIpAddresses(dom, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, ips);
    }

    public Map<String, IpAddress> addIpAddresses(VirtualClusterDomain dom, IpAddress... ips) throws NacosException {
        return updateIpAddresses(dom, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, ips);
    }

    private List<IpAddress> setValid(String oldJson, Map<String, IpAddress> map) {
        List<IpAddress> ipAddresses = new ArrayList<>();
        if (StringUtils.isNotEmpty(oldJson)) {
            try {
                ipAddresses = JSON.parseObject(oldJson, new TypeReference<List<IpAddress>>() {
                });
                for (IpAddress ipAddress : ipAddresses) {
                    IpAddress ipAddress1 = map.get(ipAddress.toIPAddr());
                    if (ipAddress1 != null) {
                        ipAddress.setValid(ipAddress1.isValid());
                        ipAddress.setLastBeat(ipAddress1.getLastBeat());
                    }
                }
            } catch (Throwable throwable) {
                Loggers.RAFT.error("error while processing json: " + oldJson, throwable);
            } finally {
                if (ipAddresses == null) {
                    ipAddresses = new ArrayList<>();
                }
            }
        }

        return ipAddresses;
    }

    public VirtualClusterDomain getService(String namespaceId, String domName) {
        if (serviceMap.get(namespaceId) == null) {
            return null;
        }
        return chooseDomMap(namespaceId).get(domName);
    }

    public void putDomain(VirtualClusterDomain domain) {
        if (!serviceMap.containsKey(domain.getNamespaceId())) {
            serviceMap.put(domain.getNamespaceId(), new ConcurrentHashMap<>(16));
        }
        serviceMap.get(domain.getNamespaceId()).put(domain.getName(), domain);
    }


    public List<VirtualClusterDomain> searchDomains(String namespaceId, String regex) {
        List<VirtualClusterDomain> result = new ArrayList<>();
        for (Map.Entry<String, VirtualClusterDomain> entry : chooseDomMap(namespaceId).entrySet()) {
            VirtualClusterDomain dom = entry.getValue();
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
            for (Domain domain : serviceMap.get(namespaceId).values()) {
                total += domain.allIPs().size();
            }
        }
        return total;
    }

    public Map<String, VirtualClusterDomain> getDomMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    public int getPagedDom(String namespaceId, int startPage, int pageSize, String keyword, List<Domain> domainList) {

        List<VirtualClusterDomain> matchList;

        if (chooseDomMap(namespaceId) == null) {
            return 0;
        }

        if (StringUtils.isNotBlank(keyword)) {
            matchList = searchDomains(namespaceId, ".*" + keyword + ".*");
        } else {
            matchList = new ArrayList<VirtualClusterDomain>(chooseDomMap(namespaceId).values());
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

                        Domain domain = getService(namespaceId, domName);

                        if (domain == null) {
                            continue;
                        }

                        domain.recalculateChecksum();

                        checksum.addItem(domName, domain.getChecksum());
                    }

                    Message msg = new Message();

                    msg.setData(JSON.toJSONString(checksum));

                    List<String> sameSiteServers = NamingProxy.getSameSiteServers().get("sameSite");

                    if (sameSiteServers == null || sameSiteServers.size() <= 0 || !NamingProxy.getServers().contains(NetUtils.localServer())) {
                        return;
                    }

                    for (String server : sameSiteServers) {
                        if (server.equals(NetUtils.localServer())) {
                            continue;
                        }
                        synchronizer.send(server, msg);
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
