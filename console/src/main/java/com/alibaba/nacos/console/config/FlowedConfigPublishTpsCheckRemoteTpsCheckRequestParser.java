package com.alibaba.nacos.console.config;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.remote.control.ConfigPublishRemoteTpsCheckRequestParser;
import com.alibaba.nacos.plugin.control.tps.mse.FlowedTpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
public class FlowedConfigPublishTpsCheckRemoteTpsCheckRequestParser extends ConfigPublishRemoteTpsCheckRequestParser {
    
    private ConfigPublishRemoteTpsCheckRequestParser configPublishRemoteTpsCheckRequestParser;
    
    public FlowedConfigPublishTpsCheckRemoteTpsCheckRequestParser(
            ConfigPublishRemoteTpsCheckRequestParser configPublishRemoteTpsCheckRequestParser) {
        this.configPublishRemoteTpsCheckRequestParser = configPublishRemoteTpsCheckRequestParser;
    }
    
    @Override
    public TpsCheckRequest parse(Request request, RequestMeta meta) {
        TpsCheckRequest parse = configPublishRemoteTpsCheckRequestParser.parse(request, meta);
        if (parse != null && request instanceof ConfigPublishRequest) {
            FlowedTpsCheckRequest flowedTpsCheckRequest = new FlowedTpsCheckRequest();
            BeanUtils.copyProperties(parse, flowedTpsCheckRequest);
            flowedTpsCheckRequest.setFlow(((ConfigPublishRequest) request).getContent().length());
            return flowedTpsCheckRequest;
        }
        return null;
    }
    
}
