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

import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.UnKnowResponse;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientStatus;
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

import java.util.Date;
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
    
    public RsocketRpcClient(String name) {
        super(name);
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
        RSocket rSocket = null;
        try {
            ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest();
            Payload setUpPayload = RsocketUtils.convertRequestToPayload(conconSetupRequest, buildMeta());
            rSocket = RSocketConnector.create().setupPayload(setUpPayload).acceptor(new SocketAcceptor() {
                @Override
                public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
    
                    RSocket rsocket = new RSocketProxy(sendingSocket) {
                        @Override
                        public Mono<Payload> requestResponse(Payload payload) {
                            try {
                                final RsocketUtils.PlainRequest plainRequest = RsocketUtils
                                        .parsePlainRequestFromPayload(payload);
                                try {
                                    Response response = handleServerRequest(plainRequest.getBody(),
                                            plainRequest.metadata);
                                    response.setRequestId(plainRequest.getBody().getRequestId());
                                    return Mono.just(RsocketUtils.convertResponseToPayload(response));
                                } catch (Exception e) {
                                    Response response = new UnKnowResponse();
                                    response.setResultCode(ResponseCode.FAIL.getCode());
                                    response.setMessage(e.getMessage());
                                    response.setRequestId(plainRequest.getBody().getRequestId());
                                    return Mono.just(RsocketUtils.convertResponseToPayload(response));
                                }
    
                            } catch (Exception e) {
                                UnKnowResponse response = new UnKnowResponse();
                                response.setResultCode(ResponseCode.FAIL.getCode());
                                response.setMessage(e.getMessage());
                                return Mono
                                        .just(DefaultPayload.create(RsocketUtils.convertResponseToPayload(response)));
                            }
                        }
        
                        @Override
                        public Mono<Void> fireAndForget(Payload payload) {
                            final RsocketUtils.PlainRequest plainRequest = RsocketUtils
                                    .parsePlainRequestFromPayload(payload);
                            handleServerRequest(plainRequest.getBody(), plainRequest.metadata);
                            return Mono.empty();
                        }
                    };
    
                    return Mono.just((RSocket) rsocket);
                }
            }).connect(TcpClientTransport.create(serverInfo.getServerIp(), serverInfo.getServerPort())).block();
            RsocketConnection connection = new RsocketConnection(serverInfo, rSocket);
            fireOnCloseEvent(rSocket, connection);
            return connection;
        } catch (Exception e) {
            shutDownRsocketClient(rSocket);
            throw e;
        }
    }
    
    void shutDownRsocketClient(RSocket client) {
        if (client != null && !client.isDisposed()) {
            client.dispose();
        }
    }
    
    void fireOnCloseEvent(final RSocket rSocket, final Connection connectionInner) {
        
        Subscriber subscriber = new Subscriber<Void>() {
            
            @Override
            public void onSubscribe(Subscription subscription) {
            
            }
            
            @Override
            public void onNext(Void aVoid) {
            }
            
            @Override
            public void onError(Throwable throwable) {
                if (isRunning() && !connectionInner.isAbandon()) {
                    System.out.println("onError ,switch server " + this + new Date().toString());
    
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                } else {
                    System.out.println(
                            "client is not running status ,ignore error event , " + this + new Date().toString());
    
                }
            }
            
            @Override
            public void onComplete() {
    
                if (isRunning() && !connectionInner.isAbandon()) {
                    System.out.println("onCompleted ,switch server " + this);
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                } else {
                    System.out.println(
                            "client is not running status ,ignore complete  event , " + this + new Date().toString());
        
                }
            }
        };
    
        rSocket.onClose().subscribe(subscriber);
    }
    
}
