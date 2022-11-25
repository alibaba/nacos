package com.alibaba.nacos.config.server.control;

import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;

public class ConfigTenantKey extends MonitorKey {
    
    public ConfigTenantKey(String key) {
        this.setKey(key);
    }
    
    @Override
    public String getType() {
        return "tenant";
    }
}
