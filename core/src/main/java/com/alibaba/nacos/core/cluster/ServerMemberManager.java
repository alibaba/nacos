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

import com.alibaba.nacos.core.notify.listener.SmartSubscribe;
import com.alibaba.nacos.core.cluster.task.ClusterConfSyncTask;
import com.alibaba.nacos.core.cluster.task.MemberDeadBroadcastTask;
import com.alibaba.nacos.core.cluster.task.MemberPingTask;
import com.alibaba.nacos.core.cluster.task.MemberPullTask;
import com.alibaba.nacos.core.cluster.task.MemberShutdownTask;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.core.utils.Constants;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.PropertyUtil;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Cluster node management in Nacos
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component(value = "serverMemberManager")
@SuppressWarnings("all")
public class ServerMemberManager implements SmartApplicationListener, DisposableBean, MemberManager {

    private final ServletContext servletContext;
    public String domainName;
    public String addressPort;
    public String addressUrl;
    public String envIdUrl;
    public String addressServerUrl;
    private Map<String, Member> serverList = new ConcurrentSkipListMap<>();
    private volatile boolean isInIpList = true;
    private volatile boolean isAddressServerHealth = true;
    private boolean isHealthCheck = true;
    private String contextPath = "";
    @Value("${server.port:8848}")
    private int port;
    @Value("${useAddressServer:false}")
    private boolean isUseAddressServer;
    private Member self;
    private String localAddress;

    private Set<Task> tasks = new HashSet<>();

    // The node here is always the node information of the UP state
    private Set<String> memberAddressInfos = new ConcurrentHashSet<>();


