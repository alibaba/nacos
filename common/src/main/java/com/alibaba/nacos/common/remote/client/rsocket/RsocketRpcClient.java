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
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ConnectionResetResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.UnKnowResponse;
import com.alibaba.nacos.api.rsocket.RsocketUtils;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientStatus;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.common.util.concurrent.FutureCallback;
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

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
    public void innerStart() throws NacosException {
        
        super.registerServerPushResponseHandler(new ServerRequestHandler() {
            @Override
            public Response requestReply(Request request) {
                if (request instanceof ConnectResetRequest) {
                    try {
                        if (isRunning()) {
                            switchServer();
                        }
                    } catch (Exception e) {
                        LOGGER.error("switch server  error ", e);
                    }
                    return new ConnectionResetResponse();
                }
                return null;
            }
        });
    
        try {
            RSocket rSocket = connectToServer(connectionId, nextRpcServer());
            if (rSocket != null) {
                rSocketClient.set(rSocket);
                fireOnClose(rSocketClient.get());
            } else {
                System.out.println("启东时连接server失败...");
                switchServer();
            }
        } catch (Exception e) {
            System.out.println("启东时连接server异常...");
        
        }
    }
    
    @Override
    public int rpcPortOffset() {
        return RSOCKET_PORT_OFFSET;
    }
    
    private RequestMeta buildMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        meta.setConnectionId(connectionId);
        return meta;
    }
    
    @Override
    public Response request(Request request) throws NacosException {
    
        Payload response = rSocketClient.get()
                .requestResponse(RsocketUtils.convertRequestToPayload(request, buildMeta())).block();
        return RsocketUtils.parseResponseFromPayload(response);
    }
    
    @Override
    public void asyncRequest(Request request, final FutureCallback<Response> callback) throws NacosException {
        try {
            Mono<Payload> response = rSocketClient.get()
                    .requestResponse(RsocketUtils.convertRequestToPayload(request, buildMeta()));
            
            response.subscribe(new Consumer<Payload>() {
                @Override
                public void accept(Payload payload) {
                    callback.onSuccess(RsocketUtils.parseResponseFromPayload(payload));
                }
            });
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }
    
    private final ReentrantLock switchingLock = new ReentrantLock();
    
    /**
     * switch a new server.
     */
    private void switchServer() {
        
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                
                try {
    
                    //only one thread can execute switching meantime.
                    boolean innerLock = switchingLock.tryLock();
                    if (!innerLock) {
                        return;
                    }
                    System.out.println(" 尝试重连服务端...");
                    
                    if (rpcClientStatus.get() == RpcClientStatus.RUNNING) {
                        eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.DISCONNECTED));
                    }
                    rpcClientStatus.set(RpcClientStatus.SWITCHING_SERVER);
                    // loop until start client success.
                    while (!isRunning()) {
                        
                        //1.get a new server
                        ServerInfo serverInfo = nextRpcServer();
                        //2.create a new channel to new server
                        RSocket rSocket = connectToServer(connectionId, serverInfo);
                        if (rSocket != null) {
    
                            final RSocket depratedClient = rSocketClient.get();
                            //switch current channel and stub
                            rpcClientStatus.getAndSet(RpcClientStatus.RUNNING);
                            eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.CONNECTED));
                            shutDownRsocketClient(depratedClient);
                            rSocketClient.set(rSocket);
                            fireOnClose(rSocketClient.get());
                            continue;
                        }
                        //
                        try {
                            //sleep 3 second to switch next server.
                            Thread.sleep(3000L);
                        } catch (InterruptedException e) {
                            // Do  nothing.
                        }
                    }
                } finally {
                    switchingLock.unlock();
                }
            }
        }, 0L, TimeUnit.MILLISECONDS);
        
    }
    
    void shutDownRsocketClient(RSocket client) {
        if (client != null && !client.isDisposed()) {
            client.dispose();
        }
    }
    
    /**
     * connectToServer ,set public for junit temp.
     *
     * @param connId.
     * @param serverInfo.
     * @return
     */
    public RSocket connectToServer(String connId, ServerInfo serverInfo) {
    
        try {
        
            ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest(connId, NetUtils.localIP(),
                    VersionUtils.getFullClientVersion());
            Payload setUpPayload = RsocketUtils.convertRequestToPayload(conconSetupRequest, buildMeta());
        
            RSocket rSocket = RSocketConnector.create().setupPayload(setUpPayload).acceptor(new SocketAcceptor() {
                @Override
                public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
                
                    RSocket rsocket = new RSocketProxy(sendingSocket) {
                        @Override
                        public Mono<Payload> requestResponse(Payload payload) {
                            System.out.println("收到服务端RPC：" + payload.getDataUtf8());
                            final AtomicReference<Response> response = new AtomicReference<Response>();
                            try {
                                final Request request = RsocketUtils.parseServerRequestFromPayload(payload);
                                serverRequestHandlers.forEach(new Consumer<ServerRequestHandler>() {
                                    @Override
                                    public void accept(ServerRequestHandler serverRequestHandler) {
                                        Response responseInner = serverRequestHandler.requestReply(request);
                                        if (responseInner != null) {
                                            response.set(responseInner);
                                            return;
                                        }
                                    }
                                });
                            
                                if (response.get() != null) {
                                    return Mono.just(RsocketUtils.convertResponseToPayload(response.get()));
                                } else {
                                    UnKnowResponse unKnowResponse = new UnKnowResponse();
                                    unKnowResponse.setResultCode(ResponseCode.FAIL.getCode());
                                    unKnowResponse.setMessage("No handlers.");
                                    return Mono.just(RsocketUtils.convertResponseToPayload(unKnowResponse));
                                }
                            } catch (Exception e) {
                                UnKnowResponse unKnowResponse = new UnKnowResponse();
                                unKnowResponse.setResultCode(ResponseCode.FAIL.getCode());
                                unKnowResponse.setMessage(e.getMessage());
                                return Mono.just(DefaultPayload
                                        .create(RsocketUtils.convertResponseToPayload(unKnowResponse)));
                            }
                        }
                    
                        @Override
                        public Mono<Void> fireAndForget(Payload payload) {
                            System.out.println("收到服务端fireAndForget：" + payload.getDataUtf8());
                        
                            final Request request = RsocketUtils.parseServerRequestFromPayload(payload);
                            serverRequestHandlers.forEach(new Consumer<ServerRequestHandler>() {
                                @Override
                                public void accept(ServerRequestHandler serverRequestHandler) {
                                    serverRequestHandler.requestReply(request);
                                }
                            });
                            return Mono.just(null);
                        }
                    };
                
                    return Mono.just((RSocket) rsocket);
                }
            }).connect(TcpClientTransport.create(serverInfo.getServerIp(), serverInfo.getServerPort())).block();
            System.out.println("连接服务端成功，client ：" + rSocket);
            return rSocket;
        } catch (Exception e) {
            System.out.println("连接服务端失败，" + serverInfo);
        }
        return null;
    }
    
    void fireOnClose(RSocket rSocket) {
        System.out.println("fireOnClose...");
        rSocket.onClose().subscribe(new Subscriber<Void>() {
            @Override
            public void onSubscribe(Subscription subscription) {
            
            }
            
            @Override
            public void onNext(Void aVoid) {
            
            }
            
            @Override
            public void onError(Throwable throwable) {
                System.out.println("On error ,switch server ...");
                switchServer();
            }
            
            @Override
            public void onComplete() {
                System.out.println("On complete ,switch server ...");
                
                switchServer();
            }
        });
    }
    
    
}
