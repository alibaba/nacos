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
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.rsocket.RsocketUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

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
    public boolean heartBeatExpire() {
        return false;
    }
    
    @Override
    public boolean sendRequest(Request request, long timeoutMills) throws Exception {
        Mono<Payload> payloadMono = clientSocket
                .requestResponse(RsocketUtils.convertRequestToPayload(request, new RequestMeta()));
        Payload block = payloadMono.block(Duration.ofMillis(timeoutMills));
        return block == null;
    }
    
    @Override
    public void sendRequestNoAck(Request request) throws Exception {
        clientSocket.fireAndForget(RsocketUtils.convertRequestToPayload(request, new RequestMeta())).block();
    }
    
    @Override
    public Future<Boolean> sendRequestWithFuture(Request request) throws Exception {
        final Mono<Payload> payloadMono = clientSocket
                .requestResponse(RsocketUtils.convertRequestToPayload(request, new RequestMeta()));
        Future<Boolean> future = new Future<Boolean>() {
            
            private volatile boolean cancel = false;
            
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return cancel = true;
            }
            
            @Override
            public boolean isCancelled() {
                return cancel;
            }
            
            @Override
            public boolean isDone() {
                return payloadMono.take(Duration.ofMillis(0L)) == null;
            }
            
            @Override
            public Boolean get() throws InterruptedException, ExecutionException {
                return payloadMono.block() == null;
            }
            
            @Override
            public Boolean get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                
                return payloadMono.block(Duration.ofMillis(unit.toMillis(timeout))) == null;
            }
            
        };
        return future;
    }
    
    @Override
    public void sendRequestWithCallBack(Request request, PushCallBack callBack) throws Exception {
        Mono<Payload> payloadMono = clientSocket
                .requestResponse(RsocketUtils.convertRequestToPayload(request, new RequestMeta()));
        payloadMono.subscribe(new Consumer<Payload>() {
            @Override
            public void accept(Payload payload) {
                Response response = RsocketUtils.parseResponseFromPayload(payload);
                if (response.isSuccess()) {
                    callBack.onSuccess();
                } else {
                    callBack.onFail(new NacosException(response.getErrorCode(), "request fail"));
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                callBack.onFail(new Exception(throwable));
            }
        });
    }
    
    @Override
    public void closeGrapcefully() {
        if (clientSocket != null && !clientSocket.isDisposed()) {
            clientSocket.dispose();
        }
    }
}
