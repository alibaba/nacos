package com.alibaba.nacos.plugin.control.event;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;

public class ConnectionDeniedEvent extends Event {
    
    private ConnectionCheckRequest connectionCheckRequest;
    
    private ConnectionCheckCode connectionCheckCode;
    
    private String message;
    
    public ConnectionDeniedEvent(ConnectionCheckRequest connectionCheckRequest, ConnectionCheckCode connectionCheckCode,
            String message) {
        this.connectionCheckRequest = connectionCheckRequest;
        this.connectionCheckCode = connectionCheckCode;
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
