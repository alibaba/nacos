package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.istio.misc.Loggers;
import io.grpc.*;
import org.springframework.stereotype.Service;

import java.net.SocketAddress;


@Service
public class McpServerIntercepter implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {

        SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        String methodName = call.getMethodDescriptor().getFullMethodName();

        Loggers.MAIN.info("address: {}, method: {}", address, methodName);

        return next.startCall(call, headers);
    }
}
