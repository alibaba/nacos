package com.alibaba.nacos.auth.context;

import com.alibaba.nacos.api.remote.request.Request;

import java.util.Map;

public class GrpcRequestContextBuilder implements RequestContextBuilder<Request> {
    
    /**
     * get auth context from grpc.
     * @param request grpc request
     * @return AuthContext request context
     */
    @Override
    public RequestContext build(Request request) {
        RequestContext authContext = new RequestContext();
        Map<String, String> map = request.getHeaders();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            authContext.setParameter(entry.getKey(), entry.getValue());
        }
        return authContext;
    }
}