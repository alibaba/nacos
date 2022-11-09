package com.alibaba.nacos.plugin.control.event;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

public class TpsRequestDeniedEvent extends Event {
    
    private TpsCheckRequest tpsCheckRequest;
    
    private String message;
    
    public TpsRequestDeniedEvent(TpsCheckRequest tpsCheckRequest, String message) {
        this.tpsCheckRequest = tpsCheckRequest;
        this.message = message;
    }
    
    public TpsCheckRequest getTpsCheckRequest() {
        return tpsCheckRequest;
    }
    
    public void setTpsCheckRequest(TpsCheckRequest tpsCheckRequest) {
        this.tpsCheckRequest = tpsCheckRequest;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
