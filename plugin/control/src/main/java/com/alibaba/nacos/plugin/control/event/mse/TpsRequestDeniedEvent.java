package com.alibaba.nacos.plugin.control.event.mse;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;

public class TpsRequestDeniedEvent extends Event {
    
    private TpsCheckRequest tpsCheckRequest;
    
    private int tpsResultCode;
    
    private boolean isMonitorModel;
    
    private String message;
    
    public TpsRequestDeniedEvent(TpsCheckRequest tpsCheckRequest, int tpsResultCode, String message) {
        this.tpsCheckRequest = tpsCheckRequest;
        this.tpsResultCode = tpsResultCode;
        this.message = message;
    }
    
    public boolean isMonitorModel() {
        return isMonitorModel;
    }
    
    public void setMonitorModel(boolean monitorModel) {
        isMonitorModel = monitorModel;
    }
    
    public int getTpsResultCode() {
        return tpsResultCode;
    }
    
    public void setTpsResultCode(int tpsResultCode) {
        this.tpsResultCode = tpsResultCode;
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
