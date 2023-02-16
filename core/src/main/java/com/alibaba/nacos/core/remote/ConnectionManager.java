/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * connect manager.
 *
 * @author liuzunfei
 * @version $Id: ConnectionManager.java, v 0.1 2020年07月13日 7:07 PM liuzunfei Exp $
 */
@Service
public class ConnectionManager {
    
    private static final Logger LOGGER = com.alibaba.nacos.plugin.control.Loggers.CONNECTION;
    
    private Map<String, AtomicInteger> connectionForClientIp = new ConcurrentHashMap<>(16);
    
    Map<String, Connection> connections = new ConcurrentHashMap<>();
    
    private RuntimeConnectionEjector runtimeConnectionEjector;
    
    private ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    
    public ConnectionManager(ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry) {
        this.clientConnectionEventListenerRegistry = clientConnectionEventListenerRegistry;
    }
    
    /**
     * if monitor detail.
     *
     * @param clientIp clientIp.
     * @return
     */
    public boolean traced(String clientIp) {
        ConnectionControlRule connectionControlRule = ControlManagerCenter.getInstance().getConnectionControlManager()
                .getConnectionLimitRule();
        return connectionControlRule != null && connectionControlRule.getMonitorIpList() != null
                && connectionControlRule.getMonitorIpList().contains(clientIp);
    }
    
    /**
     * check connection id is valid.
     *
     * @param connectionId connectionId to be check.
     * @return is valid or not.
     */
    public boolean checkValid(String connectionId) {
        return connections.containsKey(connectionId);
    }
    
    /**
     * register a new connect.
     *
     * @param connectionId connectionId
     * @param connection   connection
     */
    public synchronized boolean register(String connectionId, Connection connection) {
        
        if (connection.isConnected()) {
            String clientIp = connection.getMetaInfo().clientIp;
            if (connections.containsKey(connectionId)) {
                return true;
            }
            if (checkLimit(connection)) {
                return false;
            }
            if (traced(clientIp)) {
                connection.setTraced(true);
            }
            connections.put(connectionId, connection);
            if (!connectionForClientIp.containsKey(clientIp)) {
                connectionForClientIp.put(clientIp, new AtomicInteger(0));
            }
            connectionForClientIp.get(clientIp).getAndIncrement();
            
            clientConnectionEventListenerRegistry.notifyClientConnected(connection);
            
            LOGGER.info("new connection registered successfully, connectionId = {},connection={} ", connectionId,
                    connection);
            return true;
            
        }
        return false;
        
    }
    
    private boolean checkLimit(Connection connection) {
        if (connection.getMetaInfo().isClusterSource()) {
            return false;
        }
        ConnectionMeta metaInfo = connection.getMetaInfo();
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest(metaInfo.getClientIp(),
                metaInfo.getAppName(), metaInfo.getLabel(RemoteConstants.LABEL_SOURCE));
        connectionCheckRequest.setLabels(connection.getLabels());
        ConnectionCheckResponse checkResponse = ControlManagerCenter.getInstance().getConnectionControlManager()
                .check(connectionCheckRequest);
        return !checkResponse.isSuccess();
    }
    
    /**
     * unregister a connection .
     *
     * @param connectionId connectionId.
     */
    public synchronized void unregister(String connectionId) {
        Connection remove = this.connections.remove(connectionId);
        if (remove != null) {
            String clientIp = remove.getMetaInfo().clientIp;
            AtomicInteger atomicInteger = connectionForClientIp.get(clientIp);
            if (atomicInteger != null) {
                int count = atomicInteger.decrementAndGet();
                if (count <= 0) {
                    connectionForClientIp.remove(clientIp);
                }
            }
            remove.close();
            LOGGER.info("[{}]Connection unregistered successfully. ", connectionId);
            clientConnectionEventListenerRegistry.notifyClientDisConnected(remove);
        }
    }
    
    /**
     * get by connection id.
     *
     * @param connectionId connection id.
     * @return connection of the id.
     */
    public Connection getConnection(String connectionId) {
        return connections.get(connectionId);
    }
    
    /**
     * get by client ip.
     *
     * @param clientIp client ip.
     * @return connections of the client ip.
     */
    public List<Connection> getConnectionByIp(String clientIp) {
        Set<Map.Entry<String, Connection>> entries = connections.entrySet();
        List<Connection> connections = new ArrayList<>();
        for (Map.Entry<String, Connection> entry : entries) {
            Connection value = entry.getValue();
            if (clientIp.equals(value.getMetaInfo().clientIp)) {
                connections.add(value);
            }
        }
        return connections;
    }
    
