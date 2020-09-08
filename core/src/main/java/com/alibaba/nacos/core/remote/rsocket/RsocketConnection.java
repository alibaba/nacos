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
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.RsocketUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.utils.Loggers;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * connection of rsocket.
 *
 * @author liuzunfei
 * @version $Id: RsocketConnection.java, v 0.1 2020年08月06日 11:58 AM liuzunfei Exp $
 */
public class RsocketConnection extends Connection {
    
    RSocket clientSocket;
    
    public RsocketConnection(ConnectionMetaInfo metaInfo, RSocket clientSocket) {
        super(metaInfo);
        this.clientSocket = clientSocket;
    }
    
    @Override
    public Response sendRequest(Request request, long timeoutMills) throws NacosException {
    
        Loggers.RPC_DIGEST.debug(String.format("[%s] send request  : %s", "rsocket", request));
        
        try {
            Mono<Payload> payloadMono = clientSocket
                    .requestResponse(RsocketUtils.convertRequestToPayload(request, buildMeta()));
            Payload block = payloadMono.block(Duration.ofMillis(timeoutMills));
            return RsocketUtils.parseResponseFromPayload(block);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public void sendRequestNoAck(Request request) throws NacosException {
        Loggers.RPC_DIGEST.debug(String.format("[%s] send no ack request  : %s", "rsocket", request));
        clientSocket.fireAndForget(RsocketUtils.convertRequestToPayload(request, buildMeta())).block();
    }
    
    private RequestMeta buildMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        return meta;
    }
    
    @Override
    public RequestFuture sendRequestWithFuture(Request request) throws NacosException {
        Loggers.RPC_DIGEST.debug(String.format("[%s] send future request  : %s", "rsocket", request));
        final Mono<Payload> payloadMono = clientSocket
                .requestResponse(RsocketUtils.convertRequestToPayload(request, buildMeta()));
        final CompletableFuture<Payload> payloadCompletableFuture = payloadMono.toFuture();
        
        RequestFuture defaultPushFuture = new RequestFuture() {
            
            @Override
            public boolean isDone() {
                return payloadCompletableFuture.isDone();
            }
            
            @Override
            public Response get() throws InterruptedException, ExecutionException {
                Payload block = payloadCompletableFuture.get();
                return RsocketUtils.parseResponseFromPayload(block);
            }
            
            @Override
            public Response get(long timeoutMills) throws TimeoutException, InterruptedException, ExecutionException {
                Payload block = payloadCompletableFuture.get(timeoutMills, TimeUnit.MILLISECONDS);
                return RsocketUtils.parseResponseFromPayload(block);
            }
        };
        return defaultPushFuture;
    }
    
    @Override
    public void sendRequestWithCallBack(Request request, RequestCallBack requestCallBack) throws NacosException {
    
        Loggers.RPC_DIGEST.debug(String.format("[%s] send callback request  : %s", "rsocket", request));
        
        try {
            Mono<Payload> response = clientSocket
                    .requestResponse(RsocketUtils.convertRequestToPayload(request, buildMeta()));
    
            response.toFuture().acceptEither(failAfter(requestCallBack.getTimeout()), new Consumer<Payload>() {
                @Override
                public void accept(Payload payload) {
                    requestCallBack.onResponse(RsocketUtils.parseResponseFromPayload(payload));
                }
            }).exceptionally(throwable -> {
                requestCallBack.onException(throwable);
                return null;
            });
    
        } catch (Exception e) {
            requestCallBack.onException(e);
        }
    }
    
    private static <T> CompletableFuture<T> failAfter(final long timeouts) {
        final CompletableFuture<T> promise = new CompletableFuture<T>();
        RpcScheduledExecutor.TIMEOUT_SHEDULER.schedule(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                final TimeoutException ex = new TimeoutException("Timeout after " + timeouts);
                return promise.completeExceptionally(ex);
            }
        }, timeouts, MILLISECONDS);
        return promise;
    }
    
    @Override
    public void closeGrapcefully() {
        if (clientSocket != null && !clientSocket.isDisposed()) {
            clientSocket.dispose();
        }
    }
}
