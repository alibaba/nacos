package com.alibaba.nacos.config.server.remote.tpsparser;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;

class ConfigGroupMonitorKey extends MonitorKey {
    
    public ConfigGroupMonitorKey(String key) {
        this.setKey(key);
    }
    
    @Override
    public String getType() {
        return "group";
    }
}
