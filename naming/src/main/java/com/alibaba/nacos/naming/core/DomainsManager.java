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
import com.alibaba.nacos.common.util.Pair;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.monitor.PerformanceLoggerThread;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.raft.Datum;
import com.alibaba.nacos.naming.raft.RaftCore;
import com.alibaba.nacos.naming.raft.RaftListener;
import com.alibaba.nacos.naming.raft.RaftPeer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author dungu.zpf
 */
@Component
public class DomainsManager {
    private Map<String, Domain> domMap = new ConcurrentHashMap<>();
    private Map<String, Domain> raftDomMap = new ConcurrentHashMap<>();
    private static Map<String, Set<Domain>> appName2Doms = new ConcurrentHashMap<>();

    private LinkedBlockingDeque<DomainKey> toBeUpdatedDomsQueue = new LinkedBlockingDeque<>(1024 * 1024);

    private Synchronizer synchronizer = new DomainStatusSynchronizer();

    /**
     * thread pool core size
     */
    private final static int DOMAIN_UPDATE_EXECUTOR_NUM = 2;

    private final Lock lock = new ReentrantLock();

    private Map<String, Lock> dom2LockMap = new ConcurrentHashMap<>();

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

    public Map<String, Domain> chooseDomMap() {
        return raftDomMap;
    }

