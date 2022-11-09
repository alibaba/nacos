package com.alibaba.nacos.config.server.control;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;

public class ConfigGroupMonitorKey extends MonitorKey {
    
    public ConfigGroupMonitorKey(String key) {
        this.setKey(key);
    }
    
    @Override
    public String getType() {
        return "group";
    }
}
