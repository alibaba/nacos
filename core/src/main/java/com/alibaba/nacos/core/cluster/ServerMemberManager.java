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
import com.alibaba.nacos.core.cluster.task.ClearInvalidNodeTask;
import com.alibaba.nacos.core.cluster.task.NodeStateReportTask;
import com.alibaba.nacos.core.cluster.task.SyncNodeTask;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.Constants;
import com.alibaba.nacos.core.utils.InetUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.PropertyUtil;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component(value = "serverMemberManager")
@SuppressWarnings("all")
public class ServerMemberManager implements ApplicationListener<WebServerInitializedEvent>, MemberManager {

    private final MemberTaskManager taskManager = new MemberTaskManager(this);

    private Map<String, Member> serverListHealth = new ConcurrentSkipListMap<>();

    private Map<String, Long> lastRefreshTimeRecord = new ConcurrentHashMap<>();

    private Set<Member> serverListUnHealth = Collections.synchronizedSet(new HashSet<>());

    private volatile boolean isInIpList = true;

    private volatile boolean isAddressServerHealth = true;

    public String domainName;

    public String addressPort;

    public String addressUrl;

    public String envIdUrl;

    public String addressServerUrl;

    private boolean isHealthCheck = true;

    private String contextPath = "";

    @Value("${server.port:8848}")
    private int port;

    private String localAddress;

    @Value("${useAddressServer:false}")
    private boolean isUseAddressServer;

    private final ServletContext servletContext;

    private Member self;

    public ServerMemberManager(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @PostConstruct
    @Override
    public void init() {
        this.localAddress = InetUtils.getSelfIp() + ":" + port;

        // register NodeChangeEvent publisher to NotifyManager

        NotifyCenter.registerPublisher(NodeChangeEvent::new, NodeChangeEvent.class);

        // init nacos core sys

        initSys();

        SyncNodeTask task = new SyncNodeTask(servletContext);
        taskManager.execute(task);

        // Consistency protocol module initialization

        initAPProtocol();
        initCPProtocol();
    }

    @Override
    public void update(Member newMember) {

        String address = newMember.address();

        long nowTime = System.currentTimeMillis();

        serverListHealth.computeIfPresent(address, new BiFunction<String, Member, Member>() {
            @Override
            public Member apply(String s, Member member) {
                // If this member updates itself, ignore the health judgment
                if (!isSelf(newMember)) {
                    serverListUnHealth.remove(newMember);
                    lastRefreshTimeRecord.put(address, nowTime);
                }
                return newMember;
            }
        });
    }

    @Override
    public int indexOf(String address) {
        int index = 1;
        for (Map.Entry<String, Member> entry : serverListHealth.entrySet()) {
            if (Objects.equals(entry.getKey(), address)) {
                return index;
            }
            index++;
        }
        return index;
    }

    @Override
    public boolean hasMember(String address) {
        boolean result = serverListHealth.containsKey(address);
        if (!result) {
            for (Map.Entry<String, Member> entry : serverListHealth.entrySet()) {
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
            self = serverListHealth.get(localAddress);
        }
        return self;
    }

    @Override
    public Collection<Member> allMembers() {
        return serverListHealth.values();
    }

    @Override
    public void memberJoin(Collection<Member> members) {

        long lastRefreshTime = System.currentTimeMillis();

        if (members.isEmpty()) {
            return;
        }


        for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {

            final Member newMember = iterator.next();
            final String address = newMember.address();

            // Since this start, this node does not participate in memberJoin

            if (Objects.equals(newMember, self) || serverListHealth.containsKey(address)) {
                iterator.remove();
                continue;
            }

            // Ensure that the node is created only once
            serverListHealth.computeIfAbsent(address, s -> newMember);

            serverListHealth.computeIfPresent(address, new BiFunction<String, Member, Member>() {
                @Override
                public Member apply(String s, Member member) {
                    lastRefreshTimeRecord.put(address, lastRefreshTime);
                    return newMember;
                }
            });
        }

        NotifyCenter.publishEvent(NodeChangeEvent.class, NodeChangeEvent.builder()
                .kind("join")
                .changeNodes(members)
                .allNodes(allMembers())
                .build());

    }

    @Override
    public void memberLeave(Collection<Member> members) {

        if (members.isEmpty()) {
            return;
        }


        for (Iterator<Member> iterator = members.iterator(); iterator.hasNext(); ) {

            Member member = iterator.next();
            final String address = member.address();

            // 本节点不参与 memberLeave

            if (Objects.equals(member, self) || serverListHealth.containsKey(address)) {
                iterator.remove();
                continue;
            }

            serverListHealth.computeIfPresent(address, new BiFunction<String, Member, Member>() {
                @Override
                public Member apply(String s, Member member) {
                    lastRefreshTimeRecord.remove(address);
                    return null;
                }
            });
        }

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
    }

    private void initCPProtocol() {
        CPProtocol protocol = SpringUtils.getBean(CPProtocol.class);
        Config config = (Config) SpringUtils.getBean(protocol.configType());
        config.addLogProcessors(loadProcessorAndInjectProtocol(LogProcessor4CP.class, protocol));
        protocol.init((config));

        injectClusterInfo(protocol.protocolMetaData());

        subscribe(event -> injectClusterInfo(protocol.protocolMetaData()));
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
    public void onApplicationEvent(WebServerInitializedEvent webServerInitializedEvent) {
        taskManager.execute(new NodeStateReportTask());
        taskManager.execute(new ClearInvalidNodeTask());
    }

    public Map<String, Member> getServerListHealth() {
        return serverListHealth;
    }

    public Set<Member> getServerListUnHealth() {
        return serverListUnHealth;
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

    public void setAddressServerHealth(boolean addressServerHealth) {
        isAddressServerHealth = addressServerHealth;
    }

    public Map<String, Long> getLastRefreshTimeRecord() {
        return lastRefreshTimeRecord;
    }

}
