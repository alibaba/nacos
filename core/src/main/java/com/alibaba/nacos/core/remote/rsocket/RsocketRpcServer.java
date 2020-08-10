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

package com.alibaba.nacos.core.remote.rsocket;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.RequestTypeConstants;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.rsocket.RsocketUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.remote.RpcServer;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.TcpServerTransport;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

/**
 * rpc server of rsocket.
 *
 * @author liuzunfei
 * @version $Id: RsocketRpcServer.java, v 0.1 2020年08月06日 11:52 AM liuzunfei Exp $
 */
@Service
public class RsocketRpcServer extends RpcServer {
    
    private static final int PORT_OFFSET = 1100;
    
    private RSocketServer rSocketServer;
    
    @Autowired
    private RequestHandlerRegistry requestHandlerRegistry;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Override
    public int rpcPortOffset() {
        return PORT_OFFSET;
    }
    
    @PostConstruct
    @Override
    public void start() throws Exception {
        RSocketServer rSocketServerInner = RSocketServer.create();
        rSocketServerInner.acceptor(((setup, sendingSocket) -> {
            Loggers.RPC.info("Receive connection rsocket:" + setup.getDataUtf8());
            RsocketUtils.PlainRequest palinrequest = null;
            try {
                palinrequest = RsocketUtils.parsePlainRequestFromPayload(setup);
            } catch (Exception e) {
                //Do Nothing
            }
            
            if (palinrequest == null || !RequestTypeConstants.CONNECTION_SETUP.equals(palinrequest.getType())) {
                Loggers.RPC.info("Illegal  set up payload:" + setup.getDataUtf8());
                sendingSocket.dispose();
                return Mono.just(sendingSocket);
            } else {
                ConnectionSetupRequest connectionSetupRequest = RsocketUtils
                        .toObj(palinrequest.getBody(), ConnectionSetupRequest.class);
                ConnectionMetaInfo metaInfo = new ConnectionMetaInfo(connectionSetupRequest.getConnectionId(),
                        connectionSetupRequest.getClientIp(), ConnectionType.RSOCKET.getType(),
                        connectionSetupRequest.getClientVersion());
                Connection connection = new RsocketConnection(metaInfo, sendingSocket);
                connectionManager.register(connection.getConnectionId(), connection);
                
                sendingSocket.onClose().subscribe(new Subscriber<Void>() {
                    String connectionId;
                    
                    @Override
                    public void onSubscribe(Subscription subscription) {
                        connectionId = connection.getConnectionId();
                    }
                    
                    @Override
                    public void onNext(Void aVoid) {
                    }
                    
                    @Override
                    public void onError(Throwable throwable) {
                        connectionManager.unregister(connectionId);
                    }
                    
                    @Override
                    public void onComplete() {
                        connectionManager.unregister(connectionId);
                    }
                });
                
                return Mono.just(new RSocket() {
                    @Override
                    public Mono<Payload> requestResponse(Payload payload) {
                        Loggers.RPC_DIGEST.info("Receive request :" + payload.getDataUtf8());
                        
                        RsocketUtils.PlainRequest requestType = RsocketUtils.parsePlainRequestFromPayload(payload);
                        
                        RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(requestType.getType());
                        if (requestHandler != null) {
                            Request request = requestHandler.parseBodyString(requestType.getBody());
                            
                            try {
                                Response response = requestHandler
                                        .handle(request, JacksonUtils.toObj(requestType.getMeta(), RequestMeta.class));
                                return Mono.just(RsocketUtils.convertResponseToPayload(response));
                                
                            } catch (NacosException e) {
                                return Mono.just(RsocketUtils.convertResponseToPayload(
                                        new PlainBodyResponse("exception:" + e.getMessage())));
                            }
                        }
                        return Mono.just(RsocketUtils.convertResponseToPayload(new PlainBodyResponse("No Handler")));
                    }
                });
            }
            
        })).bind(TcpServerTransport.create("0.0.0.0", (ApplicationUtils.getPort() + PORT_OFFSET))).block();
        
        rSocketServer = rSocketServerInner;
        Loggers.RPC.info("Nacos Rsocket server start on port :" + (ApplicationUtils.getPort() + PORT_OFFSET));
        
    }
    
    @Override
    public void stop() throws Exception {
    
    }
}
