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
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.members.Member;
import com.alibaba.nacos.naming.misc.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author nkorange
 */
@Component("distroMapper")
public class DistroMapper {

    public static final int STABLE_PERIOD = 60 * 1000;

    public List<String> getHealthyList() {
        return healthyList;
    }

    private List<String> healthyList = new ArrayList<String>();

    private Map<String, List<Server>> distroConfig = new ConcurrentHashMap<String, List<Server>>();

    private Set<String> liveSites = new HashSet<String>();

    public final String LOCALHOST_SITE = UtilsAndCommons.UNKNOWN_SITE;

    private long lastHealthServerMillis = 0L;

    private boolean autoDisabledHealthCheck = false;

    private Synchronizer synchronizer = new ServerStatusSynchronizer();

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private ServerListManager serverListManager;

    /**
     * init server list
     */
    @PostConstruct
    public void init() {
        UtilsAndCommons.SERVER_STATUS_EXECUTOR.schedule(new ServerStatusReporter(),
            60000, TimeUnit.MILLISECONDS);
    }

    private void onServerStatusUpdate(String configInfo) {

        String[] configs = configInfo.split("\r\n");
        if (configs.length == 0) {
            return;
        }

        distroConfig.clear();
        List<String> newHealthyList = new ArrayList<String>();

        for (String config : configs) {
            // site:ip:lastReportTime:weight
            String[] params = config.split("#");
            if (params.length <= 3) {
                Loggers.SRV_LOG.warn("received malformed distro map data: {}", config);
                continue;
            }

            Server server = new Server();

            server.site = params[0];
            server.ip = params[1];
            server.lastRefTime = Long.parseLong(params[2]);

            Date date = new Date(Long.parseLong(params[2]));
            server.lastRefTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);

            server.weight = params.length == 4 ? Integer.parseInt(params[3]) : 1;
            server.alive = System.currentTimeMillis() - server.lastRefTime < switchDomain.getDistroServerExpiredMillis();

            List<Server> list = distroConfig.get(server.site);
            if (list == null) {
                list = new ArrayList<Server>();
                distroConfig.put(server.site, list);
            }

            list.add(server);
        }

        liveSites.addAll(distroConfig.keySet());

        List<Server> servers = distroConfig.get(LOCALHOST_SITE);
        if (CollectionUtils.isEmpty(servers)) {
            return;
        }

        List<String> allSiteSrvs = new ArrayList<String>();
        for (Server server : servers) {
            server.adWeight = switchDomain.getAdWeight(server.ip) == null ? 0 : switchDomain.getAdWeight(server.ip);

            for (int i = 0; i < server.weight + server.adWeight; i++) {
                allSiteSrvs.add(server.ip);

                if (server.alive) {
                    newHealthyList.add(server.ip);
                }
            }
        }

        Collections.sort(newHealthyList);
        float curRatio = (float) newHealthyList.size() / allSiteSrvs.size();

        if (autoDisabledHealthCheck
            && curRatio > switchDomain.getDistroThreshold()
            && System.currentTimeMillis() - lastHealthServerMillis > STABLE_PERIOD) {
            Loggers.SRV_LOG.info("[VIPSRV-DISTRO] distro threshold restored and " +
                "stable now, enable health check. current ratio: {}", curRatio);

            switchDomain.setHealthCheckEnabled(true);

            // we must set this variable, otherwise it will conflict with user's action
            autoDisabledHealthCheck = false;
        }

