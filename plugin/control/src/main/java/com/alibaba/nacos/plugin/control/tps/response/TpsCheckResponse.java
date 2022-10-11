package com.alibaba.nacos.plugin.control.tps.response;

/**
 * tps request.
 */
public class TpsCheckResponse {
    
    private boolean success;
    
    private TpsResultCode code;
    
    private String message;
    
    public TpsCheckResponse(boolean success, TpsResultCode code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }
    
    public TpsResultCode getCode() {
        return code;
    }
    
    public void setCode(TpsResultCode code) {
        this.code = code;
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
