package com.alibaba.nacos.plugin.control.connection.request;

import java.util.Map;

public class ConnectionCheckRequest {
    
    String clientIp;
    
    String appName;
    
    String source;
    
    Map<String, String> labels;
    
    public ConnectionCheckRequest(String clientIp, String appName, String source) {
        this.appName = appName;
        this.clientIp = clientIp;
        this.source=source;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
}
