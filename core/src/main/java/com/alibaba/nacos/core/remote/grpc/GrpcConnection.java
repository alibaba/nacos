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

import com.alibaba.nacos.api.remote.connection.Connection;
import com.alibaba.nacos.api.remote.connection.ConnectionMetaInfo;
import com.alibaba.nacos.api.remote.response.Response;

import io.grpc.stub.StreamObserver;

/**
 * grpc connection.
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
    public void sendResponse(Response reponse) {
        streamObserver.onNext(GrpcUtils.convert(reponse));
    }
    
    @Override
    public void closeGrapcefully() {
        //Empty implements
    }
}
