package com.alibaba.nacos.config.server.remote.tpsparser;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.control.ConfigGroupKey;
import com.alibaba.nacos.config.server.control.ConfigGroupMonitorKey;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.control.remote.RemoteTpsCheckRequestParser;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ConfigQueryParser extends RemoteTpsCheckRequestParser {
    
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
    
    @Override
    public String getPointName() {
        return "ConfigQuery";
    }
    
    @Override
    public String getName() {
        return "ConfigQuery";
    }
}
