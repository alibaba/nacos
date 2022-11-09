package com.alibaba.nacos.plugin.control.event;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;

public class ConnectionDeniedEvent extends Event {
    
    private ConnectionCheckRequest connectionCheckRequest;
    
    private String message;
    
    public ConnectionDeniedEvent(ConnectionCheckRequest connectionCheckRequest, String message) {
        this.connectionCheckRequest = connectionCheckRequest;
        this.message = message;
    }
    
    public ConnectionCheckRequest getConnectionCheckRequest() {
        return connectionCheckRequest;
    }
    
    public void setConnectionCheckRequest(ConnectionCheckRequest connectionCheckRequest) {
        this.connectionCheckRequest = connectionCheckRequest;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
