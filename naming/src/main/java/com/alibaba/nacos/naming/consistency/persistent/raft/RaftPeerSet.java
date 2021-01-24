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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.apache.commons.collections.SortedBag;
import org.apache.commons.collections.bag.TreeBag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sets of raft peers.
 *
 * @author nacos
 * @deprecated will remove in 1.4.x
 */
@Deprecated
@Component
@DependsOn("ProtocolManager")
public class RaftPeerSet extends MemberChangeListener implements Closeable {

    private final ServerMemberManager memberManager;

    private AtomicLong localTerm = new AtomicLong(0L);

    private RaftPeer leader = null;

    private volatile Map<String, RaftPeer> peers = new HashMap<>(8);

    private Set<String> sites = new HashSet<>();

    private volatile boolean ready = false;

    private Set<Member> oldMembers = new HashSet<>();

    public RaftPeerSet(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }

    @PostConstruct
    public void init() {
        /**
         * 注册监听  集群内节点的变化
         */
        NotifyCenter.registerSubscriber(this);
        changePeers(memberManager.allMembers());
    }

    @Override
    public void shutdown() throws NacosException {
        this.localTerm.set(-1);
        this.leader = null;
        this.peers.clear();
        this.sites.clear();
        this.ready = false;
        this.oldMembers.clear();
    }
    /**
     * 返回leader
     * @return
     */
    public RaftPeer getLeader() {
        if (ApplicationUtils.getStandaloneMode()) {
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

    /**
     * Remove raft node.
     *
     * @param servers node address need to be removed
     */
    public void remove(List<String> servers) {
        for (String server : servers) {
            peers.remove(server);
        }
    }

    /**
     * Update raft peer.
     * 修改peers内得节点信息
     *
     * @param peer new peer.
     * @return new peer
     */
    public RaftPeer update(RaftPeer peer) {
        peers.put(peer.ip, peer);
        return peer;
    }

    /**
     * Judge whether input address is leader.
     * ip对应得节点是否为leader
     * @param ip peer address
     * @return true if is leader or stand alone, otherwise false
     */
    public boolean isLeader(String ip) {
        if (ApplicationUtils.getStandaloneMode()) {
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
     * Get all servers excludes current peer.
     * 集群中得其他节点
     * @return all servers excludes current peer
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
     * Calculate and decide which peer is leader. If has new peer has more than half vote, change leader to new peer.
     * 选举leader
     * @param candidate new candidate
     * @return new leader if new candidate has more than half vote, otherwise old leader
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
                ApplicationUtils.publishEvent(new LeaderElectFinishedEvent(this, leader, local()));
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
     * Set leader as new candidate.
     * follower设置leader
     * @param candidate new candidate
     * @return new leader
     */
    public RaftPeer makeLeader(RaftPeer candidate) {
        /**
         * 更换leader
         */
        if (!Objects.equals(leader, candidate)) {
            leader = candidate;
            ApplicationUtils.publishEvent(new MakeLeaderEvent(this, leader, local()));
            Loggers.RAFT
                    .info("{} has become the LEADER, local: {}, leader: {}", leader.ip, JacksonUtils.toJson(local()),
                            JacksonUtils.toJson(leader));
        }


        /**
         * 获取前leader节点的实时信息
         */
        for (final RaftPeer peer : peers.values()) {
            Map<String, String> params = new HashMap<>(1);
            /**
             * 上一个leader节点
             */
            if (!Objects.equals(peer, candidate) && peer.state == RaftPeer.State.LEADER) {
                try {
                    /**
                     * 查询上一个leader节点的信息
                     */
                    String url = RaftCore.buildUrl(peer.ip, RaftCore.API_GET_PEER);
                    HttpClient.asyncHttpGet(url, null, params, new Callback<String>() {
                        @Override
                        public void onReceive(RestResult<String> result) {
                            if (!result.ok()) {
                                Loggers.RAFT
                                        .error("[NACOS-RAFT] get peer failed: {}, peer: {}", result.getCode(), peer.ip);
                                peer.state = RaftPeer.State.FOLLOWER;
                                return;
                            }
                            /**
                             * 修改前leader节点的实时信息
                             */
                            update(JacksonUtils.toObj(result.getData(), RaftPeer.class));
                        }

                        @Override
                        public void onError(Throwable throwable) {

                        }

                        @Override
                        public void onCancel() {

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
     * Get local raft peer.
     * 获取本地节点信息
     *
     * @return local raft peer
     */
    public RaftPeer local() {
        /**
         * 本地节点对应得RaftPeer
         */
        RaftPeer peer = peers.get(ApplicationUtils.getLocalAddress());
        /**
         * standalone模式  且peer为null
         */
        if (peer == null && ApplicationUtils.getStandaloneMode()) {
            RaftPeer localPeer = new RaftPeer();
            localPeer.ip = NetUtils.localServer();
            localPeer.term.set(localTerm.get());
            peers.put(localPeer.ip, localPeer);
            return localPeer;
        }
        if (peer == null) {
            throw new IllegalStateException(
                    "unable to find local peer: " + NetUtils.localServer() + ", all peers: " + Arrays
                            .toString(peers.keySet().toArray()));
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
     * Reset set.
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

    @Override
    public void onEvent(MembersChangeEvent event) {
        Collection<Member> members = event.getMembers();
        Collection<Member> newMembers = new HashSet<>(members);
        newMembers.removeAll(oldMembers);

        // If an IP change occurs, the change starts
        if (!newMembers.isEmpty()) {
            changePeers(members);
        }

        oldMembers.clear();
        oldMembers.addAll(members);
    }
    /**
     * 集群内节点列表变化通知   更新集群内节点列表
     * @param latestMembers
     */
    protected void changePeers(Collection<Member> members) {
        Map<String, RaftPeer> tmpPeers = new HashMap<>(members.size());

        for (Member member : members) {
            final String address = member.getAddress();
            /**
             * ip已存在
             */
            if (peers.containsKey(address)) {
                tmpPeers.put(address, peers.get(address));
                continue;
            }

            RaftPeer raftPeer = new RaftPeer();
            raftPeer.ip = address;

            // first time meet the local server:
            /**
             * 本机  则设置term
             */
            if (ApplicationUtils.getLocalAddress().equals(address)) {
                raftPeer.term.set(localTerm.get());
            }
            /**
             * 新增ip
             */
            tmpPeers.put(address, raftPeer);
        }

        // replace raft peer set:
        /**
         * 替换原有peers
         */
        peers = tmpPeers;

        ready = true;
        Loggers.RAFT.info("raft peers changed: " + members);
    }

    @Override
    public String toString() {
        return "RaftPeerSet{" + "localTerm=" + localTerm + ", leader=" + leader + ", peers=" + peers + ", sites="
                + sites + '}';
    }
}
