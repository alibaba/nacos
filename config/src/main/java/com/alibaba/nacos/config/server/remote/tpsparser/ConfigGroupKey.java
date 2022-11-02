package com.alibaba.nacos.config.server.remote.tpsparser;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;

class ConfigGroupKey extends MonitorKey {
    
    public ConfigGroupKey(String key) {
        this.setKey(key);
    }
    
    @Override
    public String getType() {
        return "groupKey";
    }
}
