package com.alibaba.nacos.plugin.control.connection.interceptor;

import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;

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
    InterceptResult intercept(ConnectionCheckRequest connectionCheckRequest);
}
