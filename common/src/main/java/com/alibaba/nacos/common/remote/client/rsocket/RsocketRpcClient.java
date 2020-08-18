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

package com.alibaba.nacos.common.remote.client.rsocket;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerPushRequest;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.ServerPushResponse;
import com.alibaba.nacos.api.rsocket.RsocketUtils;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import io.rsocket.util.RSocketProxy;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * rsocket implementation of rpc client.
 *
 * @author liuzunfei
 * @version $Id: RsocketRpcClient.java, v 0.1 2020年08月06日 10:46 AM liuzunfei Exp $
 */
public class RsocketRpcClient extends RpcClient {
    
    static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.remote.client");
    
    private static final int RSOCKET_PORT_OFFSET = 1100;
    
    private AtomicReference<RSocket> rSocketClient = new AtomicReference<RSocket>();
    
    @Override
    public void shutdown() throws NacosException {
        shutDownRsocketClient(rSocketClient.get());
    }
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.RSOCKET;
    }
    
    @Override
    public int rpcPortOffset() {
        return RSOCKET_PORT_OFFSET;
    }
    
    @Override
    public Connection connectToServer(ServerInfo serverInfo) throws Exception {
        
        try {
            ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest(NetUtils.localIP(),
                    VersionUtils.getFullClientVersion(), labels);
            Payload setUpPayload = RsocketUtils.convertRequestToPayload(conconSetupRequest, buildMeta());
            RSocket rSocket = RSocketConnector.create().keepAlive(Duration.ofMillis(3000L), Duration.ofMillis(6000L))
                    .setupPayload(setUpPayload).acceptor(new SocketAcceptor() {
                        @Override
                        public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
    
                            RSocket rsocket = new RSocketProxy(sendingSocket) {
                                @Override
                                public Mono<Payload> requestResponse(Payload payload) {
                                    try {
                                        final ServerPushRequest request = RsocketUtils
                                                .parseServerRequestFromPayload(payload);
                                        try {
                                            handleServerRequest(request);
                                            ServerPushResponse response = new ServerPushResponse();
                                            response.setRequestId(request.getRequestId());
                                            return Mono.just(RsocketUtils.convertResponseToPayload(response));
                                        } catch (Exception e) {
                                            ServerPushResponse response = new ServerPushResponse();
                                            response.setResultCode(ResponseCode.FAIL.getCode());
                                            response.setMessage(e.getMessage());
                                            response.setRequestId(request.getRequestId());
                                            return Mono.just(RsocketUtils.convertResponseToPayload(response));
                                        }
    
                                    } catch (Exception e) {
                                        ServerPushResponse response = new ServerPushResponse();
                                        response.setResultCode(ResponseCode.FAIL.getCode());
                                        response.setMessage(e.getMessage());
                                        return Mono.just(DefaultPayload
                                                .create(RsocketUtils.convertResponseToPayload(response)));
                                    }
                                }
        
                                @Override
                                public Mono<Void> fireAndForget(Payload payload) {
                                    final ServerPushRequest request = RsocketUtils
                                            .parseServerRequestFromPayload(payload);
                                    handleServerRequest(request);
                                    return Mono.just(null);
                                }
                            };
    
                            return Mono.just((RSocket) rsocket);
                        }
                    }).connect(TcpClientTransport.create(serverInfo.getServerIp(), serverInfo.getServerPort())).block();
            RsocketConnection connection = new RsocketConnection(serverInfo, rSocket);
            fireOnCloseEvent(rSocket);
            return connection;
        } catch (Exception e) {
            throw e;
        }
    }
    
    private RequestMeta buildMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        return meta;
    }
    
    void shutDownRsocketClient(RSocket client) {
        if (client != null && !client.isDisposed()) {
            System.out.println(client);
            client.dispose();
        }
    }
    
    void cancelfireOnCloseEvent(RSocket rSocket) {
        
        if (rSocket != null) {
            System.out.println("Disposed subscribe..." + rSocket);
            rSocket.onClose().subscribe().dispose();
        }
    }
    
    @Override
    protected void clearContextOnResetRequest() {
        RsocketConnection rsocket = (RsocketConnection) currentConnetion;
        cancelfireOnCloseEvent(rsocket.getrSocketClient());
    }
    
    void fireOnCloseEvent(final RSocket rSocket) {
        
        Subscriber subscriber = new Subscriber<Void>() {
            @Override
            public void onSubscribe(Subscription subscription) {
            
            }
            
            @Override
            public void onNext(Void aVoid) {
            }
            
            @Override
            public void onError(Throwable throwable) {
    
                if (throwable.getMessage().equals("Disposed")) {
                    System.out.println("Disposed ignore current event" + rSocket);
                    return;
                }
                switchServerAsync();
            }
            
            @Override
            public void onComplete() {
                System.out.println("On complete ,switch server ..." + rSocket);
                switchServerAsync();
            }
        };
        rSocket.onClose().subscribe(subscriber);
        
        System.out.println("fire onclise  :" + rSocket);
    }
    
    class RsocketHolder {
        
        RSocket rsocket;
        
    }
    
}
