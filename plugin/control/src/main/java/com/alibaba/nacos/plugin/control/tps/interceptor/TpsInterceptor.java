package com.alibaba.nacos.plugin.control.tps.interceptor;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;

public interface TpsInterceptor {
    
    /**
     * get interceptor name.
     *
     * @return
     */
    String getName();
    
    
    /**
     * get point name.
     *
     * @return
     */
    String getPointName();
    
    /**
     * tps pre intercept method.
     *
     * @param tpsCheckRequest tpsCheckRequest.
     * @return if return denied or passed,return directly.
     */
    InterceptResult preIntercept(TpsCheckRequest tpsCheckRequest);
    
    /**
     * tps post intercept method,the returned InterceptResult  will influence the final tps check response.
     *
     * @param tpsCheckRequest  tpsCheckRequest.
     * @param tpsCheckResponse tpsCheckResponse.
     * @return
     */
    InterceptResult postIntercept(TpsCheckRequest tpsCheckRequest, TpsCheckResponse tpsCheckResponse);
}
