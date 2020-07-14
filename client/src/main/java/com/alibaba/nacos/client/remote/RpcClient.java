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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;

/**
 * abstract remote client to connect to server.
 * @author liuzunfei
 * @version $Id: RpcClient.java, v 0.1 2020年07月13日 9:15 PM liuzunfei Exp $
 */
public abstract class RpcClient {
    
    private ServerListFactory serverListFactory;
    
    protected String connectionId;
    
    protected RpcClientStatus rpcClientStatus = RpcClientStatus.WAIT_INIT;
    
    /**
     * check is this client is inited.
     *
     * @return
     */
    public boolean isWaitInited() {
        return this.rpcClientStatus == RpcClientStatus.WAIT_INIT;
    }
    
    /**
     * listener called where connect status changed.
     */
    List<ConnectionEventListener> connectionEventListeners = new ArrayList<ConnectionEventListener>();
    
    /**
     * change listeners handler registry.
     */
    List<ChangeListenResponseHandler> changeListenReplyListeners = new ArrayList<ChangeListenResponseHandler>();
    
    public RpcClient() {
    
    }
    
    /**
     * init server list factory.
     *
     * @param serverListFactory serverListFactory
     */
    public void init(ServerListFactory serverListFactory) {
        this.serverListFactory = serverListFactory;
        this.connectionId = UUID.randomUUID().toString();
        this.rpcClientStatus = RpcClientStatus.INITED;
    }
    
    public RpcClient(ServerListFactory serverListFactory) {
        this.serverListFactory = serverListFactory;
        this.connectionId = UUID.randomUUID().toString();
        this.rpcClientStatus = RpcClientStatus.INITED;
    }
    
    /**
     * Start this client.
     */
    @PostConstruct
    public abstract void start() throws NacosException;
    
    /**
     * Switch Server.
     */
    public abstract void switchServer();
    
    /**
     * send request.
     * @param request request.
     * @return
     */
    public abstract Response request(Request request);
    
    /**
     * register connection handler.will be notified wher inner connect chanfed.
     * @param connectionEventListener connectionEventListener
     */
    public void registerConnectionListener(ConnectionEventListener connectionEventListener) {
        this.connectionEventListeners.add(connectionEventListener);
    }
    
    /**
     * register change listeners ,will be called when server send change notify response th current client.
     * @param changeListenResponseHandler changeListenResponseHandler
     */
    public void registerChangeListenHandler(ChangeListenResponseHandler changeListenResponseHandler) {
        this.changeListenReplyListeners.add(changeListenResponseHandler);
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
