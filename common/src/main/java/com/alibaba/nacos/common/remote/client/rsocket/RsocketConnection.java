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
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
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
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * rsocket connection.
 *
 * @author liuzunfei
 * @version $Id: RsocketConnection.java, v 0.1 2020年08月09日 2:57 PM liuzunfei Exp $
 */
public class RsocketConnection extends Connection {
    
    private RSocket rSocketClient;
    
    public RsocketConnection(RpcClient.ServerInfo serverInfo, RSocket rSocketClient) {
        super(serverInfo);
        this.rSocketClient = rSocketClient;
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta) throws NacosException {
        return request(request, requestMeta, 3000L);
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta, long timeouts) throws NacosException {
        Payload response = rSocketClient.requestResponse(RsocketUtils.convertRequestToPayload(request, requestMeta))
                .block(Duration.ofMillis(timeouts));
        return RsocketUtils.parseResponseFromPayload(response);
    }
    
    @Override
    public RequestFuture requestFuture(Request request, RequestMeta requestMeta) throws NacosException {
        final Mono<Payload> response = rSocketClient
                .requestResponse(RsocketUtils.convertRequestToPayload(request, requestMeta));
        final CompletableFuture<Payload> payloadCompletableFuture = response.toFuture();
        return new RequestFuture() {
            
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
            public Response get(long timeout) throws TimeoutException, InterruptedException, ExecutionException {
                Payload block = payloadCompletableFuture.get(timeout, TimeUnit.MILLISECONDS);
                return RsocketUtils.parseResponseFromPayload(block);
            }
        };
    }
    
    @Override
    public void asyncRequest(Request request, RequestMeta requestMeta, final RequestCallBack requestCallBack)
            throws NacosException {
        try {
            Mono<Payload> response = rSocketClient
                    .requestResponse(RsocketUtils.convertRequestToPayload(request, requestMeta));
            CompletableFuture<Payload> payloadCompletableFuture = response.toFuture();
            payloadCompletableFuture.acceptEither(RsocketConnection.<Payload>failAfter(requestCallBack.getTimeout()),
                    new Consumer<Payload>() {
                        @Override
                        public void accept(Payload payload) {
                            requestCallBack.onResponse(RsocketUtils.parseResponseFromPayload(payload));
                        }
                    });
            payloadCompletableFuture.exceptionally(new Function<Throwable, Payload>() {
                @Override
                public Payload apply(Throwable throwable) {
                    requestCallBack.onException(throwable);
                    return null;
                }
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
    public void close() {
        if (this.rSocketClient != null && !rSocketClient.isDisposed()) {
            rSocketClient.dispose();
        }
    }
    
    /**
     * Getter method for property <tt>rSocketClient</tt>.
     *
     * @return property value of rSocketClient
     */
    public RSocket getrSocketClient() {
        return rSocketClient;
    }
    
    @Override
    public String toString() {
        return "RsocketConnection{" + "serverInfo=" + serverInfo + ", labels=" + labels + '}';
    }
}
