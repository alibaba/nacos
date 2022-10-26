package com.alibaba.nacos.plugin.control.tps.interceptor;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

public interface TpsInterceptor {
    
    /**
     * get interceptor name.
     *
     * @return
     */
    String getName();
    
    /**
     * tps intercept method.
     *
     * @param tpsCheckRequest
     * @return
     */
    InterceptResult intercept(TpsCheckRequest tpsCheckRequest);
}
