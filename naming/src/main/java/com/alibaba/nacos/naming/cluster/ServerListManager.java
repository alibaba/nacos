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

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Message;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.ServerStatusSynchronizer;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.Synchronizer;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The manager to globally refresh and operate server list.
 *
 * @author nkorange
 * @since 1.0.0
 * @deprecated 1.3.0 This object will be deleted sometime after version 1.3.0
 */
@Component("serverListManager")
public class ServerListManager extends MemberChangeListener {
    
    private static final String LOCALHOST_SITE = UtilsAndCommons.UNKNOWN_SITE;
    
    private final SwitchDomain switchDomain;
    
    private final ServerMemberManager memberManager;
    
    private final Synchronizer synchronizer = new ServerStatusSynchronizer();
    
    private volatile List<Member> servers;
    
    public ServerListManager(final SwitchDomain switchDomain, final ServerMemberManager memberManager) {
        this.switchDomain = switchDomain;
        this.memberManager = memberManager;
        NotifyCenter.registerSubscriber(this);
        this.servers = new ArrayList<>(memberManager.allMembers());
    }
    
    @PostConstruct
    public void init() {
        GlobalExecutor.registerServerStatusReporter(new ServerStatusReporter(), 2000);
        GlobalExecutor.registerServerInfoUpdater(new ServerInfoUpdater());
    }
    
    /**
     * Judge whether contain server in cluster.
     *
     * @param serverAddress server address
     * @return true if contain, otherwise false
     */
    public boolean contains(String serverAddress) {
        for (Member server : getServers()) {
            if (Objects.equals(serverAddress, server.getAddress())) {
                return true;
            }
        }
        return false;
    }
    
    public List<Member> getServers() {
        return servers;
    }
    
    @Override
    public void onEvent(MembersChangeEvent event) {
        this.servers = new ArrayList<>(event.getMembers());
    }
    
    /**
     * Compatible with older version logic, In version 1.2.1 and before
     *
     * @param configInfo site:ip:lastReportTime:weight
     */
    public synchronized void onReceiveServerStatus(String configInfo) {
        
        Loggers.SRV_LOG.info("receive config info: {}", configInfo);
        
        String[] configs = configInfo.split("\r\n");
        if (configs.length == 0) {
            return;
        }
        
        for (String config : configs) {
            // site:ip:lastReportTime:weight
            String[] params = config.split("#");
            if (params.length <= 3) {
                Loggers.SRV_LOG.warn("received malformed distro map data: {}", config);
                continue;
            }
    
            String[] info = InternetAddressUtil.splitIPPortStr(params[1]);
            Member server = Optional.ofNullable(memberManager.find(params[1]))
                    .orElse(Member.builder().ip(info[0]).state(NodeState.UP)
                            .port(Integer.parseInt(info[1])).build());
            
            // This metadata information exists from 1.3.0 onwards "version"
            if (server.getExtendVal(MemberMetaDataConstants.VERSION) == null) {
                // copy to trigger member change event
                server = server.copy();
                // received heartbeat from server of version before 1.3.0
                if (!server.getState().equals(NodeState.UP)) {
                    Loggers.SRV_LOG.info("member {} state changed to UP", server);
                }
                server.setState(NodeState.UP);
            }
            server.setExtendVal(MemberMetaDataConstants.SITE_KEY, params[0]);
            server.setExtendVal(MemberMetaDataConstants.WEIGHT, params.length == 4 ? Integer.parseInt(params[3]) : 1);
            memberManager.update(server);
            
            if (!contains(server.getAddress())) {
                throw new IllegalArgumentException("server: " + server.getAddress() + " is not in serverlist");
            }
        }
    }
    
    private class ServerInfoUpdater implements Runnable {
        
        private int cursor = 0;
        
        @Override
        public void run() {
            List<Member> members = servers;
            if (members.isEmpty()) {
                return;
            }
            
            this.cursor = (this.cursor + 1) % members.size();
            Member target = members.get(cursor);
            if (Objects.equals(target.getAddress(), EnvUtil.getLocalAddress())) {
                return;
            }
            
            // This metadata information exists from 1.3.0 onwards "version"
            if (target.getExtendVal(MemberMetaDataConstants.VERSION) != null) {
                return;
            }
            
            final String path =
                    UtilsAndCommons.NACOS_NAMING_OPERATOR_CONTEXT + UtilsAndCommons.NACOS_NAMING_CLUSTER_CONTEXT
                            + "/state";
            final Map<String, String> params = new HashMap(2);
            final String server = target.getAddress();
            
            try {
                String content = NamingProxy.reqCommon(path, params, server, false);
                if (!StringUtils.EMPTY.equals(content)) {
                    RaftPeer raftPeer = JacksonUtils.toObj(content, RaftPeer.class);
                    if (null != raftPeer) {
                        String json = JacksonUtils.toJson(raftPeer);
                        Map map = JacksonUtils.toObj(json, HashMap.class);
                        target.setExtendVal("naming", map);
                        memberManager.update(target);
                    }
                }
            } catch (Exception ignore) {
                //
            }
        }
    }
    
    private class ServerStatusReporter implements Runnable {
        
        @Override
        public void run() {
            try {
                
                if (EnvUtil.getPort() <= 0) {
                    return;
                }
                
                int weight = EnvUtil.getAvailableProcessors(0.5);
                if (weight <= 0) {
                    weight = 1;
                }
                
                long curTime = System.currentTimeMillis();
                String status = LOCALHOST_SITE + "#" + EnvUtil.getLocalAddress() + "#" + curTime + "#" + weight
                        + "\r\n";
                
                List<Member> allServers = getServers();
                
                if (!contains(EnvUtil.getLocalAddress())) {
                    Loggers.SRV_LOG.error("local ip is not in serverlist, ip: {}, serverlist: {}",
                            EnvUtil.getLocalAddress(), allServers);
                    return;
                }
                
                if (allServers.size() > 0 && !EnvUtil.getLocalAddress()
                        .contains(InternetAddressUtil.localHostIP())) {
                    for (Member server : allServers) {
                        if (Objects.equals(server.getAddress(), EnvUtil.getLocalAddress())) {
                            continue;
                        }
                        
                        // This metadata information exists from 1.3.0 onwards "version"
                        if (server.getExtendVal(MemberMetaDataConstants.VERSION) != null) {
                            Loggers.SRV_LOG
                                    .debug("[SERVER-STATUS] target {} has extend val {} = {}, use new api report status",
                                            server.getAddress(), MemberMetaDataConstants.VERSION,
                                            server.getExtendVal(MemberMetaDataConstants.VERSION));
                            continue;
                        }
                        
                        Message msg = new Message();
                        msg.setData(status);
                        
                        synchronizer.send(server.getAddress(), msg);
                    }
                }
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[SERVER-STATUS] Exception while sending server status", e);
            } finally {
                GlobalExecutor
                        .registerServerStatusReporter(this, switchDomain.getServerStatusSynchronizationPeriodMillis());
            }
            
        }
    }
    
}
