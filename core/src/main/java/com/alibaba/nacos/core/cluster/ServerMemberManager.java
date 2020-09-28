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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Cluster node management in Nacos.
 *
 * <p>{@link ServerMemberManager#init()} Cluster node manager initialization {@link ServerMemberManager#shutdown()} The
 * cluster node manager is down {@link ServerMemberManager#getSelf()} Gets local node information {@link
 * ServerMemberManager#getServerList()} Gets the cluster node dictionary {@link ServerMemberManager#getMemberAddressInfos()}
 * Gets the address information of the healthy member node {@link ServerMemberManager#allMembers()} Gets a list of
 * member information objects {@link ServerMemberManager#allMembersWithoutSelf()} Gets a list of cluster member nodes
 * with the exception of this node {@link ServerMemberManager#hasMember(String)} Is there a node {@link
 * ServerMemberManager#memberChange(Collection)} The final node list changes the method, making the full size more
 * {@link ServerMemberManager#memberJoin(Collection)} Node join, can automatically trigger {@link
 * ServerMemberManager#memberLeave(Collection)} When the node leaves, only the interface call can be manually triggered
 * {@link ServerMemberManager#update(Member)} Update the target node information {@link
 * ServerMemberManager#isUnHealth(String)} Whether the target node is healthy {@link
 * ServerMemberManager#initAndStartLookup()} Initializes the addressing mode
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component(value = "serverMemberManager")
public class ServerMemberManager implements ApplicationListener<WebServerInitializedEvent> {
    
    private final NacosAsyncRestTemplate asyncRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(Loggers.CORE);
    
    /**
     * Cluster node list.
     */
    private volatile ConcurrentSkipListMap<String, Member> serverList;
    
    /**
     * Is this node in the cluster list.
     */
    private volatile boolean isInIpList = true;
    
    /**
     * port.
     */
    private int port;
    
    /**
     * Address information for the local node.
     */
    private String localAddress;
    
    /**
     * Addressing pattern instances.
     */
    private MemberLookup lookup;
    
    /**
     * self member obj.
     */
    private volatile Member self;
    
    /**
     * here is always the node information of the "UP" state.
     */
    private volatile Set<String> memberAddressInfos = new ConcurrentHashSet<>();
    
    /**
     * Broadcast this node element information task.
     */
    private final MemberInfoReportTask infoReportTask = new MemberInfoReportTask();
    
    public ServerMemberManager(ServletContext servletContext) throws Exception {
        this.serverList = new ConcurrentSkipListMap<>();
        ApplicationUtils.setContextPath(servletContext.getContextPath());
        MemberUtils.setManager(this);
        
        init();
    }
    
    protected void init() throws NacosException {
        Loggers.CORE.info("Nacos-related cluster resource initialization");
        this.port = ApplicationUtils.getProperty("server.port", Integer.class, 8848);
        this.localAddress = InetUtils.getSelfIP() + ":" + port;
        this.self = MemberUtils.singleParse(this.localAddress);
        this.self.setExtendVal(MemberMetaDataConstants.VERSION, VersionUtils.version);
        serverList.put(self.getAddress(), self);
        
        // register NodeChangeEvent publisher to NotifyManager
        registerClusterEvent();
        
        // Initializes the lookup mode
        initAndStartLookup();
        
        if (serverList.isEmpty()) {
            throw new NacosException(NacosException.SERVER_ERROR, "cannot get serverlist, so exit.");
        }
        
        Loggers.CORE.info("The cluster resource is initialized");
    }
    
    private void initAndStartLookup() throws NacosException {
        this.lookup = LookupFactory.createLookUp(this);
        this.lookup.start();
    }
    
    public void switchLookup(String name) throws NacosException {
        this.lookup = LookupFactory.switchLookup(name, this);
        this.lookup.start();
    }
    
    private void registerClusterEvent() {
        // Register node change events
        NotifyCenter.registerToPublisher(MembersChangeEvent.class,
                ApplicationUtils.getProperty("nacos.member-change-event.queue.size", Integer.class, 128));
        
        // The address information of this node needs to be dynamically modified
        // when registering the IP change of this node
        NotifyCenter.registerSubscriber(new Subscriber<InetUtils.IPChangeEvent>() {
            @Override
            public void onEvent(InetUtils.IPChangeEvent event) {
                String newAddress = event.getNewIP() + ":" + port;
                ServerMemberManager.this.localAddress = newAddress;
                ApplicationUtils.setLocalAddress(localAddress);
    
                Member self = ServerMemberManager.this.self;
                self.setIp(event.getNewIP());
    
                String oldAddress = event.getOldIP() + ":" + port;
                ServerMemberManager.this.serverList.remove(oldAddress);
                ServerMemberManager.this.serverList.put(newAddress, self);
                
                ServerMemberManager.this.memberAddressInfos.remove(oldAddress);
                ServerMemberManager.this.memberAddressInfos.add(newAddress);
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return InetUtils.IPChangeEvent.class;
            }
        });
    }
    
    /**
     * member information update.
     *
     * @param newMember {@link Member}
     * @return update is success
     */
    public boolean update(Member newMember) {
        Loggers.CLUSTER.debug("member information update : {}", newMember);
        
        String address = newMember.getAddress();
        if (!serverList.containsKey(address)) {
            return false;
        }
        
        serverList.computeIfPresent(address, (s, member) -> {
            if (NodeState.DOWN.equals(newMember.getState())) {
                memberAddressInfos.remove(newMember.getAddress());
            }
            if (!MemberUtils.fullEquals(newMember, member)) {
                newMember.setExtendVal(MemberMetaDataConstants.LAST_REFRESH_TIME, System.currentTimeMillis());
                MemberUtils.copy(newMember, member);
                // member data changes and all listeners need to be notified
                NotifyCenter.publishEvent(MembersChangeEvent.builder().members(allMembers()).build());
            }
            return member;
        });
        
        return true;
    }
    
    /**
     * Whether the node exists within the cluster.
     *
     * @param address ip:port
     * @return is exist
     */
    public boolean hasMember(String address) {
        boolean result = serverList.containsKey(address);
        if (!result) {
            // If only IP information is passed in, a fuzzy match is required
            for (Map.Entry<String, Member> entry : serverList.entrySet()) {
                if (StringUtils.contains(entry.getKey(), address)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }
    
    public Member getSelf() {
        return this.self;
    }
    
    public Member find(String address) {
        return serverList.get(address);
    }
    
    /**
     * return this cluster all members.
     *
     * @return {@link Collection} all member
     */
    public Collection<Member> allMembers() {
        // We need to do a copy to avoid affecting the real data
        HashSet<Member> set = new HashSet<>(serverList.values());
        set.add(self);
        return set;
    }
    
    /**
     * return this cluster all members without self.
     *
     * @return {@link Collection} all member without self
     */
    public List<Member> allMembersWithoutSelf() {
        List<Member> members = new ArrayList<>(serverList.values());
        members.remove(self);
        return members;
    }
    
    synchronized boolean memberChange(Collection<Member> members) {
        
        if (members == null || members.isEmpty()) {
            return false;
        }
        
        boolean isContainSelfIp = members.stream()
                .anyMatch(ipPortTmp -> Objects.equals(localAddress, ipPortTmp.getAddress()));
        
        if (isContainSelfIp) {
            isInIpList = true;
        } else {
            isInIpList = false;
            members.add(this.self);
            Loggers.CLUSTER.warn("[serverlist] self ip {} not in serverlist {}", self, members);
        }
        
        // If the number of old and new clusters is different, the cluster information
        // must have changed; if the number of clusters is the same, then compare whether
        // there is a difference; if there is a difference, then the cluster node changes
        // are involved and all recipients need to be notified of the node change event
        
        boolean hasChange = members.size() != serverList.size();
        ConcurrentSkipListMap<String, Member> tmpMap = new ConcurrentSkipListMap<>();
        Set<String> tmpAddressInfo = new ConcurrentHashSet<>();
        for (Member member : members) {
            final String address = member.getAddress();
            
            if (!serverList.containsKey(address)) {
                hasChange = true;
            }
            
            // Ensure that the node is created only once
            tmpMap.put(address, member);
            tmpAddressInfo.add(address);
        }
        
        serverList = tmpMap;
        memberAddressInfos = tmpAddressInfo;
        
        Collection<Member> finalMembers = allMembers();
        
        Loggers.CLUSTER.warn("[serverlist] updated to : {}", finalMembers);
        
        // Persist the current cluster node information to cluster.conf
        // <important> need to put the event publication into a synchronized block to ensure
        // that the event publication is sequential
        if (hasChange) {
            MemberUtils.syncToFile(finalMembers);
            Set<Member> healthMembers = MemberUtils.selectTargetMembers(members, member -> {
                return !NodeState.DOWN.equals(member.getState());
            });
            Event event = MembersChangeEvent.builder().members(finalMembers).build();
            NotifyCenter.publishEvent(event);
        }
        
        return hasChange;
    }
    
    /**
     * members join this cluster.
     *
     * @param members {@link Collection} new members
     * @return is success
     */
    public synchronized boolean memberJoin(Collection<Member> members) {
        Set<Member> set = new HashSet<>(members);
        set.addAll(allMembers());
        return memberChange(set);
    }
    
    /**
     * members leave this cluster.
     *
     * @param members {@link Collection} wait leave members
     * @return is success
     */
    public synchronized boolean memberLeave(Collection<Member> members) {
        Set<Member> set = new HashSet<>(allMembers());
        set.removeAll(members);
        return memberChange(set);
    }
    
    /**
     * this member {@link Member#getState()} is health.
     *
     * @param address ip:port
     * @return is health
     */
    public boolean isUnHealth(String address) {
        Member member = serverList.get(address);
        if (member == null) {
            return false;
        }
        return !NodeState.UP.equals(member.getState());
    }
    
    public boolean isFirstIp() {
        return Objects.equals(serverList.firstKey(), this.localAddress);
    }
    
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        getSelf().setState(NodeState.UP);
        if (!ApplicationUtils.getStandaloneMode()) {
            GlobalExecutor.scheduleByCommon(this.infoReportTask, 5_000L);
        }
        ApplicationUtils.setPort(event.getWebServer().getPort());
        ApplicationUtils.setLocalAddress(this.localAddress);
        Loggers.CLUSTER.info("This node is ready to provide external services");
    }
    
    /**
     * ServerMemberManager shutdown.
     *
     * @throws NacosException NacosException
     */
    @PreDestroy
    public void shutdown() throws NacosException {
        serverList.clear();
        memberAddressInfos.clear();
        infoReportTask.shutdown();
        LookupFactory.destroy();
    }
    
    public Set<String> getMemberAddressInfos() {
        return memberAddressInfos;
    }
    
    @JustForTest
    public void updateMember(Member member) {
        serverList.put(member.getAddress(), member);
    }
    
    @JustForTest
    public void setMemberAddressInfos(Set<String> memberAddressInfos) {
        this.memberAddressInfos = memberAddressInfos;
    }
    
    @JustForTest
    public MemberInfoReportTask getInfoReportTask() {
        return infoReportTask;
    }
    
    public Map<String, Member> getServerList() {
        return Collections.unmodifiableMap(serverList);
    }
    
    public boolean isInIpList() {
        return isInIpList;
    }
    
    // Synchronize the metadata information of a node
    // A health check of the target node is also attached
    
    class MemberInfoReportTask extends Task {
        
        private final GenericType<RestResult<String>> reference = new GenericType<RestResult<String>>() {
        };
        
        private int cursor = 0;
        
        @Override
        protected void executeBody() {
            List<Member> members = ServerMemberManager.this.allMembersWithoutSelf();
            
            if (members.isEmpty()) {
                return;
            }
            
            this.cursor = (this.cursor + 1) % members.size();
            Member target = members.get(cursor);
            
            Loggers.CLUSTER.debug("report the metadata to the node : {}", target.getAddress());
            
            final String url = HttpUtils
                    .buildUrl(false, target.getAddress(), ApplicationUtils.getContextPath(), Commons.NACOS_CORE_CONTEXT,
                            "/cluster/report");
            
            try {
                asyncRestTemplate
                        .post(url, Header.newInstance().addParam(Constants.NACOS_SERVER_HEADER, VersionUtils.version),
                                Query.EMPTY, getSelf(), reference.getType(), new Callback<String>() {
                                    @Override
                                    public void onReceive(RestResult<String> result) {
                                        if (result.getCode() == HttpStatus.NOT_IMPLEMENTED.value()
                                                || result.getCode() == HttpStatus.NOT_FOUND.value()) {
                                            Loggers.CLUSTER
                                                    .warn("{} version is too low, it is recommended to upgrade the version : {}",
                                                            target, VersionUtils.version);
                                            return;
                                        }
                                        if (result.ok()) {
                                            MemberUtils.onSuccess(target);
                                        } else {
                                            Loggers.CLUSTER
                                                    .warn("failed to report new info to target node : {}, result : {}",
                                                            target.getAddress(), result);
                                            MemberUtils.onFail(target);
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(Throwable throwable) {
                                        Loggers.CLUSTER
                                                .error("failed to report new info to target node : {}, error : {}",
                                                        target.getAddress(),
                                                        ExceptionUtil.getAllExceptionMsg(throwable));
                                        MemberUtils.onFail(target, throwable);
                                    }
            
                                    @Override
                                    public void onCancel() {
                
                                    }
                                });
            } catch (Throwable ex) {
                Loggers.CLUSTER.error("failed to report new info to target node : {}, error : {}", target.getAddress(),
                        ExceptionUtil.getAllExceptionMsg(ex));
            }
        }
        
        @Override
        protected void after() {
            GlobalExecutor.scheduleByCommon(this, 2_000L);
        }
    }
    
}
