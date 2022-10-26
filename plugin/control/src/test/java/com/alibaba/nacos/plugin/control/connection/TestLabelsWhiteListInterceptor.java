package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.plugin.control.connection.interceptor.ConnectionInterceptor;
import com.alibaba.nacos.plugin.control.connection.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;

public class TestLabelsWhiteListInterceptor implements ConnectionInterceptor {
    
    @Override
    public String getName() {
        return "testlabelwhitelist";
    }
    
    @Override
    public InterceptResult intercept(ConnectionCheckRequest connectionCheckRequest) {
        
        if (connectionCheckRequest.getLabels() != null && "Y"
                .equalsIgnoreCase(connectionCheckRequest.getLabels().get("nolimitlabel")) && connectionCheckRequest
                .getSource().equalsIgnoreCase("cluster")) {
            return InterceptResult.CHECK_PASS;
        } else {
            return InterceptResult.CHECK_SKIP;
        }
    }
}
