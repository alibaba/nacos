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
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.GrpcUtils;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.stub.StreamObserver;

/**
 * grpc connection.
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
    
    @Override
    public Response sendRequest(Request request, long timeoutMills) throws NacosException {
        DefaultRequestFuture pushFuture = (DefaultRequestFuture) sendRequestWithFuture(request);
        try {
            return pushFuture.get(timeoutMills);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        } finally {
            RpcAckCallbackSynchronizer.clearFuture(getConnectionId(), pushFuture.getRequestId());
        }
    }
    
    @Override
    public void sendRequestNoAck(Request request) throws NacosException {
        try {
            streamObserver.onNext(GrpcUtils.convert(request, buildMeta()));
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                throw new ConnectionAlreadyClosedException(e);
            }
            throw e;
        }
    }
    
    Metadata buildMeta() {
        Metadata meta = Metadata.newBuilder().setClientIp(NetUtils.localIP())
                .setVersion(VersionUtils.getFullClientVersion()).build();
        return meta;
    }
    
    @Override
    public RequestFuture sendRequestWithFuture(Request request) throws NacosException {
        return sendRequestInner(request, null);
    }
    
    @Override
    public void sendRequestWithCallBack(Request request, RequestCallBack callBack) throws NacosException {
        sendRequestInner(request, callBack);
    }
    
    private DefaultRequestFuture sendRequestInner(Request request, RequestCallBack callBack) throws NacosException {
        Loggers.RPC_DIGEST.info("Grpc sendRequest :" + request);
        String requestId = String.valueOf(PushAckIdGenerator.getNextId());
        request.setRequestId(requestId);
        sendRequestNoAck(request);
        DefaultRequestFuture defaultPushFuture = new DefaultRequestFuture(requestId, callBack);
        RpcAckCallbackSynchronizer.syncCallback(getConnectionId(), requestId, defaultPushFuture);
        return defaultPushFuture;
    }
    
    @Override
    public void closeGrapcefully() {
        try {
            streamObserver.onCompleted();
        } catch (Exception e) {
            Loggers.RPC_DIGEST.warn("Grpc connection close exception .", e);
        }
    }
    
}
