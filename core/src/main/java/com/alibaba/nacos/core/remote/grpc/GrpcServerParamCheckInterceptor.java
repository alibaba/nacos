/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;
import com.alibaba.nacos.core.paramcheck.RpcParamExtractorManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Grpc server interceptor for param check.
 *
 * @author zhuoguang
 */
public class GrpcServerParamCheckInterceptor implements ServerInterceptor {
    
    @Override
    public <T, S> ServerCall.Listener<T> interceptCall(ServerCall<T, S> call, Metadata headers,
                                                       ServerCallHandler<T, S> next) {
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<T>(next.startCall(call, headers)) {
            @Override
            public void onMessage(T message) {
                boolean ifParamCheck = EnvUtil.getProperty("nacos.paramcheck", Boolean.class, true);
                if (!ifParamCheck) {
                    super.onMessage(message);
                    return;
                }
                Payload payload = (Payload) message;
                String type = payload.getMetadata().getType();
                Object parseObj;
                try {
                    parseObj = GrpcUtils.parse(payload);
                    if (parseObj instanceof Request) {
                        Request request = (Request) parseObj;
                        RpcParamExtractorManager extractorManager = RpcParamExtractorManager.getInstance();
                        AbstractRpcParamExtractor extractor = extractorManager.getExtractor(type);
                        extractor.extractParamAndCheck(request);
                    }
                    super.onMessage(message);
                } catch (Exception e) {
                    call.close(Status.INVALID_ARGUMENT.withDescription(e.getMessage()), headers);
                }
                
            }
        };
    }
}
