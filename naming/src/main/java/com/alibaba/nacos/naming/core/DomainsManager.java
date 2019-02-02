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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.raft.Datum;
import com.alibaba.nacos.naming.raft.RaftCore;
import com.alibaba.nacos.naming.raft.RaftListener;
import com.alibaba.nacos.naming.raft.RaftPeer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@Component
public class DomainsManager {

    /**
     * Map<namespace, Map<group::serviceName, Service>>
     */
    private Map<String, Map<String, Domain>> serviceMap = new ConcurrentHashMap<>();

    private LinkedBlockingDeque<DomainKey> toBeUpdatedDomsQueue = new LinkedBlockingDeque<>(1024 * 1024);

    private Synchronizer synchronizer = new DomainStatusSynchronizer();

    /**
     * thread pool core size
     */
    private final static int DOMAIN_UPDATE_EXECUTOR_NUM = 2;

    private final Lock lock = new ReentrantLock();

    private Map<String, Condition> dom2ConditionMap = new ConcurrentHashMap<>();

    private Map<String, Lock> dom2LockMap = new ConcurrentHashMap<>();

    public Map<String, Lock> getDom2LockMap() {
        return dom2LockMap;
    }

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

    public Map<String, Domain> chooseDomMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    private void initConfig() {

        RaftPeer leader;
        while (true) {

            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Loggers.SRV_LOG.error("[AUTO-INIT] failed to auto init", e);
            }

            try {
                leader = RaftCore.getPeerSet().getLeader();
                if (leader != null) {
                    Loggers.SRV_LOG.info("[AUTO-INIT] leader is: {}", leader.ip);
                    break;
                }

            } catch (Throwable throwable) {
                Loggers.SRV_LOG.error("[AUTO-INIT] failed to auto init", throwable);
            }

        }
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

        VirtualClusterDomain raftVirtualClusterDomain = (VirtualClusterDomain) getDomain(namespaceId, domName);

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

