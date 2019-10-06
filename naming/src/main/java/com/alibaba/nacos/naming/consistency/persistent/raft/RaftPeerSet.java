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
package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.servers.ServerChangeListener;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.apache.commons.collections.SortedBag;
import org.apache.commons.collections.bag.TreeBag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.alibaba.nacos.core.utils.SystemUtils.STANDALONE_MODE;

/**
 * @author nacos
 */
@Component
@DependsOn("serverListManager")
public class RaftPeerSet implements ServerChangeListener, ApplicationContextAware {

    @Autowired
    private ServerListManager serverListManager;

    private ApplicationContext applicationContext;

    private AtomicLong localTerm = new AtomicLong(0L);

    private RaftPeer leader = null;

    private Map<String, RaftPeer> peers = new HashMap<>();

    private Set<String> sites = new HashSet<>();

    private boolean ready = false;

    public RaftPeerSet() {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        /**
         * 注册监听  集群内节点的变化
         */
        serverListManager.listen(this);
    }

    /**
     * 返回leader
     * @return
     */
    public RaftPeer getLeader() {
        if (STANDALONE_MODE) {
            return local();
        }
        return leader;
    }

    public Set<String> allSites() {
        return sites;
    }

    public boolean isReady() {
        return ready;
    }

    public void remove(List<String> servers) {
        for (String server : servers) {
            peers.remove(server);
        }
    }

    /**
     * 修改peers内得节点信息
     * @param peer
     * @return
     */
    public RaftPeer update(RaftPeer peer) {
        peers.put(peer.ip, peer);
        return peer;
    }

    /**
     * ip对应得节点是否为leader
     * @param ip
     * @return
     */
    public boolean isLeader(String ip) {
        if (STANDALONE_MODE) {
            return true;
        }

        if (leader == null) {
            Loggers.RAFT.warn("[IS LEADER] no leader is available now!");
            return false;
        }

        return StringUtils.equals(leader.ip, ip);
    }

    public Set<String> allServersIncludeMyself() {
        return peers.keySet();
    }

    /**
     * 集群中得其他节点
     * @return
     */
    public Set<String> allServersWithoutMySelf() {
        Set<String> servers = new HashSet<String>(peers.keySet());

        // exclude myself
        /**
         * 排除自身
         */
        servers.remove(local().ip);

        return servers;
    }

    /**
     * 获得集群内所有节点列表
     * @return
     */
    public Collection<RaftPeer> allPeers() {
        return peers.values();
    }

    public int size() {
        return peers.size();
    }

    /**
     * 选举leader
     * @param candidate  remote节点
     * @return
     */
    public RaftPeer decideLeader(RaftPeer candidate) {
        /**
         * 更新peers中得RaftPeer信息   主要是voteFor
         */
        peers.put(candidate.ip, candidate);

        SortedBag ips = new TreeBag();
        int maxApproveCount = 0;
        String maxApprovePeer = null;
        for (RaftPeer peer : peers.values()) {
            /**
             * 未投票节点
             */
            if (StringUtils.isEmpty(peer.voteFor)) {
                continue;
            }

            /**
             * 累计各节点得票数
             */
            ips.add(peer.voteFor);
            /**
             * 计算每一轮选举提名最多得节点
             */
            if (ips.getCount(peer.voteFor) > maxApproveCount) {
                /**
                 * 票数
                 */
                maxApproveCount = ips.getCount(peer.voteFor);
                /**
                 * 提名最多得节点
                 */
                maxApprovePeer = peer.voteFor;
            }
        }

        /**
         * 获得合法票数
         */
        if (maxApproveCount >= majorityCount()) {
            /**
             * 选举出的leader节点   并修改节点得状态
             */
            RaftPeer peer = peers.get(maxApprovePeer);
            peer.state = RaftPeer.State.LEADER;

            /**
             * 修改本机对应得leader
             */
            if (!Objects.equals(leader, peer)) {
                leader = peer;
                /**
                 * 发布事件   nacos没有监听   留待接入方接听
                 */
                applicationContext.publishEvent(new LeaderElectFinishedEvent(this, leader));
                Loggers.RAFT.info("{} has become the LEADER", leader.ip);
            }
        }

        return leader;
    }

