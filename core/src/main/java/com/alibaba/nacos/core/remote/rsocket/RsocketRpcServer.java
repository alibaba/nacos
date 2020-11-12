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
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.rsocket.RsocketUtils;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.remote.BaseRpcServer;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.utils.Loggers;
import io.rsocket.DuplexConnection;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.fragmentation.ReassemblyDuplexConnection;
import io.rsocket.transport.netty.TcpDuplexConnection;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.RSocketProxy;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
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
                Loggers.REMOTE.error(String
                        .format("[%s] error to parse new connection request :%s, error message: %s ", "rsocket",
                                setup.getDataUtf8(), e.getMessage(), e));
            }
            reactor.netty.Connection privateConnection = getPrivateConnection(sendingSocket);
            InetSocketAddress remoteAddress = (InetSocketAddress) privateConnection.channel().remoteAddress();
    
            InetSocketAddress localAddress = (InetSocketAddress) privateConnection.channel().localAddress();
            
            if (palinrequest == null || !(palinrequest.getBody() instanceof ConnectionSetupRequest)) {
                Loggers.REMOTE.info(String.format("[%s] invalid connection setup request, request info : %s", "rsocket",
                        palinrequest.toString()));
                sendingSocket.dispose();
                return Mono.just(sendingSocket);
            } else {
    
                String connectionid = UUID.randomUUID().toString();
    
                ConnectionSetupRequest connectionSetupRequest = (ConnectionSetupRequest) palinrequest.getBody();
                ConnectionMetaInfo metaInfo = new ConnectionMetaInfo(connectionid, remoteAddress.getHostName(),
                        remoteAddress.getPort(), localAddress.getPort(), ConnectionType.RSOCKET.getType(),
                        palinrequest.getMetadata().getClientVersion(), palinrequest.getMetadata().getLabels());
                Connection connection = new RsocketConnection(metaInfo, sendingSocket);
    
                if (connectionManager.isOverLimit()) {
                    //Not register to the connection manager if current server is over limit.
                    try {
                        connection.request(new ConnectResetRequest(), buildRequestMeta());
                        connection.close();
                    } catch (Exception e) {
                        //Do nothing.
                    }
        
                } else {
                    connectionManager.register(connection.getMetaInfo().getConnectionId(), connection);
                }
    
                fireOnCloseEvent(sendingSocket, connection);
                RSocketProxy rSocketProxy = new NacosRsocket(sendingSocket, connectionid,
                        remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort());
    
                return Mono.just(rSocketProxy);
            }
    
        })).bind(TcpServerTransport.create("0.0.0.0", getServicePort())).block();
    
        rSocketServer = rSocketServerInner;
    
    }
    
    private void fireOnCloseEvent(RSocket rSocket, Connection connection) {
        
        rSocket.onClose().subscribe(new Subscriber<Void>() {
            String connectionId;
            
            @Override
            public void onSubscribe(Subscription subscription) {
                connectionId = connection.getMetaInfo().getConnectionId();
            }
            
            @Override
            public void onNext(Void aVoid) {
            }
            
            @Override
            public void onError(Throwable throwable) {
    
                Loggers.REMOTE.error(String
                        .format("[%s] error on  connection, connection id : %s, error message :%s", "rsocket",
                                connectionId, throwable.getMessage(), throwable));
                connectionManager.unregister(connectionId);
            }
            
            @Override
            public void onComplete() {
    
                Loggers.REMOTE
                        .info(String.format("[%s]  connection finished ,connection id  %s", "rsocket", connectionId));
                connectionManager.unregister(connectionId);
            }
        });
    }
    
    private reactor.netty.Connection getPrivateConnection(RSocket rSocket) {
        try {
            DuplexConnection internalDuplexConnection = (DuplexConnection) ReflectUtils
                    .getFieldValue(rSocket, "connection");
            ReassemblyDuplexConnection source = (ReassemblyDuplexConnection) ReflectUtils
                    .getFieldValue(internalDuplexConnection, "source");
            TcpDuplexConnection tcpDuplexConnection = (TcpDuplexConnection) ReflectUtils
                    .getFieldValue(source, "delegate");
            return (reactor.netty.Connection) ReflectUtils.getFieldValue(tcpDuplexConnection, "connection");
        } catch (Exception e) {
            throw new IllegalStateException("Can't access connection details!", e);
        }
    }
    
    class NacosRsocket extends RSocketProxy {
        
        String connectionId;
    
        String clientIp;
    
        int clientPort;
        
        public NacosRsocket(RSocket source) {
            super(source);
        }
    
        public NacosRsocket(RSocket source, String connectionId, String clientIp, int clientPort) {
            super(source);
            this.connectionId = connectionId;
            this.clientIp = clientIp;
            this.clientPort = clientPort;
        }
        
        @Override
        public Mono<Payload> requestResponse(Payload payload) {
            try {
                RsocketUtils.PlainRequest requestType = RsocketUtils.parsePlainRequestFromPayload(payload);
                
                RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(requestType.getType());
                if (requestHandler != null) {
                    RequestMeta requestMeta = requestType.getMetadata();
                    requestMeta.setConnectionId(connectionId);
                    requestMeta.setClientIp(clientIp);
                    requestMeta.setClientPort(clientPort);
                    try {
    
                        Response response = requestHandler.handleRequest(requestType.getBody(), requestMeta);
                        return Mono.just(RsocketUtils.convertResponseToPayload(response));
    
                    } catch (NacosException e) {
                        Loggers.REMOTE_DIGEST.debug(String
                                .format("[%s] fail to handle request, error message : %s ", "rsocket", e.getMessage(),
                                        e));
                        return Mono.just(RsocketUtils
                                .convertResponseToPayload(new PlainBodyResponse("exception:" + e.getMessage())));
                    }
                }
    
                Loggers.REMOTE_DIGEST.debug(String
                        .format("[%s] no handler for request type : %s :", "rsocket", requestType.getType()));
                return Mono.just(RsocketUtils.convertResponseToPayload(new PlainBodyResponse("No Handler")));
            } catch (Exception e) {
                Loggers.REMOTE_DIGEST.debug(String
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
    
    private RequestMeta buildRequestMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        meta.setClientPort(getServicePort());
        return meta;
    }
}
