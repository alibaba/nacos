package com.alibaba.nacos.plugin.control.tps.mse.interceptor;

import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.tps.mse.MseTpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;

public abstract class TpsInterceptor {
    
    boolean disabled;
    
    public boolean isDisabled() {
        return disabled;
    }
    
    public void setDisabled(boolean disabled) {
        Loggers.CONTROL
                .warn("TpsInterceptor {} disabled status is set to {}", this.getClass().getSimpleName(), disabled);
        this.disabled = disabled;
    }
    
    public int getOrder() {
        return 0;
    }
    
    /**
     * get interceptor name.
     *
     * @return
     */
    public abstract String getName();
    
    /**
     * get point name.
     *
     * @return
     */
    public abstract String getPointName();
    
    /**
     * tps pre intercept method.
     *
     * @param mseTpsCheckRequest mseTpsCheckRequest.
     * @return if return denied or passed,return directly.
     */
    public abstract InterceptResult preIntercept(MseTpsCheckRequest mseTpsCheckRequest);
    
    /**
     * tps post intercept method,the returned InterceptResult  will influence the final tps check response.
     *
     * @param mseTpsCheckRequest mseTpsCheckRequest.
     * @param tpsCheckResponse   tpsCheckResponse.
     * @return
     */
    public abstract InterceptResult postIntercept(MseTpsCheckRequest mseTpsCheckRequest,
            TpsCheckResponse tpsCheckResponse);
}
