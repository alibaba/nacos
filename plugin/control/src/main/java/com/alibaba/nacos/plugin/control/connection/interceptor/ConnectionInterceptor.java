package com.alibaba.nacos.plugin.control.connection.interceptor;

import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;

public abstract class ConnectionInterceptor {
    
    
    boolean disabled;
    
    public boolean isDisabled() {
        return disabled;
    }
    
    public void setDisabled(boolean disabled) {
        Loggers.CONTROL.warn("ConnectionInterceptor {} disabled status is set to {}", this.getClass().getSimpleName(),
                disabled);
        this.disabled = disabled;
    }
    
    /**
     * get name.
     *
     * @return
     */
    public abstract String getName();
    
    /**
     * connection intercept.
     *
     * @param connectionCheckRequest connectionCheckRequest.
     * @return
     */
    public abstract InterceptResult preIntercept(ConnectionCheckRequest connectionCheckRequest);
    
    /**
     * connection intercept.
     *
     * @param connectionCheckRequest  connectionCheckRequest.
     * @param connectionCheckResponse connectionCheckResponse.
     * @return
     */
    public abstract InterceptResult postIntercept(ConnectionCheckRequest connectionCheckRequest,
            ConnectionCheckResponse connectionCheckResponse);
}
