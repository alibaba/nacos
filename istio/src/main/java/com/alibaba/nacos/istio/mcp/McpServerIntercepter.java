/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.istio.misc.Loggers;
import io.grpc.*;
import org.springframework.stereotype.Service;

import java.net.SocketAddress;

/**
 * Interceptor for MCP server
 *
 * @author nkorange
 * @since 1.1.4
 */
@Service
public class McpServerIntercepter implements ServerInterceptor {

    private static final String INTERCEPTE_METHOD_NAME = "EstablishResourceStream";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        String methodName = call.getMethodDescriptor().getFullMethodName();

        Loggers.MAIN.info("remote address: {}, method: {}", address, methodName);

        return next.startCall(call, headers);
    }
}
