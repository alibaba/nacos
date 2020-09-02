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
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.RsocketUtils;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.common.util.concurrent.FutureCallback;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

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
    public Response request(Request request) throws NacosException {
        Payload response = rSocketClient.requestResponse(RsocketUtils.convertRequestToPayload(request, buildMeta()))
                .block();
        return RsocketUtils.parseResponseFromPayload(response);
    }
    
    @Override
    public void asyncRequest(Request request, final FutureCallback<Response> callback) throws NacosException {
        try {
            Mono<Payload> response = rSocketClient
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
    
    private RequestMeta buildMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        return meta;
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
