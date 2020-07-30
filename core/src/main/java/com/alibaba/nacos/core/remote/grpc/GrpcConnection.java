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

import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.api.remote.response.ServerPushResponse;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * grpc connection.
 *
 * @author liuzunfei
 * @version $Id: GrpcConnection.java, v 0.1 2020年07月13日 7:26 PM liuzunfei Exp $
 */
public class GrpcConnection extends Connection {
    
    static ThreadPoolExecutor pushWorkers = new ThreadPoolExecutor(10, 50, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(5000));
    
    private static final long MAX_TIMEOUTS = 500L;
    
    private StreamObserver streamObserver;
    
    public GrpcConnection(ConnectionMetaInfo metaInfo, StreamObserver streamObserver) {
        super(metaInfo);
        this.streamObserver = streamObserver;
    }
    
    @Override
    public boolean sendPush(ServerPushResponse request, long timeout) throws Exception {
        try {
            String requestId = String.valueOf(PushAckIdGenerator.getNextId());
            request.setRequestId(requestId);
            streamObserver.onNext(GrpcUtils.convert(request, requestId));
            try {
                GrpcAckSynchronizer.waitAck(requestId, timeout);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                GrpcAckSynchronizer.release(requestId);
            }
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                //return true where client is not active yet.
                return true;
            }
            throw e;
        }
        return false;
    }
    
    private void sendPushWithCallback(ServerPushResponse request, PushCallBack callBack) {
        try {
            String requestId = String.valueOf(PushAckIdGenerator.getNextId());
            request.setRequestId(requestId);
            streamObserver.onNext(GrpcUtils.convert(request, requestId));
            GrpcAckSynchronizer.syncCallbackOnAck(requestId, callBack);
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                //return true where client is not active yet.
                callBack.onSuccess();
            }
            callBack.onFail();
        }
    }
    
    @Override
    public boolean sendPushNoAck(ServerPushResponse request) throws Exception {
        try {
            streamObserver.onNext(GrpcUtils.convert(request, ""));
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                throw new ConnectionAlreadyClosedException(e);
            }
            throw e;
        }
        return false;
    }
    
    @Override
    public Future<Boolean> sendPushWithFuture(ServerPushResponse request) throws Exception {
        return pushWorkers.submit(new PushCallable(request, MAX_TIMEOUTS));
    }
    
    @Override
    public void sendPushCallBackWithCallBack(ServerPushResponse request, PushCallBack callBack) throws Exception {
        sendPushWithCallback(request, callBack);
    }
    
    @Override
    public void closeGrapcefully() {
    }
    
    class PushCallable implements Callable<Boolean> {
        
        private ServerPushResponse request;
        
        private long timeoutMills;
        
        public PushCallable(ServerPushResponse request, long timeoutMills) {
            this.request = request;
            this.timeoutMills = timeoutMills;
        }
        
        @Override
        public Boolean call() throws Exception {
            return sendPush(request, timeoutMills);
        }
    }
}
