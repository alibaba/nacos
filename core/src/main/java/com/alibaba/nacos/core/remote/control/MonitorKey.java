package com.alibaba.nacos.core.remote.control;

import com.alibaba.nacos.api.common.Constants;

public abstract class MonitorKey {
    
    String key;
    
    public MonitorKey() {
    
    }
    
    public MonitorKey(String key) {
        this.key = key;
    }
    
    public abstract String getType();
    
    
    public String getKey() {
        return this.key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String build() {
        return this.getType() + Constants.COLON + this.getKey();
    }
}
