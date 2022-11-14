package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;

public class TestLabelsWhiteListInterceptor extends ConnectionInterceptor {
    
    @Override
    public String getName() {
        return "testlabelwhitelist";
    }
    
    @Override
    public InterceptResult preIntercept(ConnectionCheckRequest connectionCheckRequest) {
        
        if (connectionCheckRequest.getLabels() != null && "Y"
                .equalsIgnoreCase(connectionCheckRequest.getLabels().get("nolimitlabel")) && connectionCheckRequest
                .getSource().equalsIgnoreCase("cluster")) {
            return InterceptResult.CHECK_PASS;
        } else {
            return InterceptResult.CHECK_SKIP;
        }
    }
    
    @Override
    public InterceptResult postIntercept(ConnectionCheckRequest connectionCheckRequest,
            ConnectionCheckResponse connectionCheckResponse) {
        if (!connectionCheckResponse.isSuccess() && connectionCheckRequest.getLabels() != null && "Y"
                .equalsIgnoreCase(connectionCheckRequest.getLabels().get("overturned"))) {
            return InterceptResult.CHECK_PASS;
        }
        return null;
    }
}
