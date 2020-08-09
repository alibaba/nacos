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
import com.alibaba.nacos.core.utils.Loggers;
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
            new LinkedBlockingQueue<>(50000));
    
    private static final long MAX_TIMEOUTS = 5000L;
    
    private StreamObserver streamObserver;
    
    public GrpcConnection(ConnectionMetaInfo metaInfo, StreamObserver streamObserver) {
        super(metaInfo);
        this.streamObserver = streamObserver;
    }
    
    @Override
    public boolean heartBeatExpire() {
        return true;
    }
    
    @Override
    public boolean sendRequest(ServerPushRequest request, long timeout) throws Exception {
        try {
    
            Loggers.RPC_DIGEST.info("Grpc sendRequest :" + request);
    
            String requestId = String.valueOf(PushAckIdGenerator.getNextId());
            request.setRequestId(requestId);
            streamObserver.onNext(GrpcUtils.convert(request, requestId));
            try {
                return GrpcAckSynchronizer.waitAck(requestId, timeout);
            } catch (Exception e) {
                //Do nothing，return fail.
                return false;
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
    }
    
    private void sendRequestWithCallback(ServerPushRequest request, PushCallBack callBack) {
        try {
            Loggers.RPC_DIGEST.info("Grpc sendRequestWithCallback :" + request);
    
            String requestId = String.valueOf(PushAckIdGenerator.getNextId());
            request.setRequestId(requestId);
            streamObserver.onNext(GrpcUtils.convert(request, requestId));
            GrpcAckSynchronizer.syncCallbackOnAck(requestId, callBack);
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                //return true where client is not active yet.
                callBack.onSuccess();
                return;
            }
            callBack.onFail(e);
        }
    }
    
    @Override
    public void sendRequestNoAck(ServerPushRequest request) throws Exception {
        try {
    
            Loggers.RPC_DIGEST.info("Grpc sendRequestNoAck :" + request);
            streamObserver.onNext(GrpcUtils.convert(request, ""));
        } catch (Exception e) {
            if (e instanceof StatusRuntimeException) {
                throw new ConnectionAlreadyClosedException(e);
            }
            throw e;
        }
    }
    
    @Override
    public Future<Boolean> sendRequestWithFuture(ServerPushRequest request) throws Exception {
        Loggers.RPC_DIGEST.info("Grpc sendRequestWithFuture :" + request);
        return pushWorkers.submit(new PushCallable(request, MAX_TIMEOUTS));
    }
    
    @Override
    public void sendRequestWithCallBack(ServerPushRequest request, PushCallBack callBack) throws Exception {
        Loggers.RPC_DIGEST.info("Grpc sendRequestWithCallBack :" + request);
        sendRequestWithCallback(request, callBack);
    }
    
    @Override
    public void closeGrapcefully() {
    }
    
    class PushCallable implements Callable<Boolean> {
    
        private ServerPushRequest request;
        
        private long timeoutMills;
    
        public PushCallable(ServerPushRequest request, long timeoutMills) {
            this.request = request;
            this.timeoutMills = timeoutMills;
        }
        
        @Override
        public Boolean call() throws Exception {
            return sendRequest(request, timeoutMills);
        }
    }
    
}
