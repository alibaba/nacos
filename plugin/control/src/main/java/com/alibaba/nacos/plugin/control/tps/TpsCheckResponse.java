package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;

import java.util.List;

/**
 * tps request.
 */
public class TpsCheckResponse {
    
    private boolean success;
    
    private String message;
    
    
    public TpsCheckResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
}
