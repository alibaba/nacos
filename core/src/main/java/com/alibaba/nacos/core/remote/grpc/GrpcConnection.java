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

import com.alibaba.nacos.api.remote.request.ServerPushRequest;
import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.DefaultPushFuture;
import com.alibaba.nacos.core.remote.PushFuture;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

/**
 * grpc connection.
 *
 * @author liuzunfei
 * @version $Id: GrpcConnection.java, v 0.1 2020年07月13日 7:26 PM liuzunfei Exp $
 */
public class GrpcConnection extends Connection {
    
    private StreamObserver streamObserver;
    
    public GrpcConnection(ConnectionMetaInfo metaInfo, StreamObserver streamObserver) {
        super(metaInfo);
        this.streamObserver = streamObserver;
    }
    
    @Override
    public boolean heartBeatExpire() {
        return false;
    }
    
    @Override
    public boolean sendRequest(ServerPushRequest request, long timeoutMills) throws Exception {
        DefaultPushFuture pushFuture = (DefaultPushFuture) sendRequestWithFuture(request);
        try {
            return pushFuture.get(timeoutMills);
        } finally {
            RpcAckCallbackSynchronizer.clearFuture(getConnectionId(), pushFuture.getRequestId());
        }
    }
    
    @Override
    public void sendRequestNoAck(ServerPushRequest request) throws Exception {
        try {
            streamObserver.onNext(GrpcUtils.convert(request, ""));
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                throw new ConnectionAlreadyClosedException(e);
            }
            throw e;
        }
    }
    
    @Override
    public PushFuture sendRequestWithFuture(ServerPushRequest request) throws Exception {
        return sendRequestInner(request, null);
    }
    
    @Override
    public void sendRequestWithCallBack(ServerPushRequest request, PushCallBack callBack) throws Exception {
        sendRequestInner(request, callBack);
    }
    
    private DefaultPushFuture sendRequestInner(ServerPushRequest request, PushCallBack callBack) throws Exception {
        Loggers.RPC_DIGEST.info("Grpc sendRequest :" + request);
        String requestId = String.valueOf(PushAckIdGenerator.getNextId());
        request.setRequestId(requestId);
        sendRequestNoAck(request);
        DefaultPushFuture defaultPushFuture = new DefaultPushFuture(requestId, callBack);
        RpcAckCallbackSynchronizer.syncCallback(getConnectionId(), requestId, defaultPushFuture);
        return defaultPushFuture;
    }
    
    @Override
    public void closeGrapcefully() {
    }
    
}