        if (!CollectionUtils.isEqualCollection(healthyList, newHealthyList)) {
            // for every change disable healthy check for some while
            if (switchDomain.isHealthCheckEnabled()) {
                Loggers.SRV_LOG.info("[VIPSRV-DISTRO] healthy server list changed, " +
                        "disable health check for {} ms from now on, healthList: {}, newHealthyList {}",
                    STABLE_PERIOD, healthyList, newHealthyList);

                switchDomain.setHealthCheckEnabled(false);
                autoDisabledHealthCheck = true;

                lastHealthServerMillis = System.currentTimeMillis();
            }

            healthyList = newHealthyList;
        }
    }

    public synchronized void onReceiveServerStatus(String configInfo) {
        String[] configs = configInfo.split("\r\n");
        if (configs.length == 0) {
            return;
        }

        List<String> newHealthyList = new ArrayList<String>();
        List<Server> tmpServerList = new ArrayList<Server>();

        for (String config : configs) {
            tmpServerList.clear();
            // site:ip:lastReportTime:weight
            String[] params = config.split("#");
            if (params.length <= 3) {
                Loggers.SRV_LOG.warn("received malformed distro map data: {}", config);
                continue;
            }

            Server server = new Server();

            server.site = params[0];
            server.ip = params[1];
            server.lastRefTime = Long.parseLong(params[2]);

            if (!serverListManager.contains(server.ip)) {
                throw new IllegalArgumentException("ip: " + server.ip + " is not in serverlist");
            }

            Date date = new Date(Long.parseLong(params[2]));
            server.lastRefTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);

            server.weight = params.length == 4 ? Integer.parseInt(params[3]) : 1;
            server.alive = System.currentTimeMillis() - server.lastRefTime < switchDomain.getDistroServerExpiredMillis();
            List<Server> list = distroConfig.get(server.site);
            if (list == null || list.size() <= 0) {
                list = new ArrayList<Server>();
                list.add(server);
                distroConfig.put(server.site, list);
            }

            for (Server s : list) {
                String serverId = s.ip + "_" + s.site;
                String newServerId = server.ip + "_" + server.site;

                if (serverId.equals(newServerId)) {
                    if (s.alive != server.alive || s.weight != server.weight) {
                        Loggers.SRV_LOG.warn("server beat out of date, current: {}, last: {}",
                            JSON.toJSONString(server), JSON.toJSONString(s));
                    }
                    tmpServerList.add(server);
                    continue;
                }
                tmpServerList.add(s);
            }

            if (!tmpServerList.contains(server)) {
                tmpServerList.add(server);
            }

            distroConfig.put(server.site, tmpServerList);

        }
        liveSites.addAll(distroConfig.keySet());

        List<Server> servers = distroConfig.get(LOCALHOST_SITE);
        if (CollectionUtils.isEmpty(servers)) {
            return;
        }

        //local site servers
        List<String> allLocalSiteSrvs = new ArrayList<String>();
        for (Server server : servers) {

            if (server.ip.endsWith(":0")) {
                continue;
            }

            server.adWeight = switchDomain.getAdWeight(server.ip) == null ? 0 : switchDomain.getAdWeight(server.ip);

            for (int i = 0; i < server.weight + server.adWeight; i++) {

                if (!allLocalSiteSrvs.contains(server.ip)) {
                    allLocalSiteSrvs.add(server.ip);
                }

                if (server.alive && !newHealthyList.contains(server.ip)) {
                    newHealthyList.add(server.ip);
                }
            }
        }

        Collections.sort(newHealthyList);
        float curRatio = (float) newHealthyList.size() / allLocalSiteSrvs.size();

        if (autoDisabledHealthCheck
            && curRatio > switchDomain.getDistroThreshold()
            && System.currentTimeMillis() - lastHealthServerMillis > STABLE_PERIOD) {
            Loggers.SRV_LOG.info("[VIPSRV-DISTRO] distro threshold restored and " +
                "stable now, enable health check. current ratio: {}", curRatio);

            switchDomain.setHealthCheckEnabled(true);

            // we must set this variable, otherwise it will conflict with user's action
            autoDisabledHealthCheck = false;
        }

        if (!CollectionUtils.isEqualCollection(healthyList, newHealthyList)) {
            // for every change disable healthy check for some while
            if (switchDomain.isHealthCheckEnabled()) {
                Loggers.SRV_LOG.info("[VIPSRV-DISTRO] healthy server list changed, " +
                    "disable health check for {} ms from now on", STABLE_PERIOD);

                switchDomain.setHealthCheckEnabled(false);
                autoDisabledHealthCheck = true;

                lastHealthServerMillis = System.currentTimeMillis();
            }

            healthyList = newHealthyList;
        }
    }

    public boolean responsible(Cluster cluster, IpAddress ipAddress) {
        return switchDomain.isHealthCheckEnabled(cluster.getServiceName())
            && !cluster.getHealthCheckTask().isCancelled()
            && responsible(cluster.getServiceName())
            && cluster.contains(ipAddress);
    }

    public boolean responsible(String dom) {
        if (!switchDomain.isDistroEnabled()) {
            return true;
        }

        if (CollectionUtils.isEmpty(healthyList)) {
            // means distro config is not ready yet
            return false;
        }

        int index = healthyList.indexOf(NetUtils.localServer());
        int lastIndex = healthyList.lastIndexOf(NetUtils.localServer());
        if (lastIndex < 0 || index < 0) {
            return true;
        }

        int target = distroHash(dom) % healthyList.size();
        return target >= index && target <= lastIndex;
    }

    public String mapSrv(String dom) {
        if (CollectionUtils.isEmpty(healthyList) || !switchDomain.distroEnabled) {
            return NetUtils.localServer();
        }

        try {
            return healthyList.get(distroHash(dom) % healthyList.size());
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("distro mapper failed, return localhost: " + NetUtils.localServer(), e);

            return NetUtils.localServer();
        }
    }

    public int distroHash(String dom) {
        return Math.abs(dom.hashCode() % Integer.MAX_VALUE);
    }

    public String mapSrvName(String dom) {
        return UtilsAndCommons.UNKNOWN_HOST;
    }

    public Set<String> getLiveSites() {
        return liveSites;
    }

    public void clean() {
        cleanInvalidServers();

        for (Map.Entry<String, List<Server>> entry : distroConfig.entrySet()) {
            for (Server server : entry.getValue()) {
                //request other server to clean invalid servers
                if (!server.ip.equals(NetUtils.localServer())) {
                    requestOtherServerCleanInvalidServers(server.ip);
                }
            }

        }
    }

    private void cleanInvalidServers() {

        for (Map.Entry<String, List<Server>> entry : distroConfig.entrySet()) {
            List<Server> tmpServers = null;
            List<Server> currentServerList = entry.getValue();

            for (Server server : entry.getValue()) {
                if (!server.alive) {

                    tmpServers = new ArrayList<Server>();

                    for (Server server1 : currentServerList) {
                        String serverKey1 = server1.ip + "_" + server1.site;
                        String serverKey = server.ip + "_" + server.site;

                        if (!serverKey.equals(serverKey1) && !tmpServers.contains(server1)) {
                            tmpServers.add(server1);
                        }
                    }
                }
            }
            if (tmpServers != null) {
                distroConfig.put(entry.getKey(), tmpServers);
            }
        }
    }

    private void requestOtherServerCleanInvalidServers(String serverIP) {
        Map<String, String> params = new HashMap<String, String>(1);

        params.put("action", "without-diamond-clean");
        try {
            NamingProxy.reqAPI("distroStatus", params, serverIP, false);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[DISTRO-STATUS-CLEAN] Failed to request to clean server status to " + serverIP, e);
        }
    }

    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public static class Server {
        public String site = UtilsAndCommons.UNKNOWN_SITE;
        public String ip;
        public int weight = 1;
        /**
         * additional weight, used to adjust manually
         */
        public int adWeight;

        public boolean alive = false;

        public long lastRefTime = 0L;
        public String lastRefTimeStr;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Server server = (Server) o;

            return ip.equals(server.ip);

        }

        @Override
        public int hashCode() {
            return ip.hashCode();
        }
    }

    private class ServerStatusReporter implements Runnable {

        @Override
        public void run() {
            try {

                if (RunningConfig.getServerPort() <= 0) {
                    return;
                }

                for (String key : distroConfig.keySet()) {
                    for (Server server : distroConfig.get(key)) {
                        server.alive = System.currentTimeMillis() - server.lastRefTime < switchDomain.getDistroServerExpiredMillis();
                    }
                }

                int weight = Runtime.getRuntime().availableProcessors() / 2;
                if (weight <= 0) {
                    weight = 1;
                }

                long curTime = System.currentTimeMillis();
                String status = LOCALHOST_SITE + "#" + NetUtils.localServer() + "#" + curTime + "#" + weight + "\r\n";

                //send status to itself
                onReceiveServerStatus(status);

                List<Member> allServers = serverListManager.getMembers();

                if (!serverListManager.contains(NetUtils.localServer())) {
                    Loggers.SRV_LOG.error("local ip is not in serverlist, ip: {}, serverlist: {}", NetUtils.localServer(), allServers);
                    return;
                }

                if (allServers.size() > 0 && !NetUtils.localServer().contains(UtilsAndCommons.LOCAL_HOST_IP)) {
                    for (Member server : allServers) {
                        if (server.getKey().equals(NetUtils.localServer())) {
                            continue;
                        }

                        Message msg = new Message();
                        msg.setData(status);

                        synchronizer.send(server.getKey(), msg);

                    }
                }
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[SERVER-STATUS] Exception while sending server status", e);
            } finally {
                UtilsAndCommons.SERVER_STATUS_EXECUTOR.schedule(this, switchDomain.getServerStatusSynchronizationPeriodMillis(), TimeUnit.MILLISECONDS);
            }

        }
    }

    public Map<String, List<Server>> getDistroConfig() {
        return distroConfig;
    }
}
