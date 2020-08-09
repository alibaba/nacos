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
    
    private static final long EXPIRE_MILLSECOND = 10000L;
    
    private ScheduledExecutorService executors = Executors.newScheduledThreadPool(2);
    
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
    
    public Connection getConnection(String connectionId) {
        return connetions.get(connectionId);
    }
    
    /**
     * get curret connetions count.
     *
     * @return
     */
    public int getCurretConnectionCount() {
        return this.connetions.size();
    }
    
    public void setSwitching(String connectionId) {
        Connection connection = connetions.get(connectionId);
        if (connection != null) {
            connection.setStatus(Connection.SWITCHING);
        }
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
                    long currentStamp = System.currentTimeMillis();
                    Set<Map.Entry<String, Connection>> entries = connetions.entrySet();
                    boolean isLoaderClient = loadClient >= 0;
                    int currentMaxClient = isLoaderClient ? loadClient : maxClient;
                    int expelCount = currentMaxClient < 0 ? currentMaxClient : entries.size() - currentMaxClient;
                    List<String> expelClient = new LinkedList<String>();
                    
                    List<String> expireCLients = new LinkedList<String>();
                    for (Map.Entry<String, Connection> entry : entries) {
                        Connection client = entry.getValue();
                        long lastActiveTimestamp = entry.getValue().getLastActiveTimestamp();
                        if (client.heartBeatExpire() && currentStamp - lastActiveTimestamp > EXPIRE_MILLSECOND) {
                            expireCLients.add(client.getConnectionId());
                            expelCount--;
                        } else if (expelCount > 0) {
                            expelClient.add(client.getConnectionId());
                            expelCount--;
                        }
                    }
                    
                    for (String expireClient : expireCLients) {
                        unregister(expireClient);
                        Loggers.RPC.info("expire connection found ，success expel connectionid = {} ", expireClient);
                    }
                    
                    for (String expeledClient : expelClient) {
                        try {
                            Connection connection = getConnection(expeledClient);
                            if (connection != null) {
                                if (connection.isSwitching()) {
                                    continue;
                                }
                                connection.sendRequestNoAck(new ConnectResetRequest());
                                connection.setStatus(Connection.SWITCHING);
                                Loggers.RPC.info("expel connection ,send switch server response connectionid = {} ",
                                        expeledClient);
                            }
                            
                        } catch (ConnectionAlreadyClosedException e) {
                            unregister(expeledClient);
                        } catch (Exception e) {
                            Loggers.RPC.error("error occurs when expel connetion :", expeledClient, e);
                        }
                        
                    }
                    
                    //reset loader client
                    if (isLoaderClient) {
                        loadClient = -1;
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
    
    public void loadClientsSmoth(int loadClient) {
        this.loadClient = loadClient;
    }
    
    public int currentClients() {
        return connetions.size();
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
    
}
