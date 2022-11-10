package com.alibaba.nacos.plugin.control.connection.interceptor;

import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;

public interface ConnectionInterceptor {
    
    /**
     * get name.
     *
     * @return
     */
    String getName();
    
    /**
     * connection intercept.
     *
     * @param connectionCheckRequest connectionCheckRequest.
     * @return
     */
    InterceptResult preIntercept(ConnectionCheckRequest connectionCheckRequest);
    
    /**
     * connection intercept.
     *
     * @param connectionCheckRequest  connectionCheckRequest.
     * @param connectionCheckResponse connectionCheckResponse.
     * @return
     */
    InterceptResult postIntercept(ConnectionCheckRequest connectionCheckRequest,
            ConnectionCheckResponse connectionCheckResponse);
}
