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
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.RsocketUtils;
import com.alibaba.nacos.core.remote.BaseRpcServer;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.RSocketProxy;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * rpc server of rsocket.
 *
 * @author liuzunfei
 * @version $Id: RsocketRpcServer.java, v 0.1 2020年08月06日 11:52 AM liuzunfei Exp $
 */
@Service
public class RsocketRpcServer extends BaseRpcServer {
    
    private static final int PORT_OFFSET = 1100;
    
    private RSocketServer rSocketServer;
    
    CloseableChannel closeChannel;
    
    @Autowired
    private RequestHandlerRegistry requestHandlerRegistry;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Override
    public int rpcPortOffset() {
        return PORT_OFFSET;
    }
    
    @Override
    public void startServer() throws Exception {
        RSocketServer rSocketServerInner = RSocketServer.create();
        closeChannel = rSocketServerInner.acceptor(((setup, sendingSocket) -> {
            RsocketUtils.PlainRequest palinrequest = null;
            try {
                palinrequest = RsocketUtils.parsePlainRequestFromPayload(setup);
            } catch (Exception e) {
                Loggers.RPC.error(String
                        .format("[%s] error to parse new connection request :%s, error message: %s ", "rsocket",
                                setup.getDataUtf8(), e.getMessage(), e));
            }
    
            if (palinrequest == null || !(palinrequest.getBody() instanceof ConnectionSetupRequest)) {
                Loggers.RPC.info(String.format("[%s] invalid connection setup request, request info : %s", "rsocket",
                        palinrequest.toString()));
                sendingSocket.dispose();
                return Mono.just(sendingSocket);
            } else {
        
                String connectionid = UUID.randomUUID().toString();
                Loggers.RPC.info(String
                        .format("[%s] new connection receive, connection id : %s, clientMeta :%s", "rsocket",
                                connectionid, palinrequest.getMetadata()));
        
                ConnectionSetupRequest connectionSetupRequest = (ConnectionSetupRequest) palinrequest.getBody();
                ConnectionMetaInfo metaInfo = new ConnectionMetaInfo(connectionid,
                        palinrequest.getMetadata().getClientIp(), ConnectionType.RSOCKET.getType(),
                        palinrequest.getMetadata().getClientVersion(), palinrequest.getMetadata().getLabels());
                Connection connection = new RsocketConnection(metaInfo, sendingSocket);
        
                if (connectionManager.isOverLimit()) {
                    //Not register to the connection manager if current server is over limit.
                    try {
                        connection.sendRequestNoAck(new ConnectResetRequest());
                        connection.closeGrapcefully();
                    } catch (Exception e) {
                        //Do nothing.
                    }
            
                } else {
                    connectionManager.register(connection.getConnectionId(), connection);
                }
                
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
    
                        Loggers.RPC.error(String
                                .format("[%s] error on  connection, connection id : %s, error message :%s", "rsocket",
                                        connectionid, throwable.getMessage(), throwable));
                        throwable.printStackTrace();
                        connectionManager.unregister(connectionId);
                    }
                    
                    @Override
                    public void onComplete() {
    
                        Loggers.RPC.info(String
                                .format("[%s]  connection finished ,connection id  %s", "rsocket", connectionid));
                        connectionManager.unregister(connectionId);
                    }
                });
    
                RSocketProxy rSocketProxy = new NacosRsocket(sendingSocket, connectionid);
        
                return Mono.just(rSocketProxy);
            }
    
        })).bind(TcpServerTransport.create("0.0.0.0", (ApplicationUtils.getPort() + PORT_OFFSET))).block();
    
        rSocketServer = rSocketServerInner;
    
    }
    
    class NacosRsocket extends RSocketProxy {
        
        String connectionId;
        
        public NacosRsocket(RSocket source) {
            super(source);
        }
        
        public NacosRsocket(RSocket source, String connectionId) {
            super(source);
            this.connectionId = connectionId;
        }
        
        @Override
        public Mono<Payload> requestResponse(Payload payload) {
            try {
                RsocketUtils.PlainRequest requestType = RsocketUtils.parsePlainRequestFromPayload(payload);
                Loggers.RPC_DIGEST.debug(String.format("[%s] request receive : %s", "rsocket", requestType.toString()));
    
                RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(requestType.getType());
                if (requestHandler != null) {
                    RequestMeta requestMeta = requestType.getMetadata();
                    requestMeta.setConnectionId(connectionId);
                    try {
                        Response response = requestHandler.handle(requestType.getBody(), requestMeta);
                        return Mono.just(RsocketUtils.convertResponseToPayload(response));
    
                    } catch (NacosException e) {
                        Loggers.RPC_DIGEST.debug(String
                                .format("[%s] fail to handle request, error message : %s ", "rsocket", e.getMessage(),
                                        e));
                        return Mono.just(RsocketUtils
                                .convertResponseToPayload(new PlainBodyResponse("exception:" + e.getMessage())));
                    }
                }
    
                Loggers.RPC_DIGEST.debug(String
                        .format("[%s] no handler for request type : %s :", "rsocket", requestType.getType()));
                return Mono.just(RsocketUtils.convertResponseToPayload(new PlainBodyResponse("No Handler")));
            } catch (Exception e) {
                Loggers.RPC_DIGEST.debug(String
                        .format("[%s] fail to parse request, error message : %s ", "rsocket", e.getMessage(), e));
                return Mono.just(RsocketUtils
                        .convertResponseToPayload(new PlainBodyResponse("exception:" + e.getMessage())));
            }
            
        }
    }
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.RSOCKET;
    }
    
    @Override
    public void shundownServer() {
        if (this.closeChannel != null && !closeChannel.isDisposed()) {
            this.closeChannel.dispose();
        }
    }
}
