package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorKeyParser;

public class ConfigPublishGroupParser extends MonitorKeyParser {
    
    public MonitorKey parse(Object... args) {
        if (args != null && args.length != 0 && args[0] instanceof ConfigPublishRequest) {
            return new ConfigGroupKey(((ConfigPublishRequest) args[0]).getGroup());
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
            return "group";
        }
    }
}
