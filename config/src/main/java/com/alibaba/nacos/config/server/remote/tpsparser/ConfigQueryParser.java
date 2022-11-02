package com.alibaba.nacos.config.server.remote.tpsparser;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.control.RemoteTpsCheckParser;
import com.alibaba.nacos.core.remote.control.TpsCheckRequestParserRegistry;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ConfigQueryParser implements RemoteTpsCheckParser {
    
    static {
        TpsCheckRequestParserRegistry.register("ConfigQuery", new ConfigQueryParser());
    }
    
    @Override
    public TpsCheckRequest parse(Request request, RequestMeta meta) {
        if (request instanceof ConfigQueryRequest) {
            ConfigQueryRequest configQueryRequest = (ConfigQueryRequest) request;
            ConfigGroupKey configGroupKey = new ConfigGroupKey(
                    GroupKey.getKeyTenant(configQueryRequest.getDataId(), configQueryRequest.getGroup(),
                            configQueryRequest.getTenant()));
            
            ConfigGroupMonitorKey configGroupMonitorKey = new ConfigGroupMonitorKey(configQueryRequest.getGroup());
            TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
            tpsCheckRequest.setConnectionId(meta.getConnectionId());
            tpsCheckRequest.setClientIp(meta.getClientIp());
            tpsCheckRequest.setMonitorKeys(new ArrayList<>());
            tpsCheckRequest.getMonitorKeys().add(configGroupKey);
            tpsCheckRequest.getMonitorKeys().add(configGroupMonitorKey);
            return tpsCheckRequest;
        }
        return null;
    }
}