    /**
     * follower设置leader
     *
     * @param candidate
     * @return
     */
    public RaftPeer makeLeader(RaftPeer candidate) {
        /**
         * 更换leader
         */
        if (!Objects.equals(leader, candidate)) {
            leader = candidate;
            applicationContext.publishEvent(new MakeLeaderEvent(this, leader));
            Loggers.RAFT.info("{} has become the LEADER, local: {}, leader: {}",
                leader.ip, JSON.toJSONString(local()), JSON.toJSONString(leader));
        }

        /**
         * 遍历集群内节点
         */
        for (final RaftPeer peer : peers.values()) {
            Map<String, String> params = new HashMap<>(1);
            /**
             * 获取前leader节点的实时信息
             */
            if (!Objects.equals(peer, candidate) && peer.state == RaftPeer.State.LEADER) {
                try {
                    String url = RaftCore.buildURL(peer.ip, RaftCore.API_GET_PEER);
                    HttpClient.asyncHttpGet(url, null, params, new AsyncCompletionHandler<Integer>() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                Loggers.RAFT.error("[NACOS-RAFT] get peer failed: {}, peer: {}",
                                    response.getResponseBody(), peer.ip);
                                peer.state = RaftPeer.State.FOLLOWER;
                                return 1;
                            }

                            /**
                             * 修改前leader节点的实时信息
                             */
                            update(JSON.parseObject(response.getResponseBody(), RaftPeer.class));

                            return 0;
                        }
                    });
                } catch (Exception e) {
                    peer.state = RaftPeer.State.FOLLOWER;
                    Loggers.RAFT.error("[NACOS-RAFT] error while getting peer from peer: {}", peer.ip);
                }
            }
        }

        /**
         * 修改现在leader节点信息
         */
        return update(candidate);
    }

    /**
     * 获取本地节点信息
     * @return
     */
    public RaftPeer local() {
        /**
         * 本地节点对应得RaftPeer
         */
        RaftPeer peer = peers.get(NetUtils.localServer());

        /**
         * standalone模式  且peer为null
         */
        if (peer == null && SystemUtils.STANDALONE_MODE) {
            RaftPeer localPeer = new RaftPeer();
            localPeer.ip = NetUtils.localServer();
            localPeer.term.set(localTerm.get());
            peers.put(localPeer.ip, localPeer);
            return localPeer;
        }
        if (peer == null) {
            throw new IllegalStateException("unable to find local peer: " + NetUtils.localServer() + ", all peers: "
                + Arrays.toString(peers.keySet().toArray()));
        }

        return peer;
    }

    public RaftPeer get(String server) {
        return peers.get(server);
    }

    /**
     * 合法票数
     * @return
     */
    public int majorityCount() {
        return peers.size() / 2 + 1;
    }

    /**
     * 重置
     */
    public void reset() {

        /**
         * 本机对应得leader为null
         */
        leader = null;

        /**
         * 集群内所有节点得选举对象为null
         */
        for (RaftPeer peer : peers.values()) {
            peer.voteFor = null;
        }
    }

    public void setTerm(long term) {
        localTerm.set(term);
    }

    public long getTerm() {
        return localTerm.get();
    }

    public boolean contains(RaftPeer remote) {
        return peers.containsKey(remote.ip);
    }

    /**
     * 集群内节点列表变化通知   更新集群内节点列表
     * @param latestMembers
     */
    @Override
    public void onChangeServerList(List<Server> latestMembers) {

        Map<String, RaftPeer> tmpPeers = new HashMap<>(8);
        for (Server member : latestMembers) {

            /**
             * ip已存在
             */
            if (peers.containsKey(member.getKey())) {
                tmpPeers.put(member.getKey(), peers.get(member.getKey()));
                continue;
            }

            RaftPeer raftPeer = new RaftPeer();
            raftPeer.ip = member.getKey();

            // first time meet the local server:
            /**
             * 本机  则设置term
             */
            if (NetUtils.localServer().equals(member.getKey())) {
                raftPeer.term.set(localTerm.get());
            }

            /**
             * 新增ip
             */
            tmpPeers.put(member.getKey(), raftPeer);
        }

        // replace raft peer set:
        /**
         * 替换原有peers
         */
        peers = tmpPeers;

        if (RunningConfig.getServerPort() > 0) {
            ready = true;
        }

        Loggers.RAFT.info("raft peers changed: " + latestMembers);
    }

    @Override
    public void onChangeHealthyServerList(List<Server> latestReachableMembers) {

    }
}