    private void initConfig() {

        RaftPeer leader;
        while (true) {

            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Loggers.SRV_LOG.error("AUTO-INIT", "failed to auto init", e);
            }

            try {
                leader = RaftCore.getPeerSet().getLeader();
                if (leader != null) {
                    Loggers.SRV_LOG.info("AUTO-INIT", "no leader now, sleep 3 seconds and try again.");
                    break;
                }

            } catch (Throwable throwable) {
                Loggers.SRV_LOG.error("AUTO-INIT", "failed to auto init", throwable);
            }

        }
    }


    public void addUpdatedDom2Queue(String domName, String serverIP, String checksum) {
        lock.lock();
        try {
            toBeUpdatedDomsQueue.offer(new DomainKey(domName, serverIP, checksum), 5, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            toBeUpdatedDomsQueue.poll();
            toBeUpdatedDomsQueue.add(new DomainKey(domName, serverIP, checksum));
            Loggers.SRV_LOG.error("DOMAIN-STATUS", "Failed to add domain to be updatd to queue.", e);
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
            String checksum;

            try {
                while (true) {
                    DomainKey domainKey = null;

                    try {
                        domainKey = toBeUpdatedDomsQueue.take();
                    } catch (Exception e) {
                        Loggers.EVT_LOG.error("UPDATE-DOMAIN", "Exception while taking item from LinkedBlockingDeque.");
                    }

                    if (domainKey == null) {
                        continue;
                    }

                    domName = domainKey.getDomName();
                    serverIP = domainKey.getServerIP();
                    checksum = domainKey.getChecksum();

                    domainUpdateExecutor.execute(new DomUpdater(domName, serverIP));
                }
            } catch (Exception e) {
                Loggers.EVT_LOG.error("UPDATE-DOMAIN", "Exception while update dom: " + domName + "from " + serverIP, e);
            }
        }
    }

    private class DomUpdater implements Runnable {
        String domName;
        String serverIP;

        public DomUpdater(String domName, String serverIP) {
            this.domName = domName;
            this.serverIP = serverIP;
        }

        @Override
        public void run() {
            try {
                updatedDom2(domName, serverIP);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("DOMAIN-UPDATER", "Exception while update dom: " + domName + "from " + serverIP, e);
            }
        }
    }

    public void updatedDom2(String domName, String serverIP) {
        Message msg = synchronizer.get(serverIP, domName);
        JSONObject dom = JSON.parseObject(msg.getData());

        JSONArray ipList = dom.getJSONArray("ips");
        Map<String, Pair> ipsMap = new HashMap<>(ipList.size());
        for (int i=0; i<ipList.size(); i++) {

            String ip = ipList.getString(i);
            String[] strings = ip.split("_");
            ipsMap.put(strings[0], new Pair(strings[1], strings[2]));
        }

        VirtualClusterDomain raftVirtualClusterDomain = (VirtualClusterDomain) raftDomMap.get(domName);

        if (raftVirtualClusterDomain == null) {
            return;
        }

        List<IpAddress> ipAddresses = raftVirtualClusterDomain.allIPs();
        for (IpAddress ipAddress : ipAddresses) {
            Pair pair = ipsMap.get(ipAddress.toIPAddr());
            if (pair == null) {
                continue;
            }
            Boolean valid = Boolean.parseBoolean(pair.getKey());
            if (valid != ipAddress.isValid()) {
                ipAddress.setValid(Boolean.parseBoolean(pair.getKey()));
                ipAddress.setInvalidType(pair.getValue());
                Loggers.EVT_LOG.info("{" + domName + "} {SYNC} " +
                        "{IP-" + (ipAddress.isValid() ? "ENABLED" : "DISABLED") + "} " + ipAddress.getIp()
                        + ":" + ipAddress.getPort() + "@" + ipAddress.getClusterName());
            }
        }

        PushService.domChanged(raftVirtualClusterDomain.getName());
        StringBuilder stringBuilder = new StringBuilder();
        List<IpAddress> allIps = raftVirtualClusterDomain.allIPs();
        for (IpAddress ipAddress : allIps) {
            stringBuilder.append(ipAddress.toIPAddr()).append("_").append(ipAddress.isValid()).append(",");
        }

        Loggers.EVT_LOG.info("IP-UPDATED", "dom: " + raftVirtualClusterDomain.getName() + ", ips: " + stringBuilder.toString());

    }

    public Set<String> getAllDomNames() {
        return new HashSet<String>(chooseDomMap().keySet());
    }

    public List<String> getAllDomNamesList() {
        return new ArrayList<>(chooseDomMap().keySet());
    }

    public void setAllDomNames(List<String> allDomNames) {
        this.allDomNames = new HashSet<>(allDomNames);
    }

    public Set<String> getAllDomNamesCache() {
        if (Switch.isAllDomNameCache()) {
            if (CollectionUtils.isNotEmpty(allDomNames)) {
                return allDomNames;
            } else {
                allDomNames = getAllDomNames();
            }
        } else {
            return getAllDomNames();
        }

        return allDomNames;
    }

    private Set<String> allDomNames;

    public List<Domain> getResponsibleDoms() {
        List<Domain> result = new ArrayList<>();
        Map<String, Domain> domainMap = chooseDomMap();

        for (Map.Entry<String, Domain> entry : domainMap.entrySet()) {
            Domain domain = entry.getValue();
            if (DistroMapper.responsible(entry.getKey())) {
                result.add(domain);
            }
        }

        return result;
    }

    public int getResponsibleIPCount() {
        List<Domain> responsibleDoms = getResponsibleDoms();
        int count = 0;
        for (Domain domain : responsibleDoms) {
            count += domain.allIPs().size();
        }

        return count;
    }

    public void easyRemoveDom(String domName) throws Exception {

        Domain dom = raftDomMap.get(domName);
        if (dom != null) {
            RaftCore.signalDelete(UtilsAndCommons.getDomStoreKey(dom));
        }
    }

    public void easyAddOrReplaceDom(Domain newDom) throws Exception {
        VirtualClusterDomain virtualClusterDomain = null;
        if (newDom instanceof VirtualClusterDomain) {
            virtualClusterDomain = (VirtualClusterDomain) newDom;
            newDom = virtualClusterDomain;
        }
        RaftCore.signalPublish(UtilsAndCommons.getDomStoreKey(newDom), JSON.toJSONString(newDom));
    }

    public void easyReplaceIP4Dom(String domName, String clusterName, List<IpAddress> ips) throws Exception {
        Domain dom = chooseDomMap().get(domName);
        if (dom == null) {
            throw new IllegalArgumentException("dom doesn't exist: " + domName);
        }

        Cluster cluster = ((VirtualClusterDomain) dom).getClusterMap().get(clusterName);
        if (cluster == null) {
            throw new IllegalArgumentException("cluster doesn't exist: " + clusterName);
        }

        List<IpAddress> deadIPs = cluster.allIPs();
        deadIPs.removeAll(ips);

        easyAddIP4Dom(dom.getName(), ips);
        easyRemvIP4Dom(dom.getName(), deadIPs);
    }

    public void easyAddIP4Dom(String domName, List<IpAddress> ips) throws Exception {
        easyAddIP4Dom(domName, ips, -1);
    }

    public void easyAddIP4Dom(String domName, List<IpAddress> ips, long timestamp) throws Exception {
        easyAddIP4Dom(domName, ips, timestamp, -1);
    }

    public void easyAddIP4Dom(String domName, List<IpAddress> ips, long timestamp, long term) throws Exception {

        try {
            VirtualClusterDomain dom = (VirtualClusterDomain) chooseDomMap().get(domName);
            if (dom == null) {
                throw new IllegalArgumentException("dom doesn't exist: " + domName);
            }

            // set default port and site info if missing
            for (IpAddress ip : ips) {
                if (ip.getPort() == 0) {
                    ip.setPort(dom.getClusterMap().get(ip.getClusterName()).getDefIPPort());
                }
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
                    Loggers.SRV_LOG.info("cluster: " + ipAddress.getClusterName() + "  not found, ip: " + ipAddress.toJSON());
                    continue;
                }

                ipAddressMap.put(ipAddress.getDatumKey(), ipAddress);
            }

            if (ipAddressMap.size() <= 0) {
                throw new IllegalArgumentException("ip list can not be empty, dom: " + dom.getName() + ", ip list: "
                        + JSON.toJSONString(ipAddressMap.values()));
            }

            if (timestamp == -1) {
                RaftCore.signalPublish(UtilsAndCommons.getIPListStoreKey(dom),
                        JSON.toJSONString(ipAddressMap.values()));
            } else {
                String key = UtilsAndCommons.getIPListStoreKey(dom);
                String value = JSON.toJSONString(ipAddressMap.values());

                Datum datum = new Datum();
                datum.key = key;
                datum.value = value;
                datum.timestamp = timestamp;

                RaftPeer peer = new RaftPeer();
                peer.ip = RaftCore.getLeader().ip;
                peer.term.set(term);
                peer.voteFor = RaftCore.getLeader().voteFor;
                peer.heartbeatDueMs = RaftCore.getLeader().heartbeatDueMs;
                peer.leaderDueMs = RaftCore.getLeader().leaderDueMs;
                peer.state = RaftCore.getLeader().state;

                JSONObject json = new JSONObject();
                json.put("datum", datum);
                json.put("source", peer);

                RaftCore.onPublish(json);
            }
        } finally {
//            lock.unlock();
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
                    }
                }
            } catch (Throwable throwable) {
                Loggers.RAFT.error("NA", "error while processing json: " + oldJson, throwable);
            } finally {
                if (ipAddresses == null) {
                    ipAddresses = new ArrayList<>();
                }
            }
        }

        return ipAddresses;
    }

    public void easyRemvIP4Dom(String domName, List<IpAddress> ips) throws Exception {
        Lock lock = dom2LockMap.get(domName);
        if (lock == null) {
            throw new IllegalStateException("no lock for " + domName + ", operation is disabled now.");
        }

        try {
            lock.lock();
            Domain dom = chooseDomMap().get(domName);
            if (dom == null) {
                throw new IllegalArgumentException("domain doesn't exist: " + domName);
            }

            Datum datum = RaftCore.getDatum(UtilsAndCommons.getIPListStoreKey(dom));
            String oldJson = StringUtils.EMPTY;
            List<IpAddress> currentIPs = dom.allIPs();

            if (currentIPs.size() <= 0) {
                return;
            }

            Map<String, IpAddress> map = new ConcurrentHashMap(currentIPs.size());

            for (IpAddress ipAddress : currentIPs) {
                map.put(ipAddress.toIPAddr(), ipAddress);
            }

            if (datum != null) {
                oldJson = datum.value;
            }

            List<IpAddress> ipAddrs = setValid(oldJson, map);

            ipAddrs.removeAll(ips);

            RaftCore.signalPublish(UtilsAndCommons.getIPListStoreKey(dom), JSON.toJSONString(ipAddrs));
        } finally {
            lock.unlock();
        }
    }

    public Domain getDomain(String domName) {
        return chooseDomMap().get(domName);
    }

    public List<Domain> searchDomains(String regex) {
        List<Domain> result = new ArrayList<Domain>();
        for (Map.Entry<String, Domain> entry : chooseDomMap().entrySet()) {
            Domain dom = entry.getValue();

            String key = dom.getName() + ":" + ArrayUtils.toString(dom.getOwners());
            if (key.matches(regex)) {
                result.add(dom);
            }
        }

        return result;
    }

    public int getDomCount() {
        return chooseDomMap().size();
    }

    public int getIPCount() {
        int total = 0;
        List<String> doms = new ArrayList<String>(getAllDomNames());
        for (String dom : doms) {
            Domain domain = getDomain(dom);
            total += (domain.allIPs().size());
        }

        return total;
    }

    public Map<String, Domain> getRaftDomMap() {
        return raftDomMap;
    }

    public List<Domain> getPagedDom(int startPage, int pageSize) {
        ArrayList<Domain> domainList = new ArrayList<Domain>(chooseDomMap().values());
        if (pageSize >= chooseDomMap().size()) {
            return Collections.unmodifiableList(domainList);
        }

        List<Domain> resultList = new ArrayList<Domain>();
        for (int i = 0; i < domainList.size(); i++) {
            if (i < startPage * pageSize) {
                continue;
            }

            resultList.add(domainList.get(i));

            if (resultList.size() >= pageSize) {
                break;
            }
        }

        return resultList;
    }

    public static class DomainChecksum {
        public Map<String, String> domName2Checksum = new HashMap<String, String>();

        public void addItem(String domName, String checksum) {
            if (StringUtils.isEmpty(domName) || StringUtils.isEmpty(checksum)) {
                Loggers.SRV_LOG.warn("DOMAIN-CHECKSUM", "domName or checksum is empty,domName: " + domName + " checksum: " + checksum);
                return;
            }

            domName2Checksum.put(domName, checksum);
        }
    }

    private class DomainReporter implements Runnable {

        @Override
        public void run() {
            try {

                DomainChecksum checksum = new DomainChecksum();

                List<String> allDomainNames = new ArrayList<String>(getAllDomNames());

                if (allDomainNames.size() <= 0) {
                    //ignore
                    return;
                }

                for (String domName : allDomainNames) {
                    if (!DistroMapper.responsible(domName)) {
                        continue;
                    }

                    Domain domain = getDomain(domName);

                    if (domain == null || domain instanceof SwitchDomain) {
                        continue;
                    }

                    domain.recalculateChecksum();

                    checksum.addItem(domName, domain.getChecksum());
                }

                Message msg = new Message();

                msg.setData(JSON.toJSONString(checksum));

                List<String> sameSiteServers = NamingProxy.getSameSiteServers().get("sameSite");

                if (sameSiteServers == null || sameSiteServers.size() <= 0 || !NamingProxy.getServers().contains(NetUtils.localIP())) {
                    return;
                }

                for (String server : sameSiteServers) {
                    if (server.equals(NetUtils.localIP())) {
                        continue;
                    }
                    synchronizer.send(server, msg);
                }
            } catch (Exception e) {
                Loggers.SRV_LOG.error("DOMAIN-STATUS", "Exception while sending domain status: ", e);
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

        PerformanceLoggerThread performanceLoggerThread = new PerformanceLoggerThread();
        performanceLoggerThread.init(this);

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
                return StringUtils.startsWith(key, UtilsAndCommons.DOMAINS_DATA_ID);
            }

            @Override
            public boolean matchUnlistenKey(String key) {
                return StringUtils.equals(key, UtilsAndCommons.DOMAINS_DATA_ID + ".*");
            }

            @SuppressFBWarnings("JLM_JSR166_LOCK_MONITORENTER")
            @Override
            public void onChange(String key, String value) throws Exception {
                try {
                    if (StringUtils.isEmpty(value)) {
                        Loggers.SRV_LOG.warn("received empty push from raft, key=" + key);
                        return;
                    }

                    VirtualClusterDomain dom = VirtualClusterDomain.fromJSON(value);
                    if (dom == null) {
                        throw new IllegalStateException("dom parsing failed, json: " + value);
                    }

                    Loggers.RAFT.info("RAFT-NOTIFIER", "datum is changed, key:" + key + ", value:" + value);

                    Domain oldDom = raftDomMap.get(dom.getName());
                    if (oldDom != null) {
                        oldDom.update(dom);
                    } else {

                        if (!dom2LockMap.containsKey(dom.getName())) {
                            dom2LockMap.put(dom.getName(), new ReentrantLock());
                        }

                        Lock lock = dom2LockMap.get(dom.getName());


                        synchronized (lock) {
                            raftDomMap.put(dom.getName(), dom);
                            dom.init();
                            lock.notifyAll();
                        }

                        Loggers.SRV_LOG.info("[NEW-DOM-raft] " + dom.toJSON());
                    }

                } catch (Throwable e) {
                    Loggers.SRV_LOG.error("VIPSRV-DOM", "error while processing dom update", e);
                }
            }

            @Override
            public void onDelete(String key, String value) throws Exception {
                String name = StringUtils.removeStart(key, UtilsAndCommons.DOMAINS_DATA_ID + ".");
                Domain dom = raftDomMap.remove(name);
                Loggers.RAFT.info("RAFT-NOTIFIER", "datum is deleted, key:" + key + ", value:" + value);

                if (dom != null) {
                    dom.destroy();
                    Loggers.SRV_LOG.info("[DEAD-DOM] " + dom.toJSON());
                }
            }
        };
        RaftCore.listen(raftListener);

    }

    public Lock addLock(String domName) {
        Lock lock = new ReentrantLock();
        dom2LockMap.put(domName, lock);
        return lock;
    }

    public Map<String, Domain> getDomMap() {
        return new HashMap<String, Domain>(domMap);
    }

    private static class DomainKey {
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

        private String checksum;

        public DomainKey(String domName, String serverIP, String checksum) {
            this.domName = domName;
            this.serverIP = serverIP;
            this.checksum = checksum;
        }
    }
}
