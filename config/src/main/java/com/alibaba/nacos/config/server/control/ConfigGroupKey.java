package com.alibaba.nacos.config.server.control;

import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;

public class ConfigGroupKey extends MonitorKey {
    
    public ConfigGroupKey(String key) {
        this.setKey(key);
    }
    
    public ConfigGroupKey(String dataId, String group, String tenant) {
        this.setKey(GroupKey2.getKey(dataId, group, tenant));
    }
    
    @Override
    public String getType() {
        return "groupKey";
    }
}
