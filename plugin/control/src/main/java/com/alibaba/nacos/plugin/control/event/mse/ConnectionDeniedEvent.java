package com.alibaba.nacos.plugin.control.event.mse;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;

public class ConnectionDeniedEvent extends Event {
    
    private ConnectionCheckRequest connectionCheckRequest;
    
    private ConnectionCheckCode connectionCheckCode;
    
    private boolean isMonitorModel;
    
    private String message;
    
    public ConnectionDeniedEvent(ConnectionCheckRequest connectionCheckRequest, ConnectionCheckCode connectionCheckCode,
            String message) {
        this.connectionCheckRequest = connectionCheckRequest;
        this.connectionCheckCode = connectionCheckCode;
        this.message = message;
    }
    
    public boolean isMonitorModel() {
        return isMonitorModel;
    }
    
    public void setMonitorModel(boolean monitorModel) {
        isMonitorModel = monitorModel;
    }
    
    public ConnectionCheckRequest getConnectionCheckRequest() {
        return connectionCheckRequest;
    }
    
    public void setConnectionCheckRequest(ConnectionCheckRequest connectionCheckRequest) {
        this.connectionCheckRequest = connectionCheckRequest;
    }
    
    public ConnectionCheckCode getConnectionCheckCode() {
        return connectionCheckCode;
    }
    
    public void setConnectionCheckCode(ConnectionCheckCode connectionCheckCode) {
        this.connectionCheckCode = connectionCheckCode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
