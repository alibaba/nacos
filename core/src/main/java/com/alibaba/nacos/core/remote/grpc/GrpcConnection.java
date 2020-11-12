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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.remote.DefaultRequestFuture;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.stub.StreamObserver;

import java.util.Map;

/**
 * grpc connection.
 *
 * @author liuzunfei
 * @version $Id: GrpcConnection.java, v 0.1 2020年07月13日 7:26 PM liuzunfei Exp $
 */
public class GrpcConnection extends Connection {
    
    private StreamObserver streamObserver;
    
    private Channel channel;
    
    public GrpcConnection(ConnectionMetaInfo metaInfo, StreamObserver streamObserver, Channel channel) {
        super(metaInfo);
        this.streamObserver = streamObserver;
        this.channel = channel;
    }
    
    private void sendRequestNoAck(Request request, RequestMeta meta) throws NacosException {
        try {
            streamObserver.onNext(GrpcUtils.convert(request, wrapMeta(meta)));
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                throw new ConnectionAlreadyClosedException(e);
            }
            throw e;
        }
    }
    
    private RequestMeta wrapMeta(RequestMeta meta) {
        if (meta == null) {
            meta = new RequestMeta();
        }
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setConnectionId(getMetaInfo().getConnectionId());
        meta.setClientPort(getMetaInfo().getLocalPort());
        meta.setClientIp(NetUtils.localIP());
        return meta;
    }
    
    Metadata buildMeta(String type) {
        Metadata meta = Metadata.newBuilder().setClientIp(NetUtils.localIP()).setType(type)
                .setClientVersion(VersionUtils.getFullClientVersion()).build();
        return meta;
    }
    
    private DefaultRequestFuture sendRequestInner(Request request, RequestMeta meta, RequestCallBack callBack)
            throws NacosException {
        String requestId = String.valueOf(PushAckIdGenerator.getNextId());
        request.setRequestId(requestId);
        sendRequestNoAck(request, meta);
        DefaultRequestFuture defaultPushFuture = new DefaultRequestFuture(getMetaInfo().getConnectionId(), requestId,
                callBack,
                new DefaultRequestFuture.TimeoutInnerTrigger() {
                    @Override
                    public void triggerOnTimeout() {
                        RpcAckCallbackSynchronizer.clearFuture(getMetaInfo().getConnectionId(), requestId);
                    }
                });
        RpcAckCallbackSynchronizer.syncCallback(getMetaInfo().getConnectionId(), requestId, defaultPushFuture);
        return defaultPushFuture;
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta) throws NacosException {
        return request(request, requestMeta, 3000L);
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta, long timeoutMills) throws NacosException {
        DefaultRequestFuture pushFuture = (DefaultRequestFuture) sendRequestInner(request, requestMeta, null);
        try {
            return pushFuture.get(timeoutMills);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        } finally {
            RpcAckCallbackSynchronizer.clearFuture(getMetaInfo().getConnectionId(), pushFuture.getRequestId());
        }
    }
    
    @Override
    public RequestFuture requestFuture(Request request, RequestMeta requestMeta) throws NacosException {
        return sendRequestInner(request, requestMeta, null);
    }
    
    @Override
    public void asyncRequest(Request request, RequestMeta requestMeta, RequestCallBack requestCallBack)
            throws NacosException {
        sendRequestInner(request, requestMeta, requestCallBack);
    }
    
    @Override
    public Map<String, String> getLabels() {
        return null;
    }
    
    @Override
    public void close() {
        try {
            streamObserver.onCompleted();
        } catch (Exception e) {
            Loggers.REMOTE.debug(String.format("[%s] connection close exception  : %s", "grpc", e.getMessage()));
        }
    }
    
    @Override
    public boolean isConnected() {
        return channel.isActive();
    }
}
