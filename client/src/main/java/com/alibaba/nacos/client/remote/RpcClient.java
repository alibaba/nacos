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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * abstract remote client to connect to server.
 *
 * @author liuzunfei
 * @version $Id: RpcClient.java, v 0.1 2020年07月13日 9:15 PM liuzunfei Exp $
 */
public abstract class RpcClient implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(RpcClient.class);
    
    private ServerListFactory serverListFactory;
    
    protected String connectionId;
    
    protected AtomicReference<RpcClientStatus> rpcClientStatus = new AtomicReference<RpcClientStatus>(
            RpcClientStatus.WAIT_INIT);
    
    /**
     * Notify when client connected.
     */
    protected void notifyReConnected() {
        if (!this.connectionEventListeners.isEmpty()) {
            connectionEventListeners.forEach(new Consumer<ConnectionEventListener>() {
                @Override
                public void accept(ConnectionEventListener connectionEventListener) {
                    connectionEventListener.onReconnected();
                }
            });
        }
    }
    
    /**
     * check is this client is inited.
     *
     * @return
     */
    public boolean isWaitInited() {
        return this.rpcClientStatus.get() == RpcClientStatus.WAIT_INIT;
    }
    
    /**
     * check is this client is running.
     *
     * @return
     */
    public boolean isRunning() {
        return this.rpcClientStatus.get() == RpcClientStatus.RUNNING;
    }
    
    /**
     * check is this client is in init status,have not start th client.
     *
     * @return
     */
    public boolean isInitStatus() {
        return this.rpcClientStatus.get() == RpcClientStatus.INITED;
    }
    
    /**
     * check is this client is in starting process.
     *
     * @return
     */
    public boolean isStarting() {
        return this.rpcClientStatus.get() == RpcClientStatus.STARTING;
    }
    
    /**
     * listener called where connect status changed.
     */
    protected List<ConnectionEventListener> connectionEventListeners = new ArrayList<ConnectionEventListener>();
    
    /**
     * change listeners handler registry.
     */
    protected List<ServerPushResponseHandler> serverPushResponseListeners = new ArrayList<ServerPushResponseHandler>();
    
    public RpcClient() {
    }
    
    /**
     * init server list factory.
     *
     * @param serverListFactory serverListFactory
     */
    public void init(ServerListFactory serverListFactory) {
        if (!isWaitInited()) {
            return;
        }
        this.serverListFactory = serverListFactory;
        this.connectionId = UUID.randomUUID().toString();
        rpcClientStatus.compareAndSet(RpcClientStatus.WAIT_INIT, RpcClientStatus.INITED);
        
        LOGGER.info("RpcClient init ,connectionId={}, ServerListFactory ={}", this.connectionId,
                serverListFactory.getClass().getName());
    }
    
    public RpcClient(ServerListFactory serverListFactory) {
        this.serverListFactory = serverListFactory;
        this.connectionId = UUID.randomUUID().toString();
        rpcClientStatus.compareAndSet(RpcClientStatus.WAIT_INIT, RpcClientStatus.INITED);
        LOGGER.info("RpcClient init in constructor ,connectionId={}, ServerListFactory ={}", this.connectionId,
                serverListFactory.getClass().getName());
    }
    
    /**
     * Start this client.
     */
    @PostConstruct
    public abstract void start() throws NacosException;
    
    /**
     * send request.
     *
     * @param request request.
     * @return
     */
    public abstract Response request(Request request) throws NacosException;
    
    /**
     * register connection handler.will be notified wher inner connect chanfed.
     *
     * @param connectionEventListener connectionEventListener
     */
    public void registerConnectionListener(ConnectionEventListener connectionEventListener) {
    
        LOGGER.info(" Registry connection listener to current client,connectionId={}, connectionEventListener={}",
                this.connectionId, connectionEventListener.getClass().getName());
        this.connectionEventListeners.add(connectionEventListener);
    }
    
    /**
     * register change listeners ,will be called when server send change notify response th current client.
     *
     * @param serverPushResponseHandler serverPushResponseHandler
     */
    public void registerServerPushResponseHandler(ServerPushResponseHandler serverPushResponseHandler) {
        LOGGER.info(
                " Registry server push response  listener to current client,connectionId={}, connectionEventListener={}",
                this.connectionId, serverPushResponseHandler.getClass().getName());
    
        this.serverPushResponseListeners.add(serverPushResponseHandler);
    }
    
    /**
     * Getter method for property <tt>serverListFactory</tt>.
     *
     * @return property value of serverListFactory
     */
    public ServerListFactory getServerListFactory() {
        return serverListFactory;
    }
}