        PushService.domChanged(raftVirtualClusterDomain.getNamespaceId(), raftVirtualClusterDomain.getName());
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
            for (Map.Entry<String, Domain> entry : serviceMap.get(namespaceId).entrySet()) {
                Domain domain = entry.getValue();
                if (DistroMapper.responsible(entry.getKey())) {
                    result.get(namespaceId).add(domain);
                }
            }
        }
        return result;
    }

    public int getResponsibleDomCount() {
        int domCount = 0;
        for (String namespaceId : serviceMap.keySet()) {
            for (Map.Entry<String, Domain> entry : serviceMap.get(namespaceId).entrySet()) {
                if (DistroMapper.responsible(entry.getKey())) {
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

        Domain dom = getDomain(namespaceId, serviceName);

        if (dom != null) {
            RaftCore.signalDelete(UtilsAndCommons.getDomStoreKey(dom));
        }
    }

    public void easyAddOrReplaceDom(Domain newDom) throws Exception {
        VirtualClusterDomain virtualClusterDomain;
        if (newDom instanceof VirtualClusterDomain) {
            virtualClusterDomain = (VirtualClusterDomain) newDom;
            newDom = virtualClusterDomain;
        }
        RaftCore.doSignalPublish(UtilsAndCommons.getDomStoreKey(newDom), JSON.toJSONString(newDom), true);
    }

    public void easyAddIP4Dom(String namespaceId, String domName, List<IpAddress> ips, long term) throws Exception {
        easyUpdateIP4Dom(namespaceId, domName, ips, term, "add");
    }

    public void easyRemvIP4Dom(String namespaceId, String domName, List<IpAddress> ips, long term) throws Exception {
        easyUpdateIP4Dom(namespaceId, domName, ips, term, "remove");
    }

    public void easyUpdateIP4Dom(String namespaceId, String domName, List<IpAddress> ips, long term, String action) throws Exception {

        VirtualClusterDomain dom = (VirtualClusterDomain) chooseDomMap(namespaceId).get(domName);
        if (dom == null) {
            throw new IllegalArgumentException("dom doesn't exist: " + domName);
        }

        try {

            if (!dom.getEnableClientBeat()) {
                getDom2LockMap().get(UtilsAndCommons.assembleFullServiceName(namespaceId, domName)).lock();
            }

            Datum datum1 = RaftCore.getDatum(UtilsAndCommons.getIPListStoreKey(dom));
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

            Loggers.EVT_LOG.info("{} {POS} {IP-UPDATE} {}, action: {}", dom, ips, action);

            String key = UtilsAndCommons.getIPListStoreKey(dom);
            String value = JSON.toJSONString(ipAddressMap.values());

            Datum datum = new Datum();
            datum.key = key;
            datum.value = value;

            datum.timestamp.set(datum1 == null ? 1 : datum1.timestamp.get() + 1);

            Loggers.RAFT.info("datum " + key + " updated:" + datum.timestamp.get());

            RaftPeer peer = new RaftPeer();
            peer.ip = RaftCore.getLeader().ip;
            peer.term.set(term);
            peer.voteFor = RaftCore.getLeader().voteFor;
            peer.heartbeatDueMs = RaftCore.getLeader().heartbeatDueMs;
            peer.leaderDueMs = RaftCore.getLeader().leaderDueMs;
            peer.state = RaftCore.getLeader().state;

            boolean increaseTerm = !((VirtualClusterDomain) getDomain(namespaceId, domName)).getEnableClientBeat();

            RaftCore.onPublish(datum, peer, increaseTerm);
        } finally {
            if (!dom.getEnableClientBeat()) {
                getDom2LockMap().get(UtilsAndCommons.assembleFullServiceName(namespaceId, domName)).unlock();
            }
        }

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

    public Domain getDomain(String namespaceId, String domName) {
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


    public List<Domain> searchDomains(String namespaceId, String regex) {
        List<Domain> result = new ArrayList<Domain>();
        for (Map.Entry<String, Domain> entry : chooseDomMap(namespaceId).entrySet()) {
            Domain dom = entry.getValue();

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

    public Map<String, Domain> getDomMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    public int getPagedDom(String namespaceId, int startPage, int pageSize, String keyword, List<Domain> domainList) {

        List<Domain> matchList;

        if (chooseDomMap(namespaceId) == null) {
            return 0;
        }

        if (StringUtils.isNotBlank(keyword)) {
            matchList = searchDomains(namespaceId, ".*" + keyword + ".*");
        } else {
            matchList = new ArrayList<Domain>(chooseDomMap(namespaceId).values());
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
            this.namespaceId = Constants.REQUEST_PARAM_DEFAULT_NAMESPACE_ID;
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
                        if (!DistroMapper.responsible(domName)) {
                            continue;
                        }

                        Domain domain = getDomain(namespaceId, domName);

                        if (domain == null || domain instanceof SwitchDomain) {
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
                UtilsAndCommons.DOMAIN_SYNCHRONIZATION_EXECUTOR.schedule(this, Switch.getDomStatusSynchronizationPeriodMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public DomainsManager() {
        // wait until distro-mapper ready because domain distribution check depends on it
        while (DistroMapper.getLiveSites().size() == 0) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1L));
            } catch (InterruptedException ignore) {
            }
        }

        UtilsAndCommons.DOMAIN_SYNCHRONIZATION_EXECUTOR.schedule(new DomainReporter(), 60000, TimeUnit.MILLISECONDS);

        UtilsAndCommons.DOMAIN_UPDATE_EXECUTOR.submit(new UpdatedDomainProcessor());

        UtilsAndCommons.INIT_CONFIG_EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                initConfig();
            }
        });

        final RaftListener raftListener = new RaftListener() {
            @Override
            public boolean interests(String key) {
                return StringUtils.startsWith(key, UtilsAndCommons.DOMAINS_DATA_ID_PRE);
            }

            @Override
            public boolean matchUnlistenKey(String key) {
                return StringUtils.equals(key, UtilsAndCommons.DOMAINS_DATA_ID_PRE + "*");
            }

            @SuppressFBWarnings("JLM_JSR166_LOCK_MONITORENTER")
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

                    Domain oldDom = getDomain(dom.getNamespaceId(), dom.getName());

                    if (oldDom != null) {
                        oldDom.update(dom);
                    } else {

                        addLockIfAbsent(UtilsAndCommons.assembleFullServiceName(dom.getNamespaceId(), dom.getName()));

                        putDomain(dom);
                        dom.init();
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
                Domain dom = chooseDomMap(namespace).remove(name);
                Loggers.RAFT.info("[RAFT-NOTIFIER] datum is deleted, key: {}, value: {}", key, value);

                if (dom != null) {
                    dom.destroy();
                    Loggers.SRV_LOG.info("[DEAD-DOM] {}", dom.toJSON());
                }
            }
        };
        RaftCore.listen(raftListener);

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