    /**
     * init connection ejector.
     */
    public void initConnectionEjector() {
        String connectionRuntimeEjector = null;
        try {
            connectionRuntimeEjector = ControlConfigs.getInstance().getConnectionRuntimeEjector();
            Collection<RuntimeConnectionEjector> ejectors = NacosServiceLoader.load(RuntimeConnectionEjector.class);
            for (RuntimeConnectionEjector runtimeConnectionEjectorLoad : ejectors) {
                if (runtimeConnectionEjectorLoad.getName().equalsIgnoreCase(connectionRuntimeEjector)) {
                    Loggers.CONNECTION.info("Found connection runtime ejector for name {}", connectionRuntimeEjector);
                    runtimeConnectionEjectorLoad.setConnectionManager(this);
                    runtimeConnectionEjector = runtimeConnectionEjectorLoad;
                }
            }
        } catch (Throwable throwable) {
            Loggers.CONNECTION.warn("Fail to load  runtime ejector ", throwable);
        }
        
        if (runtimeConnectionEjector == null) {
            Loggers.CONNECTION
                    .info("Fail to find connection runtime ejector for name {},use default", connectionRuntimeEjector);
            NacosRuntimeConnectionEjector nacosRuntimeConnectionEjector = new NacosRuntimeConnectionEjector();
            nacosRuntimeConnectionEjector.setConnectionManager(this);
            runtimeConnectionEjector = nacosRuntimeConnectionEjector;
        }
    }
    
    /**
     * get current connections count.
     *
     * @return get all connection count
     */
    public int getCurrentConnectionCount() {
        return this.connections.size();
    }
    
    /**
     * regresh connection active time.
     *
     * @param connectionId connectionId.
     */
    public void refreshActiveTime(String connectionId) {
        Connection connection = connections.get(connectionId);
        if (connection != null) {
            connection.freshActiveTime();
        }
    }
    
    /**
     * Start Task：Expel the connection which active Time expire.
     */
    @PostConstruct
    public void start() {
        
        initConnectionEjector();
        // Start UnHealthy Connection Expel Task.
        RpcScheduledExecutor.COMMON_SERVER_EXECUTOR.scheduleWithFixedDelay(() -> {
            runtimeConnectionEjector.doEject();
        }, 1000L, 3000L, TimeUnit.MILLISECONDS);
        
    }
    
    public void loadCount(int loadClient, String redirectAddress) {
        runtimeConnectionEjector.setLoadClient(loadClient);
        runtimeConnectionEjector.setRedirectAddress(redirectAddress);
    }
    
    /**
     * send load request to spefic connetionId.
     *
     * @param connectionId    connection id of client.
     * @param redirectAddress server address to redirect.
     */
    public void loadSingle(String connectionId, String redirectAddress) {
        Connection connection = getConnection(connectionId);
        
        if (connection != null) {
            if (connection.getMetaInfo().isSdkSource()) {
                ConnectResetRequest connectResetRequest = new ConnectResetRequest();
                if (StringUtils.isNotBlank(redirectAddress) && redirectAddress.contains(Constants.COLON)) {
                    String[] split = redirectAddress.split(Constants.COLON);
                    connectResetRequest.setServerIp(split[0]);
                    connectResetRequest.setServerPort(split[1]);
                }
                try {
                    connection.request(connectResetRequest, 3000L);
                } catch (ConnectionAlreadyClosedException e) {
                    unregister(connectionId);
                } catch (Exception e) {
                    LOGGER.error("error occurs when expel connection, connectionId: {} ", connectionId, e);
                }
            }
        }
        
    }
    
    /**
     * get all client count.
     *
     * @return client count.
     */
    public int currentClientsCount() {
        return connections.size();
    }
    
    /**
     * get client count with labels filter.
     *
     * @param filterLabels label to filter client count.
     * @return count with the specific filter labels.
     */
    public int currentClientsCount(Map<String, String> filterLabels) {
        int count = 0;
        for (Connection connection : connections.values()) {
            Map<String, String> labels = connection.getMetaInfo().labels;
            boolean disMatchFound = false;
            for (Map.Entry<String, String> entry : filterLabels.entrySet()) {
                if (!entry.getValue().equals(labels.get(entry.getKey()))) {
                    disMatchFound = true;
                    break;
                }
            }
            if (!disMatchFound) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * get client count from sdk.
     *
     * @return sdk client count.
     */
    public int currentSdkClientCount() {
        Map<String, String> filter = new HashMap<>(2);
        filter.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        return currentClientsCount(filter);
    }
    
    public Map<String, Connection> currentClients() {
        return connections;
    }
    
    public Map<String, AtomicInteger> getConnectionForClientIp() {
        return connectionForClientIp;
    }
}
