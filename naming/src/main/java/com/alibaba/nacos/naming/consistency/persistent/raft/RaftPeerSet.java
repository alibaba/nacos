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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberChangeEvent;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.apache.commons.collections.SortedBag;
import org.apache.commons.collections.bag.TreeBag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.HttpURLConnection;
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
 * @author nacos
 */
@Component
@DependsOn("ProtocolManager")
public class RaftPeerSet implements MemberChangeListener {

    private final ServerMemberManager memberManager;

    private AtomicLong localTerm = new AtomicLong(0L);

    private RaftPeer leader = null;

    private volatile Map<String, RaftPeer> peers = new HashMap<>(8);

    private Set<String> sites = new HashSet<>();

    private volatile boolean ready = false;

    public RaftPeerSet(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }

    @PostConstruct
    public void init() {
        NotifyCenter.registerSubscribe(this);
        changePeers(memberManager.allMembers());
    }

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

    public void remove(List<String> servers) {
        for (String server : servers) {
            peers.remove(server);
        }
    }

    public RaftPeer update(RaftPeer peer) {
        peers.put(peer.ip, peer);
        return peer;
    }

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

    public Set<String> allServersWithoutMySelf() {
        Set<String> servers = new HashSet<String>(peers.keySet());

        // exclude myself
        servers.remove(local().ip);

        return servers;
    }

    public Collection<RaftPeer> allPeers() {
        return peers.values();
    }

    public int size() {
        return peers.size();
    }

    public RaftPeer decideLeader(RaftPeer candidate) {
        peers.put(candidate.ip, candidate);

        SortedBag ips = new TreeBag();
        int maxApproveCount = 0;
        String maxApprovePeer = null;
        for (RaftPeer peer : peers.values()) {
            if (StringUtils.isEmpty(peer.voteFor)) {
                continue;
            }

            ips.add(peer.voteFor);
            if (ips.getCount(peer.voteFor) > maxApproveCount) {
                maxApproveCount = ips.getCount(peer.voteFor);
                maxApprovePeer = peer.voteFor;
            }
        }

        if (maxApproveCount >= majorityCount()) {
            RaftPeer peer = peers.get(maxApprovePeer);
            peer.state = RaftPeer.State.LEADER;

            if (!Objects.equals(leader, peer)) {
                leader = peer;
                ApplicationUtils.publishEvent(new LeaderElectFinishedEvent(this, leader, local()));
                Loggers.RAFT.info("{} has become the LEADER", leader.ip);
            }
        }

        return leader;
    }

    public RaftPeer makeLeader(RaftPeer candidate) {
        if (!Objects.equals(leader, candidate)) {
            leader = candidate;
            ApplicationUtils.publishEvent(new MakeLeaderEvent(this, leader, local()));
            Loggers.RAFT.info("{} has become the LEADER, local: {}, leader: {}",
                leader.ip, JacksonUtils.toJson(local()), JacksonUtils.toJson(leader));
        }

        for (final RaftPeer peer : peers.values()) {
            Map<String, String> params = new HashMap<>(1);
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

                            update(JacksonUtils.toObj(response.getResponseBody(), RaftPeer.class));

                            return 0;
                        }
                    });
                } catch (Exception e) {
                    peer.state = RaftPeer.State.FOLLOWER;
                    Loggers.RAFT.error("[NACOS-RAFT] error while getting peer from peer: {}", peer.ip);
                }
            }
        }

        return update(candidate);
    }

    public RaftPeer local() {
        RaftPeer peer = peers.get(ApplicationUtils.getLocalAddress());
        if (peer == null && ApplicationUtils.getStandaloneMode()) {
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

    public int majorityCount() {
        return peers.size() / 2 + 1;
    }

    public void reset() {

        leader = null;

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
    public void onEvent(MemberChangeEvent event) {
        changePeers(event.getMembers());
    }

    private void changePeers(Collection<Member> members) {
        Map<String, RaftPeer> tmpPeers = new HashMap<>(members.size());

        for (Member member : members) {


            final String address = member.getAddress();
            if (peers.containsKey(address)) {
                tmpPeers.put(address, peers.get(address));
                continue;
            }

            RaftPeer raftPeer = new RaftPeer();
            raftPeer.ip = address;

            // first time meet the local server:
            if (ApplicationUtils.getLocalAddress().equals(address)) {
                raftPeer.term.set(localTerm.get());
            }

            tmpPeers.put(address, raftPeer);
        }

        // replace raft peer set:
        peers = tmpPeers;

        ready = true;
        Loggers.RAFT.info("raft peers changed: " + members);
    }

    @Override
    public String toString() {
        return "RaftPeerSet{" + "localTerm=" + localTerm + ", leader=" + leader
                + ", peers=" + peers + ", sites=" + sites + '}';
    }
}
