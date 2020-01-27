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

import com.alibaba.nacos.core.cluster.task.NodeStateReportTask;
import com.alibaba.nacos.core.cluster.task.SyncNodeTask;
import com.alibaba.nacos.core.distributed.Config;
import com.alibaba.nacos.core.distributed.ConsistencyProtocol;
import com.alibaba.nacos.core.distributed.id.DistributeIDManager;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.alibaba.nacos.core.utils.SystemUtils.LOCAL_IP;

/**
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class ServerNodeManager implements ApplicationListener<WebServerInitializedEvent>, NodeManager {

    private final NodeTaskManager taskManager = new NodeTaskManager(this);

    private Map<String, Node> serverListHealth = new ConcurrentSkipListMap<>();

    private Set<Node> serverListUnHealth = new CopyOnWriteArraySet<>();

    private volatile List<Node> nodeView = new ArrayList<>();

    private volatile boolean isInIpList = true;

    private volatile boolean isAddressServerHealth = true;

    public String domainName;

    public String addressPort;

    public String addressUrl;

    public String envIdUrl;

    public String addressServerUrl;

    private boolean isHealthCheck = true;

    @Value("${server.port:8848}")
    private int port;

    @Value("${useAddressServer:true}")
    private boolean isUseAddressServer;

    private final ServletContext servletContext;

    private Node self;

    public ServerNodeManager(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @PostConstruct
    @Override
    public void init() {

        // Initialize the internal distributed ID factory

        DistributeIDManager.init();

        // register NodeChangeEvent publisher to NotifyManager

        NotifyManager.registerPublisher(NodeChangeEvent::new, NodeChangeEvent.class);

        // init nacos core sys

        initSys();

        SyncNodeTask task = new SyncNodeTask(servletContext);
        taskManager.execute(task);

        // Consistency protocol module initialization

        initConsistencyProtocol();

    }

    @Override
    public void update(Node newNode) {

        long lastRefTime = Long.parseLong(newNode.extendVal(Node.LAST_REF_TIME));

        if (lastRefTime < System.currentTimeMillis()) {
            newNode.setState(NodeState.DOWN);
            serverListHealth.remove(newNode.address());
            serverListUnHealth.add(newNode);
        } else {
            serverListHealth.put(newNode.address(), newNode);
        }

        // reset node view to lazy update

        nodeView = null;

    }

    @Override
    public int indexOf(String address) {
        int index = -1;
        for (Map.Entry<String, Node> entry : serverListHealth.entrySet()) {
            if (Objects.equals(entry.getKey(), address)) {
                return index;
            }
            index ++;
        }
        return index;
    }

    @Override
    public boolean hasNode(String address) {
        return serverListHealth.containsKey(address);
    }

    @Override
    public Node self() {
        if (Objects.isNull(self)) {
            for (Node node : serverListHealth.values()) {
                if (node.address().contains(LOCAL_IP)) {
                    self = node;
                    break;
                }
            }
        }
        return self;
    }

    @Override
    public List<Node> allNodes() {
        if (CollectionUtils.isEmpty(nodeView)) {
            nodeView = new ArrayList<>(serverListHealth.values());
        }
        return nodeView;
    }

    @Override
    public synchronized void nodeJoin(Collection<Node> nodes) {

        for (Node node : nodes) {
            serverListHealth.put(node.address(), node);
        }

        // reset node view

        nodeView = null;

        NotifyManager.publishEvent(NodeChangeEvent.class, NodeChangeEvent.builder()
                .kind("join")
                .changeNodes(nodes)
                .allNodes(allNodes())
                .build());

    }

    @Override
    public synchronized void nodeLeave(Collection<Node> nodes) {
        for (Node node : nodes) {
            serverListHealth.remove(node.address());
        }

        // reset node view

        nodeView = null;

        NotifyManager.publishEvent(NodeChangeEvent.class, NodeChangeEvent.builder()
                .kind("leave")
                .changeNodes(nodes)
                .allNodes(allNodes())
                .build());
    }

    @Override
    public void subscribe(NodeChangeListener listener) {
        NotifyManager.subscribe(listener);
    }

    @Override
    public void unSubscribe(NodeChangeListener listener) {
        NotifyManager.unSubscribe(listener);
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

    private void initConsistencyProtocol() {
        Map<String, ConsistencyProtocol> protocols = SpringUtils.getBeansOfType(ConsistencyProtocol.class);
        for (ConsistencyProtocol protocol : protocols.values()) {
            protocol.init((Config) SpringUtils.getBean(protocol.configType()));
        }
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent webServerInitializedEvent) {
        taskManager.execute(new NodeStateReportTask());
    }

    public List<Node> getServerListHealth() {
        return nodeView;
    }

    public Set<Node> getServerListUnHealth() {
        return serverListUnHealth;
    }

    public boolean isUseAddressServer() {
        return isUseAddressServer;
    }

    public boolean isFirstIp() {
        return serverListHealth.get(0).address().contains(LOCAL_IP);
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
}
