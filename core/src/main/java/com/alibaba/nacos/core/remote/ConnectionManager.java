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
import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.core.remote.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.core.utils.Loggers;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
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
public class ConnectionManager extends Subscriber<ConnectionLimitRuleChangeEvent> {
    
    public ConnectionManager() {
        NotifyCenter.registerToPublisher(ConnectionLimitRuleChangeEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(this);
    }
    
    /**
     * maxLimitClient.
     */
    private int maxClient = -1;
    
    private ConnectionLimitRule connectionLimitRule;
    
    /**
     * current loader adjust count,only effective once,use to rebalance.
     */
    private int loadClient = -1;
    
    String redirectAddress = null;
    
    private Map<String, AtomicInteger> connectionForClientIp = new ConcurrentHashMap<String, AtomicInteger>(16);
    
    @Autowired
    private ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    
    Map<String, Connection> connections = new ConcurrentHashMap<String, Connection>();
    
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
            if (connections.containsKey(connectionId)) {
                return true;
            }
            if (!checkLimit(connection)) {
                return false;
            }
            connections.put(connectionId, connection);
            connectionForClientIp.get(connection.getMetaInfo().clientIp).getAndIncrement();
            
            clientConnectionEventListenerRegistry.notifyClientConnected(connection);
            Loggers.REMOTE
                    .info("new connection registered successfully, connectionId = {},connection={} ", connectionId,
                            connection);
            return true;
            
        }
        return false;
        
    }
    
    private boolean checkLimit(Connection connection) {
        
        if (isOverLimit()) {
            return false;
        }
        
        String clientIp = connection.getMetaInfo().clientIp;
        if (!connectionForClientIp.containsKey(clientIp)) {
            connectionForClientIp.putIfAbsent(clientIp, new AtomicInteger(0));
        }
        
        AtomicInteger currentCount = connectionForClientIp.get(clientIp);
        
        if (connectionLimitRule != null) {
            // 1.check rule of specific client ip limit.
            if (connectionLimitRule.getCountLimitPerClientIp().containsKey(clientIp)) {
                Integer integer = connectionLimitRule.getCountLimitPerClientIp().get(clientIp);
                if (integer != null && integer.intValue() >= 0) {
                    return currentCount.get() < integer.intValue();
                }
            }
            // 2.check rule of specific client app limit.
            String appName = connection.getMetaInfo().getAppName();
            if (StringUtils.isNotBlank(appName) && connectionLimitRule.getCountLimitPerClientApp()
                    .containsKey(appName)) {
                Integer integerApp = connectionLimitRule.getCountLimitPerClientApp().get(appName);
                if (integerApp != null && integerApp.intValue() >= 0) {
                    return currentCount.get() < integerApp.intValue();
                }
            }
            
            // 3.check rule of default client ip.
            int countLimitPerClientIpDefault = connectionLimitRule.getCountLimitPerClientIpDefault();
            return countLimitPerClientIpDefault <= 0 || currentCount.get() < countLimitPerClientIpDefault;
        }
        
        return true;
        
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
            int count = connectionForClientIp.get(clientIp).decrementAndGet();
            if (count == 0) {
                connectionForClientIp.remove(clientIp);
            }
            remove.close();
            Loggers.REMOTE.info(" connection unregistered successfully,connectionId = {} ", connectionId);
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
        RpcScheduledExecutor.COMMON_SERVER_EXECUTOR.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    MetricsMonitor.getLongConnectionMonitor().set(connections.size());
                    Set<Map.Entry<String, Connection>> entries = connections.entrySet();
                    int currentSdkClientCount = currentSdkClientCount();
                    boolean isLoaderClient = loadClient >= 0;
                    int currentMaxClient = isLoaderClient ? loadClient : maxClient;
                    int expelCount = currentMaxClient < 0 ? currentMaxClient : currentSdkClientCount - currentMaxClient;
                    List<String> expelClient = new LinkedList<String>();
                    for (Map.Entry<String, Connection> entry : entries) {
                        Connection client = entry.getValue();
                        if (client.getMetaInfo().isSdkSource() && expelCount > 0) {
                            expelClient.add(client.getMetaInfo().getConnectionId());
                            expelCount--;
                        }
                        
                    }
                    
                    ConnectResetRequest connectResetRequest = new ConnectResetRequest();
                    if (StringUtils.isNotBlank(redirectAddress) && redirectAddress.contains(Constants.COLON)) {
                        String[] split = redirectAddress.split(Constants.COLON);
                        connectResetRequest.setServerIp(split[0]);
                        connectResetRequest.setServerPort(split[1]);
                    }
                    
                    for (String expelledClientId : expelClient) {
                        try {
                            Connection connection = getConnection(expelledClientId);
                            if (connection != null) {
                                connection.asyncRequest(connectResetRequest, buildMeta(), null);
                                Loggers.REMOTE
                                        .info("send connection reset server , connection id = {},recommendServerIp={}, recommendServerPort={}",
                                                expelledClientId, connectResetRequest.getServerIp(),
                                                connectResetRequest.getServerPort());
                            }
                            
                        } catch (ConnectionAlreadyClosedException e) {
                            unregister(expelledClientId);
                        } catch (Exception e) {
                            Loggers.REMOTE.error("error occurs when expel connection :", expelledClientId, e);
                        }
                    }
                    
                    //reset loader client
                    if (isLoaderClient) {
                        loadClient = -1;
                        redirectAddress = null;
                    }
                    
                } catch (Throwable e) {
                    Loggers.REMOTE.error("error occurs when healthy check... ", e);
                }
            }
        }, 1000L, 3000L, TimeUnit.MILLISECONDS);
        
    }
    
    private RequestMeta buildMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        return meta;
    }
    
    public void setMaxClientCount(int maxClient) {
        this.maxClient = maxClient;
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
                    connection.request(connectResetRequest, buildMeta());
                } catch (ConnectionAlreadyClosedException e) {
                    unregister(connectionId);
                } catch (Exception e) {
                    Loggers.REMOTE.error("error occurs when expel connection :", connectionId, e);
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
     * @return
     */
    public int currentSdkClientCount() {
        Map<String, String> filter = new HashMap<String, String>(2);
        filter.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        return currentClientsCount(filter);
    }
    
    public Map<String, Connection> currentClients() {
        return connections;
    }
    
    /**
     * expel all connections.
     */
    public void expelAll() {
        //reject all new connections.
        this.maxClient = 0;
        //send connect reset response to  all clients.
        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            Connection client = entry.getValue();
            try {
                if (client.getMetaInfo().isSdkSource()) {
                    client.request(new ConnectResetRequest(), buildMeta());
                }
                
            } catch (Exception e) {
                //Do Nothing.
            }
        }
    }
    
    /**
     * check if over limit.
     *
     * @return over limit or not.
     */
    private boolean isOverLimit() {
        return maxClient > 0 && currentSdkClientCount() >= maxClient;
    }
    
    public int countLimited() {
        return maxClient;
    }
    
    @Override
    public void onEvent(ConnectionLimitRuleChangeEvent event) {
        String limitRule = event.getLimitRule();
        try {
            ConnectionLimitRule connectionLimitRule = new Gson().fromJson(limitRule, ConnectionLimitRule.class);
            if (connectionLimitRule.getCountLimit() > 0) {
                this.maxClient = connectionLimitRule.getCountLimit();
            }
            this.connectionLimitRule = connectionLimitRule;
        } catch (Exception e) {
            Loggers.REMOTE.error("Fail to parse connection limit rule :{}", limitRule, e);
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ConnectionLimitRuleChangeEvent.class;
    }
    
    static class ConnectionLimitRule {
        
        private int countLimit = -1;
        
        private int countLimitPerClientIpDefault = -1;
        
        private Map<String, Integer> countLimitPerClientIp = new HashMap<String, Integer>();
        
        private Map<String, Integer> countLimitPerClientApp = new HashMap<String, Integer>();
        
        public int getCountLimit() {
            return countLimit;
        }
        
        public void setCountLimit(int countLimit) {
            this.countLimit = countLimit;
        }
        
        public int getCountLimitPerClientIpDefault() {
            return countLimitPerClientIpDefault;
        }
        
        public void setCountLimitPerClientIpDefault(int countLimitPerClientIpDefault) {
            this.countLimitPerClientIpDefault = countLimitPerClientIpDefault;
        }
        
        public Map<String, Integer> getCountLimitPerClientIp() {
            return countLimitPerClientIp;
        }
        
        public void setCountLimitPerClientIp(Map<String, Integer> countLimitPerClientIp) {
            this.countLimitPerClientIp = countLimitPerClientIp;
        }
        
        public Map<String, Integer> getCountLimitPerClientApp() {
            return countLimitPerClientApp;
        }
        
        public void setCountLimitPerClientApp(Map<String, Integer> countLimitPerClientApp) {
            this.countLimitPerClientApp = countLimitPerClientApp;
        }
    }
}