    public ServerMemberManager(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public void init() {
        Loggers.CORE.info("Nacos-related cluster resource initialization");
        this.port = ApplicationUtils.getProperty("server.port", Integer.class, 8848);
        this.localAddress = InetUtils.getSelfIp() + ":" + port;

        // init nacos core sys
        initAddressSys();

        // register NodeChangeEvent publisher to NotifyManager
        registerClusterEvent();

        ClusterConfSyncTask task = new ClusterConfSyncTask(this);
        task.init();
        GlobalExecutor.scheduleSyncJob(task, 5_000L);

        tasks.add(task);

        Loggers.CORE.info("The cluster resource is initialized");
    }

    private void registerClusterEvent() {
        // Register node change events
        NotifyCenter.registerPublisher(NodeChangeEvent::new, NodeChangeEvent.class);
        // Register the web container initialization completion event
        NotifyCenter.registerPublisher(ServerInitializedEvent::new, ServerInitializedEvent.class);
        // Register node isolation events
        NotifyCenter.registerPublisher(IsolationEvent::new, IsolationEvent.class);
        // Register node recover events
        NotifyCenter.registerPublisher(RecoverEvent::new, RecoverEvent.class);

        // Handles events related to node isolation and recovery
        NotifyCenter.registerSubscribe(new SmartSubscribe() {

            @Override
            public void onEvent(Event event) {
                if (event instanceof IsolationEvent) {
                    self.setState(NodeState.ISOLATION);
                    return;
                }
                if (event instanceof RecoverEvent) {
                    self.setState(NodeState.UP);
                    return;
                }
            }

            @Override
            public boolean canNotify(Event event) {
                boolean i = event instanceof IsolationEvent;
                boolean r = event instanceof RecoverEvent;
                return i || r;
            }
        });
    }

    @Override
    public void update(Member newMember) {
        String address = newMember.getAddress();

        if (!serverList.containsKey(address)) {
            memberJoin(new ArrayList<>(Arrays.asList(newMember)));
        }

        serverList.computeIfPresent(address, new BiFunction<String, Member, Member>() {
            @Override
            public Member apply(String s, Member member) {
                member.setFailAccessCnt(0);
                member.setState(NodeState.UP);
                MemberUtils.copy(newMember, member);
                return member;
            }
        });
    }

    @Override
    public int indexOf(String address) {
        int index = 1;
        for (Map.Entry<String, Member> entry : serverList.entrySet()) {
            if (Objects.equals(entry.getKey(), address)) {
                return index;
            }
            index++;
        }
        return index;
    }

    @Override
    public boolean hasMember(String address) {
        boolean result = serverList.containsKey(address);
        if (!result) {
            for (Map.Entry<String, Member> entry : serverList.entrySet()) {
                if (StringUtils.contains(entry.getKey(), address)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public Member getSelf() {
        if (Objects.isNull(self)) {
            self = serverList.get(localAddress);
        }
        return self;
    }

    @Override
    public Collection<Member> allMembers() {
        // We need to do a copy to avoid affecting the real data
        return new ArrayList<>(serverList.values());
    }

    @Override
    public void memberJoin(Collection<Member> members) {
        for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {
            final Member newMember = iterator.next();
            final String address = newMember.getAddress();
            if (serverList.containsKey(address)) {
                iterator.remove();
                continue;
            }
            NodeState state = newMember.getState();
            if (state == NodeState.DOWN || state == NodeState.SUSPICIOUS) {
                iterator.remove();
                continue;
            }

            // Ensure that the node is created only once
            serverList.computeIfAbsent(address, s -> {
                memberAddressInfos.add(address);
                return newMember;
            });
            serverList.computeIfPresent(address, new BiFunction<String, Member, Member>() {
                @Override
                public Member apply(String s, Member member) {
                    MemberUtils.copy(newMember, member);
                    return member;
                }
            });
        }

        if (members.isEmpty()) {
            return;
        }

        Loggers.CLUSTER.warn("have new node join : {}", members);
        NotifyCenter.publishEvent(NodeChangeEvent.builder()
                .join(true)
                .changeNodes(members)
                .allNodes(allMembers())
                .build());
    }

    @Override
    public void memberLeave(Collection<Member> members) {
        for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {
            Member member = iterator.next();
            final String address = member.getAddress();
            if (StringUtils.equals(address, localAddress)) {
                iterator.remove();
                continue;
            }
            memberAddressInfos.remove(address);
            serverList.remove(address);
        }

        if (members.isEmpty()) {
            return;
        }

        Loggers.CLUSTER.warn("have node leave : {}", members);
        NotifyCenter.publishEvent(NodeChangeEvent.builder()
                .join(false)
                .changeNodes(members)
                .allNodes(allMembers())
                .build());
    }

    @Override
    public void subscribe(MemberChangeListener listener) {
        NotifyCenter.registerSubscribe(listener);
    }

    @Override
    public void unSubscribe(MemberChangeListener listener) {
        NotifyCenter.deregisterSubscribe(listener);
    }

    public String getContextPath() {
        if (StringUtils.isBlank(contextPath)) {
            String contextPath = PropertyUtil.getProperty(Constants.WEB_CONTEXT_PATH);

            // If you can't find it, check it from Sping Environment

            if (StringUtils.isBlank(contextPath)) {
                contextPath = ApplicationUtils.getProperty(Constants.WEB_CONTEXT_PATH);
            }
            if (Constants.ROOT_WEB_CONTEXT_PATH.equals(contextPath)) {
                return StringUtils.EMPTY;
            } else {
                return contextPath;
            }
        }
        return contextPath;
    }

    @Override
    public boolean isFirstIp() {
        return 1 == indexOf(localAddress);
    }

    @Override
    public boolean isUnHealth(String address) {
        Member member = serverList.get(address);
        if (member == null) {
            return false;
        }
        return !NodeState.UP.equals(member.getState());
    }

    @PreDestroy
    @Override
    public void shutdown() {
        for (Task task : tasks) {
            task.shutdown();
        }
        getSelf().setState(NodeState.DOWN);
    }

    public boolean isSelf(Member member) {
        return Objects.equals(member.getAddress(), localAddress);
    }

    private void initAddressSys() {
        String envDomainName = System.getenv("address_server_domain");
        if (StringUtils.isBlank(envDomainName)) {
            domainName = System.getProperty("address.server.domain", "jmenv.tbsite.net");
        } else {
            domainName = envDomainName;
        }
        String envAddressPort = System.getenv("address_server_port");
        if (StringUtils.isBlank(envAddressPort)) {
            addressPort = System.getProperty("address.server.port", "8080");
        } else {
            addressPort = envAddressPort;
        }
        addressUrl = System.getProperty("address.server.url",
                servletContext.getContextPath() + "/" + "serverlist");
        addressServerUrl = "http://" + domainName + ":" + addressPort + addressUrl;
        envIdUrl = "http://" + domainName + ":" + addressPort + "/env";

        Loggers.CORE.info("ServerListService address-server port:" + addressPort);
        Loggers.CORE.info("ADDRESS_SERVER_URL:" + addressServerUrl);

        isHealthCheck = Boolean.parseBoolean(
                ApplicationUtils.getProperty("isHealthCheck", "true"));
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof WebServerInitializedEvent) {
            if (!ApplicationUtils.getStandaloneMode()) {

                Loggers.CLUSTER.info("execute cluster tasks");

                MemberPingTask pingTask = new MemberPingTask(this);
                MemberPullTask pullTask = new MemberPullTask(this);
                MemberDeadBroadcastTask broadcastTask = new MemberDeadBroadcastTask(this);

                pingTask.init();
                pullTask.init();
                broadcastTask.init();

                GlobalExecutor.schedulePingJob(pingTask, 5_000L);
                GlobalExecutor.schedulePullJob(pullTask, 5_000L);
                GlobalExecutor.scheduleBroadCastJob(broadcastTask, 10_000L);

                tasks.add(broadcastTask);
                tasks.add(pingTask);
                tasks.add(pullTask);

                NotifyCenter.publishEvent(new ServerInitializedEvent((WebServerInitializedEvent) event, servletContext));
            }
        }
        if (event instanceof ContextStartedEvent) {

            // For containers that have started, stop all messages from being published late

            Loggers.CORE.info("Terminates delayed publication of all messages");
            NotifyCenter.stopDeferPublish();
        }
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        boolean initializedEvent = WebServerInitializedEvent.class.isAssignableFrom(eventType);
        boolean contextStartedEvent = ContextStartedEvent.class.isAssignableFrom(eventType);
        return initializedEvent || contextStartedEvent;
    }

    @Override
    public void destroy() throws Exception {
        MemberShutdownTask shutdownTask = new MemberShutdownTask(this);
        GlobalExecutor.runWithoutThread(shutdownTask);
    }

    @VisibleForTesting
    public void updateMember(Member member) {
        serverList.put(member.getAddress(), member);
    }

    public Set<String> getMemberAddressInfos() {
        return memberAddressInfos;
    }

    public void setMemberAddressInfos(Set<String> memberAddressInfos) {
        this.memberAddressInfos = memberAddressInfos;
    }

    public Map<String, Member> getServerList() {
        return Collections.unmodifiableMap(serverList);
    }

    public boolean isUseAddressServer() {
        return isUseAddressServer;
    }

    public boolean isInIpList() {
        return isInIpList;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getAddressPort() {
        return addressPort;
    }

    public String getAddressUrl() {
        return addressUrl;
    }

    public String getEnvIdUrl() {
        return envIdUrl;
    }

    public boolean isHealthCheck() {
        return isHealthCheck;
    }

    public boolean isAddressServerHealth() {
        return isAddressServerHealth;
    }

    public void setAddressServerHealth(boolean addressServerHealth) {
        isAddressServerHealth = addressServerHealth;
    }

    public String getAddressServerUrl() {
        return addressServerUrl;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public boolean getUseAddressServer() {
        return isUseAddressServer;
    }

    public void setUseAddressServer(boolean useAddressServer) {
        isUseAddressServer = useAddressServer;
    }

    public int getPort() {
        return port;
    }
}
