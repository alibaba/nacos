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
package com.alibaba.nacos.naming.raft;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.apache.commons.collections.SortedBag;
import org.apache.commons.collections.bag.TreeBag;
import org.apache.commons.lang3.StringUtils;

import java.net.HttpURLConnection;
import java.util.*;

/**
 * @author nacos
 */
public class PeerSet {

    private RaftPeer leader = null;

    private static Map<String, RaftPeer> peers = new HashMap<String, RaftPeer>();

    private static Set<String> sites = new HashSet<>();

    public PeerSet() {
    }

    public RaftPeer getLeader() {
        if (UtilsAndCommons.STANDALONE_MODE) {
            return local();
        }
        return leader;
    }

    public Set<String> allSites() {
        return sites;
    }

    public void add(List<String> servers) {
        for (String server : servers) {
            RaftPeer peer = new RaftPeer();
            peer.ip = server;

            peers.put(server, peer);
        }

        if (UtilsAndCommons.STANDALONE_MODE) {
            RaftPeer local = local();
            local.state = RaftPeer.State.LEADER;
            local.voteFor = NetUtils.localIP();

        }
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
        if (UtilsAndCommons.STANDALONE_MODE) {
            return true;
        }

        Loggers.RAFT.info("IS LEADER", "leader: " + leader.ip + ", ip: " + ip);

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
        for (RaftPeer peer : peers.values()) {
            if (StringUtils.isEmpty(peer.voteFor)) {
                continue;
            }

            ips.add(peer.voteFor);
        }

        String first = (String) ips.last();
        if (ips.getCount(first) >= majorityCount()) {
            RaftPeer peer = peers.get(first);
            peer.state = RaftPeer.State.LEADER;

            if (!Objects.equals(leader, peer)) {
                leader = peer;
                Loggers.RAFT.info(leader.ip + " has become the LEADER");
            }
        }

        return leader;
    }

    public RaftPeer makeLeader(RaftPeer candidate) {
        if (!Objects.equals(leader, candidate)) {
            leader = candidate;
            Loggers.RAFT.info(leader.ip + " has become the LEADER" + ",local :" + JSON.toJSONString(local()) + ", leader: " + JSON.toJSONString(leader));
        }

        for (final RaftPeer peer : peers.values()) {
            Map<String, String> params = new HashMap<String, String>(1);
            if (!Objects.equals(peer, candidate) && peer.state == RaftPeer.State.LEADER) {
                try {
                    String url = RaftCore.buildURL(peer.ip, RaftCore.API_GET_PEER);
                    HttpClient.asyncHttpPost(url, null, params, new AsyncCompletionHandler<Integer>() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                Loggers.RAFT.error("VIPSRV-RAFT", "get peer failed: " + response.getResponseBody() + ", peer: " + peer.ip);
                                peer.state = RaftPeer.State.FOLLOWER;
                                return 1;
                            }

                            update(JSON.parseObject(response.getResponseBody(), RaftPeer.class));

                            return 0;
                        }
                    });
                } catch (Exception e) {
                    peer.state = RaftPeer.State.FOLLOWER;
                    Loggers.RAFT.error("VIPSRV-RAFT", "error while getting peer from peer: " + peer.ip);
                }
            }
        }

        return update(candidate);
    }

    public RaftPeer local() {
        RaftPeer peer = peers.get(NetUtils.localIP());
        if (peer == null) {
            throw new IllegalStateException("unable to find local peer: " + NetUtils.localIP() + ", all peers: "
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
        RaftPeer local = local();

        if (term < local.term.get()) {
            return;
        }

        local.term.set(term);
    }

    public long getTerm() {
        return local().term.get();
    }

    public boolean contains(RaftPeer remote) {
        return peers.containsKey(remote.ip);
    }
}
