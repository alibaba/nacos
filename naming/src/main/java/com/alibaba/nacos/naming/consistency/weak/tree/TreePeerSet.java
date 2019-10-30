/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.servers.ServerChangeListener;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author satjd
 */
@Component
@DependsOn("serverListManager")
public class TreePeerSet implements ServerChangeListener, ApplicationContextAware {
    @Autowired
    private ServerListManager serverListManager;

    @Autowired
    private ProtocolConfig protocolConfig;

    private Map<String, TreePeer> oldPeers = new HashMap<>();

    private Map<String, TreePeer> peers = new HashMap<>();

    private List<TreePeer> sortedPeerList;

    private List<TreePeer> oldSortedPeerList;

    private Set<TreePeer> currentChild = new HashSet<>();

    private Set<TreePeer> prevChild = new HashSet<>();

    private ReentrantReadWriteLock childUpdateRWLock = new ReentrantReadWriteLock();

    private ApplicationContext applicationContext;

    private ScheduledExecutorService topologyUpdaterThreadPool = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);

            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.tree.topologyUpdater");

            return t;
        }
    });

    @PostConstruct
    private void init() {
        serverListManager.listen(this);
    }

    public Set<TreePeer> getCurrentChild(TreePeer root) {
        Set<TreePeer> ret = new HashSet<>();
        try {
            childUpdateRWLock.readLock().lock();
            // Make peer list and sort:
            List<TreePeer> peerList = new LinkedList<>(sortedPeerList);

            // find root position in peer list
            int targetIdx = peerList.indexOf(root);

            // transform peer list, put root at the head of peer list.
            if (targetIdx >= 0) {
                transformPeer(peerList,targetIdx);
            } else {
                Loggers.TREE.warn("Server {} not found in cluster conf.",root.key);
            }

            int branchCnt = protocolConfig.getTreeParamN();
            int myIdx = peerList.indexOf(getLocal());
            for (int i = 1; i <= branchCnt; i++) {
                int nextIdx = (myIdx * branchCnt + i);
                if (nextIdx >= peerList.size()) {
                    // at leaf node
                    break;
                }
                ret.add(peerList.get(nextIdx));
            }
        } finally {
            childUpdateRWLock.readLock().unlock();
        }
        return ret;
    }

    @Override
    public void onChangeServerList(List<Server> servers) {
        topologyUpdaterThreadPool.schedule(new TopologyUpdateTask(servers),10L, TimeUnit.MILLISECONDS);
        Loggers.TREE.info("Server list change.size={}",servers.size());
    }

    @Override
    public void onChangeHealthyServerList(List<Server> healthyServer) {

        Loggers.TREE.info("Healthy server list change.size={}",healthyServer.size());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public TreePeer getLocal() {
        TreePeer peer = peers.get(NetUtils.localServer());
        if (peer == null) {
            if (SystemUtils.STANDALONE_MODE) {
                TreePeer localPeer = new TreePeer();
                localPeer.key = NetUtils.localServer();
                localPeer.ip = NetUtils.getLocalAddress();
                localPeer.port = RunningConfig.getServerPort();
                peers.put(localPeer.ip, localPeer);
                return localPeer;
            }

            throw new IllegalStateException("unable to find local peer: " + NetUtils.localServer() + ", all peers: "
                + Arrays.toString(peers.keySet().toArray()));
        }

        return peer;
    }

    private void transformPeer(List<TreePeer> peerList, int peerIdx) {
        Collections.rotate(peerList,0 - peerIdx);
    }

    private class TopologyUpdateTask implements Runnable {

        private List<Server> newServerList;

        public TopologyUpdateTask(List<Server> newServerList) {
            this.newServerList = newServerList;
        }

        @Override
        public void run() {
            childUpdateRWLock.writeLock().lock();
            int treeN = protocolConfig.getTreeParamN();
            try {
                // todo prevChild
                oldPeers = peers;
                oldSortedPeerList = sortedPeerList;

                // generate new topology
                Map<String,TreePeer> tmpPeers = new HashMap<>(128);

                for (Server s : newServerList) {
                    if (peers.containsKey(s.getKey())) {
                        tmpPeers.put(s.getKey(),peers.get(s.getKey()));
                        continue;
                    }

                    TreePeer treePeer = new TreePeer();
                    treePeer.ip = s.getIp();
                    treePeer.port = s.getServePort();
                    treePeer.key = s.getKey();


                    tmpPeers.put(s.getKey(), treePeer);
                }

                // replace tree peer set:
                peers = tmpPeers;
                sortedPeerList = new LinkedList<>(peers.values());
                Collections.sort(sortedPeerList);

            } catch (Exception e) {
                Loggers.TREE.error(e.toString());
            } finally {
                childUpdateRWLock.writeLock().unlock();
            }
        }
    }
}
