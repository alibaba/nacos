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

import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    
    private static final long EXPIRE_MILLSECOND = 10000L;
    
    private ScheduledExecutorService executors = Executors.newScheduledThreadPool(1);
    
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
            Loggers.RPC.info("new connection registered successfully, connectionid = {} ", connectionId);
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
            remove.closeGrapcefully();
            Loggers.RPC.info(" connection unregistered successfully,connectionid = {} ", connectionId);
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
    public Connection getConnectionByIp(String clientIp) {
        Set<Map.Entry<String, Connection>> entries = connetions.entrySet();
        for (Map.Entry<String, Connection> entry : entries) {
            Connection value = entry.getValue();
            if (clientIp.equals(value.getMetaInfo().clientIp)) {
                return value;
            }
        }
        return null;
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
        executors.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
    
                    MetricsMonitor.getLongConnectionMonitor().set(connetions.size());
    
                    long currentStamp = System.currentTimeMillis();
                    Set<Map.Entry<String, Connection>> entries = connetions.entrySet();
                    boolean isLoaderClient = loadClient >= 0;
                    int currentMaxClient = isLoaderClient ? loadClient : maxClient;
                    int expelCount = currentMaxClient < 0 ? currentMaxClient : entries.size() - currentMaxClient;
                    List<String> expelClient = new LinkedList<String>();
                    for (Map.Entry<String, Connection> entry : entries) {
                        Connection client = entry.getValue();
                        if (client.isSdkSource() && expelCount > 0) {
                            expelClient.add(client.getConnectionId());
                            expelCount--;
                        }
                    }
    
                    for (String expeledClientId : expelClient) {
                        try {
                            Connection connection = getConnection(expeledClientId);
                            if (connection != null) {
    
                                ConnectResetRequest connectResetRequest = new ConnectResetRequest();
                                if (StringUtils.isNotBlank(redirectAddress) && redirectAddress.contains(":")) {
                                    String[] split = redirectAddress.split(":");
                                    connectResetRequest.setServerIp(split[0]);
                                    connectResetRequest.setServerPort(split[1]);
                                }
                                connection.sendRequestNoAck(connectResetRequest);
                                Loggers.RPC
                                        .info("expel connection ,send switch server response connectionid = {},connectResetRequest={} ",
                                                expeledClientId, connectResetRequest);
                            }
                            
                        } catch (ConnectionAlreadyClosedException e) {
                            unregister(expeledClientId);
                        } catch (Exception e) {
                            Loggers.RPC.error("error occurs when expel connetion :", expeledClientId, e);
                        }
                    }
                    
                    //reset loader client
                    if (isLoaderClient) {
                        loadClient = -1;
                        redirectAddress = null;
                    }
                    
                } catch (Exception e) {
                    Loggers.RPC.error("error occurs when heathy check... ", e);
                }
            }
        }, 500L, 3000L, TimeUnit.MILLISECONDS);
        
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
     * @param connectionId
     * @param redirectAddress
     */
    public void loadSingle(String connectionId, String redirectAddress) {
        Connection connection = getConnection(connectionId);
        
        if (connection != null) {
            ConnectResetRequest connectResetRequest = new ConnectResetRequest();
            if (StringUtils.isNotBlank(redirectAddress) && redirectAddress.contains(":")) {
                String[] split = redirectAddress.split(":");
                connectResetRequest.setServerIp(split[0]);
                connectResetRequest.setServerPort(split[1]);
            }
            try {
                connection.sendRequestNoAck(connectResetRequest);
            } catch (ConnectionAlreadyClosedException e) {
                unregister(connectionId);
            } catch (Exception e) {
                Loggers.RPC.error("error occurs when expel connetion :", connectionId, e);
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
                client.sendRequestNoAck(new ConnectResetRequest());
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
