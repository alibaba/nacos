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
import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * connect manager.
 *
 * @author liuzunfei
 * @version $Id: ConnectionManager.java, v 0.1 2020年07月13日 7:07 PM liuzunfei Exp $
 */
@Service
public class ConnectionManager {
    
    /**
     * maxLimitClient.
     */
    private int maxClient = -1;
    
    /**
     * current loader adjust count,only effective once,use to rebalance.
     */
    private int loadClient = -1;
    
    String redirectAddress = null;
    
    @Autowired
    private ClientConnectionEventListenerRegistry clientConnectionEventListenerRegistry;
    
    Map<String, Connection> connetions = new ConcurrentHashMap<String, Connection>();
    
    /**
     * check connnectionid is valid.
     *
     * @param connectionId connectionId to be check.
     * @return
     */
    public boolean checkValid(String connectionId) {
        return connetions.containsKey(connectionId);
    }
    
    /**
     * register a new connect.
     *
     * @param connectionId connectionId
     * @param connection   connection
     */
    public void register(String connectionId, Connection connection) {
        Connection connectionInner = connetions.put(connectionId, connection);
        if (connectionInner == null) {
            clientConnectionEventListenerRegistry.notifyClientConnected(connection);
            Loggers.REMOTE
                    .info("new connection registered successfully, connectionid = {},connection={} ", connectionId,
                            connection);
        }
    }
    
    /**
     * unregister a connection .
     *
     * @param connectionId connectionId.
     */
    public void unregister(String connectionId) {
        Connection remove = this.connetions.remove(connectionId);
        if (remove != null) {
            remove.close();
            Loggers.REMOTE.info(" connection unregistered successfully,connectionid = {} ", connectionId);
            clientConnectionEventListenerRegistry.notifyClientDisConnected(remove);
        }
    }
    
    /**
     * get by connection id.
     *
     * @param connectionId connection id.
     * @return
     */
    public Connection getConnection(String connectionId) {
        return connetions.get(connectionId);
    }
    
    /**
     * get by client ip.
     *
     * @param clientIp client ip.
     * @return
     */
    public List<Connection> getConnectionByIp(String clientIp) {
        Set<Map.Entry<String, Connection>> entries = connetions.entrySet();
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
     * get curret connetions count.
     *
     * @return
     */
    public int getCurretConnectionCount() {
        return this.connetions.size();
    }
    
    /**
     * regresh connection active time.
     *
     * @param connnectionId connnectionId.
     */
    public void refreshActiveTime(String connnectionId) {
        Connection connection = connetions.get(connnectionId);
        if (connection != null) {
            connection.freshActiveTime();
        }
    }
    
    /**
     * Start Task：Expel the connection which active Time expire.
     */
    @PostConstruct
    public void start() {
        
        // Start UnHeathy Conection Expel Task.
        RpcScheduledExecutor.COMMON_SERVER_EXECUTOR.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
    
                    Loggers.REMOTE.info("rpc ack size :{}", RpcAckCallbackSynchronizer.CALLBACK_CONTEXT.size());
                    ;
    
                    MetricsMonitor.getLongConnectionMonitor().set(connetions.size());
    
                    long currentStamp = System.currentTimeMillis();
                    Set<Map.Entry<String, Connection>> entries = connetions.entrySet();
                    boolean isLoaderClient = loadClient >= 0;
                    int currentMaxClient = isLoaderClient ? loadClient : maxClient;
                    int expelCount = currentMaxClient < 0 ? currentMaxClient : entries.size() - currentMaxClient;
                    List<String> expelClient = new LinkedList<String>();
                    for (Map.Entry<String, Connection> entry : entries) {
                        Connection client = entry.getValue();
                        if (client.getMetaInfo().isSdkSource() && expelCount > 0) {
                            expelClient.add(client.getMetaInfo().getConnectionId());
                            expelCount--;
                        }
                    }
    
                    for (String expeledClientId : expelClient) {
                        try {
                            Connection connection = getConnection(expeledClientId);
                            if (connection != null) {
    
                                ConnectResetRequest connectResetRequest = new ConnectResetRequest();
                                if (StringUtils.isNotBlank(redirectAddress) && redirectAddress.contains(":")) {
                                    String[] split = redirectAddress.split(Constants.COLON);
                                    connectResetRequest.setServerIp(split[0]);
                                    connectResetRequest.setServerPort(split[1]);
                                }
                                connection.request(connectResetRequest, buildMeta());
                                Loggers.REMOTE
                                        .info("expel connection ,send switch server response connectionid = {},connectResetRequest={} ",
                                                expeledClientId, connectResetRequest);
                            }
                            
                        } catch (ConnectionAlreadyClosedException e) {
                            unregister(expeledClientId);
                        } catch (Exception e) {
                            Loggers.REMOTE.error("error occurs when expel connetion :", expeledClientId, e);
                        }
                    }
                    
                    //reset loader client
                    if (isLoaderClient) {
                        loadClient = -1;
                        redirectAddress = null;
                    }
                    
                } catch (Exception e) {
                    Loggers.REMOTE.error("error occurs when heathy check... ", e);
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
    
    public void coordinateMaxClientsSmoth(int maxClient) {
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
                Loggers.REMOTE.error("error occurs when expel connetion :", connectionId, e);
            }
        }
        
    }
    
    /**
     * get all client count.
     *
     * @return
     */
    public int currentClientsCount() {
        return connetions.size();
    }
    
    /**
     * get client count with labels filter.
     *
     * @param filterLabels label to filter client count.
     * @return count with the specific filter labels.
     */
    public int currentClientsCount(Map<String, String> filterLabels) {
        int count = 0;
        for (Connection connection : connetions.values()) {
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
    
    public Map<String, Connection> currentClients() {
        return connetions;
    }
    
    /**
     * expel all connections.
     */
    public void expelAll() {
        //reject all new connections.
        this.maxClient = 0;
        //send connect reset response to  all clients.
        for (Map.Entry<String, Connection> entry : connetions.entrySet()) {
            Connection client = entry.getValue();
            try {
                client.request(new ConnectResetRequest(), buildMeta());
            } catch (Exception e) {
                //Do Nothing.
            }
        }
    }
    
    /**
     * check if over limit.
     *
     * @return
     */
    public boolean isOverLimit() {
        return maxClient > 0 && this.connetions.size() >= maxClient;
    }
    
    public int countLimited() {
        return maxClient;
    }
}
