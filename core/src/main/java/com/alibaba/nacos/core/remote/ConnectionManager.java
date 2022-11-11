/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.api.remote.request.ClientDetectionRequest;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
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
    
    /**
     * 4 times of client keep alive.
     */
    private static final long KEEP_ALIVE_TIME = 20000L;
    
    /**
     * current loader adjust count,only effective once,use to re balance.
     */
    private int loadClient = -1;
    
    String redirectAddress = null;
    
    private Map<String, AtomicInteger> connectionForClientIp = new ConcurrentHashMap<>(16);
    
    Map<String, Connection> connections = new ConcurrentHashMap<>();
    
    @Autowired
    private ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    
    public ConnectionManager() {
    
    }
    
    /**
     * if monitor detail.
     *
     * @param clientIp clientIp.
     * @return
     */
    public boolean traced(String clientIp) {
        ConnectionLimitRule connectionLimitRule = ControlManagerCenter.getInstance().getConnectionControlManager()
                .getConnectionLimitRule();
        return connectionLimitRule != null && connectionLimitRule.getMonitorIpList() != null && connectionLimitRule
                .getMonitorIpList().contains(clientIp);
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
        
        // Start UnHealthy Connection Expel Task.
        RpcScheduledExecutor.COMMON_SERVER_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                
                ConnectionLimitRule connectionLimitRule = ControlManagerCenter.getInstance()
                        .getConnectionControlManager().getConnectionLimitRule();
                int totalCount = connections.size();
                LOGGER.info("Connection check task start");
                MetricsMonitor.getLongConnectionMonitor().set(totalCount);
                Set<Map.Entry<String, Connection>> entries = connections.entrySet();
                int currentSdkClientCount = currentSdkClientCount();
                boolean isLoaderClient = loadClient >= 0;
                int currentMaxClient = isLoaderClient ? loadClient : connectionLimitRule.getCountLimit();
                int expelCount = currentMaxClient < 0 ? 0 : Math.max(currentSdkClientCount - currentMaxClient, 0);
                
                LOGGER.info("Total count ={}, sdkCount={},clusterCount={}, currentLimit={}, toExpelCount={}",
                        totalCount, currentSdkClientCount, (totalCount - currentSdkClientCount),
                        currentMaxClient + (isLoaderClient ? "(loaderCount)" : ""), expelCount);
                
                List<String> expelClient = new LinkedList<>();
                
                Map<String, AtomicInteger> expelForIp = new HashMap<>(16);
                
                //1. calculate expel count  of ip.
                for (Map.Entry<String, Connection> entry : entries) {
                    
                    Connection client = entry.getValue();
                    String appName = client.getMetaInfo().getAppName();
                    String clientIp = client.getMetaInfo().getClientIp();
                    if (client.getMetaInfo().isSdkSource() && !expelForIp.containsKey(clientIp)) {
                        //get limit for current ip.
                        int countLimitOfIp = connectionLimitRule.getCountLimitOfIp(clientIp);
                        if (countLimitOfIp < 0) {
                            int countLimitOfApp = connectionLimitRule.getCountLimitOfApp(appName);
                            countLimitOfIp = countLimitOfApp < 0 ? countLimitOfIp : countLimitOfApp;
                        }
                        if (countLimitOfIp < 0) {
                            countLimitOfIp = connectionLimitRule.getCountLimitPerClientIpDefault();
                        }
                        
                        if (countLimitOfIp >= 0 && connectionForClientIp.containsKey(clientIp)) {
                            AtomicInteger currentCountIp = connectionForClientIp.get(clientIp);
                            if (currentCountIp != null && currentCountIp.get() > countLimitOfIp) {
                                expelForIp.put(clientIp, new AtomicInteger(currentCountIp.get() - countLimitOfIp));
                            }
                        }
                    }
                }
                
                LOGGER.info("Check over limit for ip limit rule, over limit ip count={}", expelForIp.size());
                
                if (expelForIp.size() > 0) {
                    LOGGER.info("Over limit ip expel info, {}", expelForIp);
                }
                
                Set<String> outDatedConnections = new HashSet<>();
                long now = System.currentTimeMillis();
                //2.get expel connection for ip limit.
                for (Map.Entry<String, Connection> entry : entries) {
                    Connection client = entry.getValue();
                    String clientIp = client.getMetaInfo().getClientIp();
                    AtomicInteger integer = expelForIp.get(clientIp);
                    if (integer != null && integer.intValue() > 0) {
                        integer.decrementAndGet();
                        expelClient.add(client.getMetaInfo().getConnectionId());
                        expelCount--;
                    } else if (now - client.getMetaInfo().getLastActiveTime() >= KEEP_ALIVE_TIME) {
                        outDatedConnections.add(client.getMetaInfo().getConnectionId());
                    }
                    
                }
                
                //3. if total count is still over limit.
                if (expelCount > 0) {
                    for (Map.Entry<String, Connection> entry : entries) {
                        Connection client = entry.getValue();
                        if (!expelForIp.containsKey(client.getMetaInfo().clientIp) && client.getMetaInfo().isSdkSource()
                                && expelCount > 0) {
                            expelClient.add(client.getMetaInfo().getConnectionId());
                            expelCount--;
                            outDatedConnections.remove(client.getMetaInfo().getConnectionId());
                        }
                    }
                }
                
                String serverIp = null;
                String serverPort = null;
                if (StringUtils.isNotBlank(redirectAddress) && redirectAddress.contains(Constants.COLON)) {
                    String[] split = redirectAddress.split(Constants.COLON);
                    serverIp = split[0];
                    serverPort = split[1];
                }
                
                for (String expelledClientId : expelClient) {
                    try {
                        Connection connection = getConnection(expelledClientId);
                        if (connection != null) {
                            ConnectResetRequest connectResetRequest = new ConnectResetRequest();
                            connectResetRequest.setServerIp(serverIp);
                            connectResetRequest.setServerPort(serverPort);
                            connection.asyncRequest(connectResetRequest, null);
                            LOGGER.info(
                                    "Send connection reset request , connection id = {},recommendServerIp={}, recommendServerPort={}",
                                    expelledClientId, connectResetRequest.getServerIp(),
                                    connectResetRequest.getServerPort());
                        }
                        
                    } catch (ConnectionAlreadyClosedException e) {
                        unregister(expelledClientId);
                    } catch (Exception e) {
                        LOGGER.error("Error occurs when expel connection, expelledClientId:{}", expelledClientId, e);
                    }
                }
                
                //4.client active detection.
                LOGGER.info("Out dated connection ,size={}", outDatedConnections.size());
                if (CollectionUtils.isNotEmpty(outDatedConnections)) {
                    Set<String> successConnections = new HashSet<>();
                    final CountDownLatch latch = new CountDownLatch(outDatedConnections.size());
                    for (String outDateConnectionId : outDatedConnections) {
                        try {
                            Connection connection = getConnection(outDateConnectionId);
                            if (connection != null) {
                                ClientDetectionRequest clientDetectionRequest = new ClientDetectionRequest();
                                connection.asyncRequest(clientDetectionRequest, new RequestCallBack() {
                                    @Override
                                    public Executor getExecutor() {
                                        return null;
                                    }
                                    
                                    @Override
                                    public long getTimeout() {
                                        return 1000L;
                                    }
                                    
                                    @Override
                                    public void onResponse(Response response) {
                                        latch.countDown();
                                        if (response != null && response.isSuccess()) {
                                            connection.freshActiveTime();
                                            successConnections.add(outDateConnectionId);
                                        }
                                    }
                                    
                                    @Override
                                    public void onException(Throwable e) {
                                        latch.countDown();
                                    }
                                });
                                
                                LOGGER.info("[{}]send connection active request ", outDateConnectionId);
                            } else {
                                latch.countDown();
                            }
                            
                        } catch (ConnectionAlreadyClosedException e) {
                            latch.countDown();
                        } catch (Exception e) {
                            LOGGER.error("[{}]Error occurs when check client active detection ,error={}",
                                    outDateConnectionId, e);
                            latch.countDown();
                        }
                    }
                    
                    latch.await(3000L, TimeUnit.MILLISECONDS);
                    LOGGER.info("Out dated connection check successCount={}", successConnections.size());
                    
                    for (String outDateConnectionId : outDatedConnections) {
                        if (!successConnections.contains(outDateConnectionId)) {
                            LOGGER.info("[{}]Unregister Out dated connection....", outDateConnectionId);
                            unregister(outDateConnectionId);
                        }
                    }
                }
                
                //reset loader client
                
                if (isLoaderClient) {
                    loadClient = -1;
                    redirectAddress = null;
                }
                
                LOGGER.info("Connection check task end");
                
            } catch (Throwable e) {
                LOGGER.error("Error occurs during connection check... ", e);
            }
        }, 1000L, 3000L, TimeUnit.MILLISECONDS);
        
    }
    
    public void loadCount(int loadClient, String redirectAddress) {
        this.loadClient = loadClient;
        this.redirectAddress = redirectAddress;
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
