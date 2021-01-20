package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorKeyParser;

public class ConfigPublishGroupKeyParser extends MonitorKeyParser {
    
    public MonitorKey parse(Object... args) {
        if (args != null && args.length != 0 && args[0] instanceof ConfigPublishRequest) {
            
            return new ConfigGroupKey(GroupKey.getKeyTenant(((ConfigPublishRequest) args[0]).getDataId(),
                    ((ConfigPublishRequest) args[0]).getGroup(), ((ConfigPublishRequest) args[0]).getTenant()));
        } else {
            return null;
        }
        
    }
    
    class ConfigGroupKey extends MonitorKey {
        
        public ConfigGroupKey(String key) {
            this.setKey(key);
        }
        
        @Override
        public String getType() {
            return "groupKey";
        }
    }
}