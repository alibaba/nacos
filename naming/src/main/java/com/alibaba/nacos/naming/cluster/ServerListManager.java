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
package com.alibaba.nacos.naming.cluster;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.servers.ServerChangeListener;
import com.alibaba.nacos.naming.misc.*;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.core.utils.SystemUtils.*;

/**
 * The manager to globally refresh and operate server list.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component("serverListManager")
public class ServerListManager {

    private static final int STABLE_PERIOD = 60 * 1000;

    @Autowired
    private SwitchDomain switchDomain;

    private List<ServerChangeListener> listeners = new ArrayList<>();

    /**
     * 集群内所有的节点列表
     */
    private List<Server> servers = new ArrayList<>();

    /**
     * 集群内所有健康的节点列表
     */
    private List<Server> healthyServers = new ArrayList<>();

    private Map<String, List<Server>> distroConfig = new ConcurrentHashMap<>();

    private Map<String, Long> distroBeats = new ConcurrentHashMap<>(16);

    private Set<String> liveSites = new HashSet<>();

    private final static String LOCALHOST_SITE = UtilsAndCommons.UNKNOWN_SITE;

    private long lastHealthServerMillis = 0L;

    private boolean autoDisabledHealthCheck = false;

    private Synchronizer synchronizer = new ServerStatusSynchronizer();

    /**
     * 注册监听   集群内节点的变化
     * @param listener
     */
    public void listen(ServerChangeListener listener) {
        listeners.add(listener);
    }

    @PostConstruct
    public void init() {
        /**
         * 配置文件对应的集群列表有变化
         */
        GlobalExecutor.registerServerListUpdater(new ServerListUpdater());
        /**
         * 根据心跳判断集群内成员状态
         */
        GlobalExecutor.registerServerStatusReporter(new ServerStatusReporter(), 5000);
    }

    /**
     * 读取cluster.conf获得nacos集群列表
     * @return
     */
    private List<Server> refreshServerList() {

        List<Server> result = new ArrayList<>();

        /**
         * 单点模式
         */
        if (STANDALONE_MODE) {
            Server server = new Server();
            server.setIp(NetUtils.getLocalAddress());
            server.setServePort(RunningConfig.getServerPort());
            result.add(server);
            return result;
        }

        List<String> serverList = new ArrayList<>();
        try {
            /**
             * 读取cluster.conf中的集群列表
             */
            serverList = readClusterConf();
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("failed to get config: " + CLUSTER_CONF_FILE_PATH, e);
        }

        if (Loggers.SRV_LOG.isDebugEnabled()) {
            Loggers.SRV_LOG.debug("SERVER-LIST from cluster.conf: {}", result);
        }

        //use system env
        /**
         * 集群信息为空   则读取系统中的环境变量
         */
        if (CollectionUtils.isEmpty(serverList)) {
            serverList = SystemUtils.getIPsBySystemEnv(UtilsAndCommons.SELF_SERVICE_CLUSTER_ENV);
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("SERVER-LIST from system variable: {}", result);
            }
        }

        /**
         * 遍历集群信息   获取Server
         */
        if (CollectionUtils.isNotEmpty(serverList)) {

            for (int i = 0; i < serverList.size(); i++) {

                String ip;
                int port;
                String server = serverList.get(i);
                if (server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
                    ip = server.split(UtilsAndCommons.IP_PORT_SPLITER)[0];
                    port = Integer.parseInt(server.split(UtilsAndCommons.IP_PORT_SPLITER)[1]);
                } else {
                    ip = server;
                    port = RunningConfig.getServerPort();
                }

                Server member = new Server();
                member.setIp(ip);
                member.setServePort(port);
                result.add(member);
            }
        }

        return result;
    }

    /**
     * 集群列表中是否有当前地址
     * @param s
     * @return
     */
    public boolean contains(String s) {
        for (Server server : servers) {
            if (server.getKey().equals(s)) {
                return true;
            }
        }
        return false;
    }

    public List<Server> getServers() {
        return servers;
    }

    public List<Server> getHealthyServers() {
        return healthyServers;
    }

    /**
     * nacos集群内的节点有变化（节点地址发生变化   节点本身出现故障以及恢复）
     */
    private void notifyListeners() {

        GlobalExecutor.notifyServerListChange(new Runnable() {
            @Override
            public void run() {
                for (ServerChangeListener listener : listeners) {
                    listener.onChangeServerList(servers);
                    listener.onChangeHealthyServerList(healthyServers);
                }
            }
        });
    }

    public Map<String, List<Server>> getDistroConfig() {
        return distroConfig;
    }

    /**
     * 接受集群内节点状态
     * @param configInfo  unknown#192.168.56.1:8848#1566292196551#6
     */
    public synchronized void onReceiveServerStatus(String configInfo) {

        Loggers.SRV_LOG.info("receive config info: {}", configInfo);

        String[] configs = configInfo.split("\r\n");
        if (configs.length == 0) {
            return;
        }

        List<Server> newHealthyList = new ArrayList<>();
        List<Server> tmpServerList = new ArrayList<>();

        for (String config : configs) {
            tmpServerList.clear();
            // site:ip:lastReportTime:weight
            String[] params = config.split("#");
            if (params.length <= 3) {
                Loggers.SRV_LOG.warn("received malformed distro map data: {}", config);
                continue;
            }

            /**
             * 存储节点数据   ip  端口   状态   上次心跳时间
             */
            Server server = new Server();

            server.setSite(params[0]);
            server.setIp(params[1].split(UtilsAndCommons.IP_PORT_SPLITER)[0]);
            server.setServePort(Integer.parseInt(params[1].split(UtilsAndCommons.IP_PORT_SPLITER)[1]));
            server.setLastRefTime(Long.parseLong(params[2]));

            /**
             * 集群列表不包括当前地址
             */
            if (!contains(server.getKey())) {
                throw new IllegalArgumentException("server: " + server.getKey() + " is not in serverlist");
            }

            /**
             * 上次心跳时间
             */
            Long lastBeat = distroBeats.get(server.getKey());
            long now = System.currentTimeMillis();
            if (null != lastBeat) {
                /**
                 * 服务是否Alive
                 */
                server.setAlive(now - lastBeat < switchDomain.getDistroServerExpiredMillis());
            }
            /**
             * 设置distroBeats
             */
            distroBeats.put(server.getKey(), now);

            Date date = new Date(Long.parseLong(params[2]));
            server.setLastRefTimeStr(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));

            server.setWeight(params.length == 4 ? Integer.parseInt(params[3]) : 1);

            /**
             * 查询和server对应的site相同状态的节点列表
             */
            List<Server> list = distroConfig.get(server.getSite());
            if (list == null || list.size() <= 0) {
                list = new ArrayList<>();
                list.add(server);
                distroConfig.put(server.getSite(), list);
            }

            for (Server s : list) {
                String serverId = s.getKey() + "_" + s.getSite();
                String newServerId = server.getKey() + "_" + server.getSite();

                /**
                 * 集合中的server和心跳上送的server key和site的组合 是否相同
                 */
                if (serverId.equals(newServerId)) {
                    if (s.isAlive() != server.isAlive() || s.getWeight() != server.getWeight()) {
                        Loggers.SRV_LOG.warn("server beat out of date, current: {}, last: {}",
                            JSON.toJSONString(server), JSON.toJSONString(s));
                    }
                    /**
                     * 以新数据为准
                     */
                    tmpServerList.add(server);
                    continue;
                }
                tmpServerList.add(s);
            }

            /**
             * tmpServerList中需要包含server
             */
            if (!tmpServerList.contains(server)) {
                tmpServerList.add(server);
            }

            /**
             * 更新distroConfig中  site对应的集合
             */
            distroConfig.put(server.getSite(), tmpServerList);
        }
        liveSites.addAll(distroConfig.keySet());
    }

    public void clean() {
        cleanInvalidServers();

        for (Map.Entry<String, List<Server>> entry : distroConfig.entrySet()) {
            for (Server server : entry.getValue()) {
                //request other server to clean invalid servers
                if (!server.getKey().equals(NetUtils.localServer())) {
                    requestOtherServerCleanInvalidServers(server.getKey());
                }
            }

        }
    }

    public Set<String> getLiveSites() {
        return liveSites;
    }

    private void cleanInvalidServers() {
        for (Map.Entry<String, List<Server>> entry : distroConfig.entrySet()) {
            List<Server> currentServers = entry.getValue();
            if (null == currentServers) {
                distroConfig.remove(entry.getKey());
                continue;
            }

            currentServers.removeIf(server -> !server.isAlive());
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

    public class ServerListUpdater implements Runnable {

        @Override
        public void run() {
            try {
                /**
                 * 读取cluster.conf获得nacos集群列表
                 */
                List<Server> refreshedServers = refreshServerList();
                List<Server> oldServers = servers;

                if (CollectionUtils.isEmpty(refreshedServers)) {
                    Loggers.RAFT.warn("refresh server list failed, ignore it.");
                    return;
                }

                boolean changed = false;

                /**
                 * 新旧集群列表比较   获取新增的Server
                 */
                List<Server> newServers = (List<Server>) CollectionUtils.subtract(refreshedServers, oldServers);
                if (CollectionUtils.isNotEmpty(newServers)) {
                    servers.addAll(newServers);
                    changed = true;
                    Loggers.RAFT.info("server list is updated, new: {} servers: {}", newServers.size(), newServers);
                }

                /**
                 * 移除被遗弃的server
                 */
                List<Server> deadServers = (List<Server>) CollectionUtils.subtract(oldServers, refreshedServers);
                if (CollectionUtils.isNotEmpty(deadServers)) {
                    servers.removeAll(deadServers);
                    changed = true;
                    Loggers.RAFT.info("server list is updated, dead: {}, servers: {}", deadServers.size(), deadServers);
                }

                /**
                 * 集群列表有变化  发送通知
                 */
                if (changed) {
                    /**
                     * 发送通知
                     */
                    notifyListeners();
                }

            } catch (Exception e) {
                Loggers.RAFT.info("error while updating server list.", e);
            }
        }
    }


    private class ServerStatusReporter implements Runnable {

        @Override
        public void run() {
            try {

                if (RunningConfig.getServerPort() <= 0) {
                    return;
                }

                /**
                 * 通过检查心跳   判断集群内的节点是否alive   有变化时   发送通知
                 * 节点心跳的记录  是由onReceiveServerStatus来完成的
                 */
                checkDistroHeartbeat();

                int weight = Runtime.getRuntime().availableProcessors() / 2;
                if (weight <= 0) {
                    weight = 1;
                }

                long curTime = System.currentTimeMillis();
                /**
                 * unknown#192.168.56.1:8848#1566292196551#6
                 */
                String status = LOCALHOST_SITE + "#" + NetUtils.localServer() + "#" + curTime + "#" + weight + "\r\n";

                //send status to itself
                /**
                 * 处理本地节点状态
                 */
                onReceiveServerStatus(status);

                /**
                 * 获取集群中的节点列表
                 */
                List<Server> allServers = getServers();

                /**
                 * 集群中不包含本机地址
                 */
                if (!contains(NetUtils.localServer())) {
                    Loggers.SRV_LOG.error("local ip is not in serverlist, ip: {}, serverlist: {}", NetUtils.localServer(), allServers);
                    return;
                }

                /**
                 * 向集群中的其他节点发送status
                 */
                if (allServers.size() > 0 && !NetUtils.localServer().contains(UtilsAndCommons.LOCAL_HOST_IP)) {
                    for (com.alibaba.nacos.naming.cluster.servers.Server server : allServers) {
                        /**
                         * 排除本机地址
                         */
                        if (server.getKey().equals(NetUtils.localServer())) {
                            continue;
                        }

                        Message msg = new Message();
                        msg.setData(status);

                        /**
                         * 向集群中的其他节点发送status
                         */
                        synchronizer.send(server.getKey(), msg);

                    }
                }
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[SERVER-STATUS] Exception while sending server status", e);
            } finally {
                GlobalExecutor.registerServerStatusReporter(this, switchDomain.getServerStatusSynchronizationPeriodMillis());
            }

        }
    }

    /**
     * 检查其他节点发送的心跳
     */
    private void checkDistroHeartbeat() {

        Loggers.SRV_LOG.debug("check distro heartbeat.");

        /**
         * 获取状态为unknown的节点
         */
        List<Server> servers = distroConfig.get(LOCALHOST_SITE);
        if (CollectionUtils.isEmpty(servers)) {
            return;
        }

        List<Server> newHealthyList = new ArrayList<>(servers.size());
        long now = System.currentTimeMillis();
        for (Server s: servers) {
            /**
             * 节点上一次的心跳时间
             */
            Long lastBeat = distroBeats.get(s.getKey());
            if (null == lastBeat) {
                continue;
            }

            /**
             * 当前节点是否alive
             */
            s.setAlive(now - lastBeat < switchDomain.getDistroServerExpiredMillis());
        }

        //local site servers
        List<String> allLocalSiteSrvs = new ArrayList<>();
        for (Server server : servers) {

            /**
             * 端口为0   则忽略
             */
            if (server.getKey().endsWith(":0")) {
                continue;
            }

            server.setAdWeight(switchDomain.getAdWeight(server.getKey()) == null ? 0 : switchDomain.getAdWeight(server.getKey()));

            for (int i = 0; i < server.getWeight() + server.getAdWeight(); i++) {

                /**
                 * 所有的节点
                 */
                if (!allLocalSiteSrvs.contains(server.getKey())) {
                    allLocalSiteSrvs.add(server.getKey());
                }

                /**
                 * alive的节点
                 */
                if (server.isAlive() && !newHealthyList.contains(server)) {
                    newHealthyList.add(server);
                }
            }
        }

        Collections.sort(newHealthyList);

        /**
         * 健康心跳的节点的比率
         */
        float curRatio = (float) newHealthyList.size() / allLocalSiteSrvs.size();

        /**
         * autoDisabledHealthCheck  &&  健康心跳节点比率大于distroThreshold  &&  时间间隔大于一分钟
         */
        if (autoDisabledHealthCheck
            && curRatio > switchDomain.getDistroThreshold()
            && System.currentTimeMillis() - lastHealthServerMillis > STABLE_PERIOD) {
            Loggers.SRV_LOG.info("[NACOS-DISTRO] distro threshold restored and " +
                "stable now, enable health check. current ratio: {}", curRatio);

            switchDomain.setHealthCheckEnabled(true);

            // we must set this variable, otherwise it will conflict with user's action
            autoDisabledHealthCheck = false;
        }

        /**
         * 集群内健康（alive）的节点发生变化
         */
        if (!CollectionUtils.isEqualCollection(healthyServers, newHealthyList)) {
            // for every change disable healthy check for some while
            if (switchDomain.isHealthCheckEnabled()) {
                Loggers.SRV_LOG.info("[NACOS-DISTRO] healthy server list changed, " +
                        "disable health check for {} ms from now on, old: {}, new: {}", STABLE_PERIOD,
                    healthyServers, newHealthyList);

                switchDomain.setHealthCheckEnabled(false);
                autoDisabledHealthCheck = true;

                lastHealthServerMillis = System.currentTimeMillis();
            }

            /**
             * 更新healthyServers
             */
            healthyServers = newHealthyList;

            /**
             * 发送通知
             */
            notifyListeners();
        }
    }

}
