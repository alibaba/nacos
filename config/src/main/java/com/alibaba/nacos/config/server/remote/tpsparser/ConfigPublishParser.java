package com.alibaba.nacos.config.server.remote.tpsparser;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.control.remote.RemoteTpsCheckParser;
import com.alibaba.nacos.core.control.remote.TpsCheckRequestParserRegistry;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ConfigPublishParser implements RemoteTpsCheckParser {
    
    
    static {
        TpsCheckRequestParserRegistry.register("ConfigPublish", new ConfigPublishParser());
    }
    
    @Override
    public TpsCheckRequest parse(Request request, RequestMeta meta) {
        if (request instanceof ConfigPublishRequest) {
            ConfigPublishRequest configpublishrequest = (ConfigPublishRequest) request;
            ConfigGroupKey configGroupKey = new ConfigGroupKey(
                    GroupKey.getKeyTenant(configpublishrequest.getDataId(), configpublishrequest.getGroup(),
                            configpublishrequest.getTenant()));
            
            ConfigGroupMonitorKey configGroupMonitorKey = new ConfigGroupMonitorKey(configpublishrequest.getGroup());
            TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
            tpsCheckRequest.setClientIp(meta.getClientIp());
            tpsCheckRequest.setConnectionId(meta.getConnectionId());
            tpsCheckRequest.setMonitorKeys(new ArrayList<>());
            tpsCheckRequest.getMonitorKeys().add(configGroupKey);
            tpsCheckRequest.getMonitorKeys().add(configGroupMonitorKey);
            
            return tpsCheckRequest;
            
        }
        return null;
    }
}
