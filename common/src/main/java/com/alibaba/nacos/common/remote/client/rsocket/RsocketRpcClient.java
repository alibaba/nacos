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
import com.alibaba.nacos.api.remote.response.ConnectionResetResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.rsocket.RsocketUtils;
import com.alibaba.nacos.api.utils.NetUtils;
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
    
    private RSocket rSocketClient;
    
    @Override
    public void shutdown() throws NacosException {
        shutDownRsocketClient(rSocketClient);
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
        
        rSocketClient = connectToServer(connectionId, nextRpcServer());
        
        super.registerServerPushResponseHandler(new ServerRequestHandler() {
            @Override
            public Response requestReply(Request request) {
                if (request instanceof ConnectResetRequest) {
                    try {
                        
                        if (isRunning()) {
                            switchServer();
                        }
                        return new ConnectionResetResponse();
                    } catch (Exception e) {
                        LOGGER.error("switch server  error ", e);
                    }
                }
                return null;
            }
            
        });
    }
    
    @Override
    public int rpcPortOffset() {
        return RSOCKET_PORT_OFFSET;
    }
    
    @Override
    public Response request(Request request) throws NacosException {
        System.out.println("Rsocket Client send rpc response:" + request);
        
        Payload response = rSocketClient.requestResponse(RsocketUtils.convertRequestToPayload(request)).block();
        System.out.println("Client get rpc response:" + response.getDataUtf8());
        return RsocketUtils.parseResponseFromPayload(response);
    }
    
    @Override
    public void asyncRequest(Request request, final FutureCallback<Response> callback) throws NacosException {
        try {
            Mono<Payload> response = rSocketClient.requestResponse(RsocketUtils.convertRequestToPayload(request));
            
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
                            
                            final RSocket depratedClient = rSocketClient;
                            //switch current channel and stub
                            rpcClientStatus.getAndSet(RpcClientStatus.RUNNING);
                            eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.CONNECTED));
                            shutDownRsocketClient(depratedClient);
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
    
    private RSocket connectToServer(String connId, ServerInfo serverInfo) {
        
        ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest(connId, NetUtils.localIP(),
                VersionUtils.getFullClientVersion());
        Payload setUpPayload = RsocketUtils.convertRequestToPayload(conconSetupRequest);
        System.out.println("setUpPayload：" + setUpPayload.getDataUtf8());
        
        return RSocketConnector.create().setupPayload(setUpPayload).acceptor(new SocketAcceptor() {
            @Override
            public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
                
                RSocket rsocket = new RSocketProxy(sendingSocket) {
                    @Override
                    public Mono<Payload> requestResponse(Payload payload) {
                        System.out.println("收到服务端推送：" + payload.getDataUtf8());
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
                                return Mono.just(DefaultPayload.create("Push Error,No Handler."));
                            }
                        } catch (Exception e) {
                            return Mono.just(DefaultPayload.create("Push Error."));
                        }
                    }
                };
                
                return Mono.just((RSocket) rsocket);
            }
        }).connect(TcpClientTransport.create(serverInfo.getServerIp(), 7001)).block();
    }
}
