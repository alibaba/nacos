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

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.core.cluster.task.ClusterConfSyncTask;
import com.alibaba.nacos.core.cluster.task.MemberDeadBroadcastTask;
import com.alibaba.nacos.core.cluster.task.MemberPingTask;
import com.alibaba.nacos.core.cluster.task.MemberPullTask;
import com.alibaba.nacos.core.cluster.task.MemberShutdownTask;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ConcurrentHashSet;
import com.alibaba.nacos.core.utils.Constants;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.PropertyUtil;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alibaba.nacos.core.utils.SystemUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.stereotype.Component;

/**
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
    private String localAddress;
    @Value("${useAddressServer:false}")
    private boolean isUseAddressServer;
    private Member self;

    // The node here is always the node information of the UP state
    private Set<String> memberAddressInfos = new ConcurrentHashSet<>();
    private CPProtocol cpProtocol;
    private APProtocol apProtocol;

    public ServerMemberManager(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @PostConstruct
    @Override
    public void init() {

        Loggers.CORE.info("Nacos-related cluster resource initialization");

        this.localAddress = InetUtils.getSelfIp() + ":" + port;

        // register NodeChangeEvent publisher to NotifyManager

        NotifyCenter.registerPublisher(NodeChangeEvent::new, NodeChangeEvent.class);

        // init nacos core sys

        initSys();

        ClusterConfSyncTask task = new ClusterConfSyncTask(this, servletContext);
        task.init();
        GlobalExecutor.scheduleSyncJob(task, 5_000L);

        // Consistency protocol module initialization

        initAPProtocol();
        initCPProtocol();

        Loggers.CORE.info("The cluster resource is initialized");
    }

    @Override
    public void update(Member newMember) {
        String address = newMember.address();

        if (!serverList.containsKey(address)) {
            memberJoin(new ArrayList<>(Arrays.asList(newMember)));
            return;
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
    public Member self() {
        if (Objects.isNull(self)) {
            self = serverList.get(localAddress);
        }
        return self;
    }

    @Override
    public Collection<Member> allMembers() {
        return serverList.values();
    }

    @Override
    public synchronized void memberJoin(Collection<Member> members) {
        for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {

            final Member newMember = iterator.next();
            final String address = newMember.address();

            if (serverList.containsKey(address)) {
                iterator.remove();
                continue;
            }

            NodeState state = newMember.state();

            if (state == NodeState.DOWN || state == NodeState.SUSPICIOUS) {
                iterator.remove();
                continue;
            }

            // Ensure that the node is created only once
            serverList.computeIfAbsent(address, s -> newMember);

            memberAddressInfos.add(address);

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

        NotifyCenter.publishEvent(NodeChangeEvent.class, NodeChangeEvent.builder()
                .kind("join")
                .changeNodes(members)
                .allNodes(allMembers())
                .build());

    }

    @Override
    public void memberLeave(Collection<Member> members) {

        for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {

            Member member = iterator.next();
            final String address = member.address();

            if (Objects.equals(address, localAddress)) {
                iterator.remove();
                continue;
            }

            memberAddressInfos.remove(address);

            serverList.computeIfPresent(address, new BiFunction<String, Member, Member>() {
                @Override
                public Member apply(String s, Member member) {
                    return null;
                }
            });
        }

        if (members.isEmpty()) {
            return;
        }

        Loggers.CLUSTER.warn("have node leave : {}", members);

        NotifyCenter.publishEvent(NodeChangeEvent.class, NodeChangeEvent.builder()
                .kind("leave")
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

    @Override
    public String getContextPath() {
        if (StringUtils.isBlank(contextPath)) {
            String contextPath = PropertyUtil.getProperty(Constants.WEB_CONTEXT_PATH);

            // If you can't find it, check it from Sping Environment

            if (StringUtils.isBlank(contextPath)) {
                contextPath = SpringUtils.getProperty(Constants.WEB_CONTEXT_PATH);
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
        return member.getState() == NodeState.UP;
    }

    @Override
    public void clean() {
    }

    @PreDestroy
    @Override
    public void shutdown() {
        Map<String, ConsistencyProtocol> protocolMap = SpringUtils.getBeansOfType(ConsistencyProtocol.class);
        for (ConsistencyProtocol protocol : protocolMap.values()) {
            protocol.shutdown();
        }
    }

    public boolean isSelf(Member member) {
        return Objects.equals(member.address(), localAddress);
    }

    private void initSys() {
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

        isHealthCheck = Boolean.parseBoolean(SpringUtils.getProperty("isHealthCheck", "true"));
    }

    private void initAPProtocol() {
        APProtocol protocol = SpringUtils.getBean(APProtocol.class);
        Config config = (Config) SpringUtils.getBean(protocol.configType());
        config.addLogProcessors(loadProcessorAndInjectProtocol(LogProcessor4AP.class, protocol));
        protocol.init((config));

        injectClusterInfo(protocol.protocolMetaData());

        // If the node information managed by the NodeManager changes, re-inject
        // the information into the protocol metadata information

        subscribe(event -> injectClusterInfo(protocol.protocolMetaData()));

        this.apProtocol = protocol;
    }

    private void initCPProtocol() {
        CPProtocol protocol = SpringUtils.getBean(CPProtocol.class);
        Config config = (Config) SpringUtils.getBean(protocol.configType());
        config.addLogProcessors(loadProcessorAndInjectProtocol(LogProcessor4CP.class, protocol));
        protocol.init((config));

        injectClusterInfo(protocol.protocolMetaData());

        subscribe(event -> injectClusterInfo(protocol.protocolMetaData()));

        this.cpProtocol = protocol;
    }

    private void injectClusterInfo(ProtocolMetaData metaData) {

        Map<String, Map<String, Object>> defaultMetaData = new HashMap<>();
        Map<String, Object> sub = new HashMap<>(8);

        defaultMetaData.put(ProtocolMetaData.GLOBAL, sub);

        // Globally unique information

        // /global/cluster => [ip:port, ip:port, ...]
        // /global/self => ip:port

        sub.put(ProtocolMetaData.CLUSTER_INFO, allMembers().stream().map(Member::address).collect(Collectors.toList()));
        sub.put(ProtocolMetaData.SELF, self().address());

        metaData.load(defaultMetaData);
    }

    @SuppressWarnings("all")
    private List<LogProcessor> loadProcessorAndInjectProtocol(Class cls, ConsistencyProtocol protocol) {
        Map<String, LogProcessor> beans = (Map<String, LogProcessor>) SpringUtils.getBeansOfType(cls);

        final List<LogProcessor> result = new ArrayList<>(beans.values());

        ServiceLoader<LogProcessor> loader = ServiceLoader.load(cls);
        for (LogProcessor t : loader) {
            result.add(t);
        }

        for (LogProcessor processor : result) {
            processor.injectProtocol(protocol);
        }

        return result;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof WebServerInitializedEvent) {
            if (!SystemUtils.STANDALONE_MODE) {
                MemberPingTask pingTask = new MemberPingTask(this);
                MemberPullTask pullTask = new MemberPullTask(this);
                MemberDeadBroadcastTask broadcastTask = new MemberDeadBroadcastTask(this);

                pingTask.init();
                pullTask.init();
                broadcastTask.init();

                GlobalExecutor.schedulePingJob(pingTask, 10_000L);
                GlobalExecutor.schedulePullJob(pullTask, 10_000L);
                GlobalExecutor.scheduleBroadCastJob(broadcastTask, 10_000L);
            }
        }
        if (event instanceof ContextStartedEvent) {

            // For containers that have started, stop all messages from being published late

            apProtocol.protocolMetaData().stopDeferPublish();
            cpProtocol.protocolMetaData().stopDeferPublish();
            NotifyCenter.stopDeferPublish();
        }
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        if (eventType.isAssignableFrom(WebServerInitializedEvent.class)) {
            return true;
        }
        return eventType.isAssignableFrom(ContextStartedEvent.class);
    }

    @Override
    public void destroy() throws Exception {
        MemberShutdownTask shutdownTask = new MemberShutdownTask(this);
        GlobalExecutor.runWithoutThread(shutdownTask);
    }

    public Set<String> getMemberAddressInfos() {
        return memberAddressInfos;
    }

    public void setMemberAddressInfos(Set<String> memberAddressInfos) {
        this.memberAddressInfos = memberAddressInfos;
    }

    public Map<String, Member> getServerList() {
        return serverList;
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
