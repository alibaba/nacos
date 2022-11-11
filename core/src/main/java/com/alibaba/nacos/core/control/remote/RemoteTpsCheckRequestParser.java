package com.alibaba.nacos.core.control.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

public abstract class RemoteTpsCheckRequestParser {
    
    public RemoteTpsCheckRequestParser() {
        RemoteTpsCheckRequestParserRegistry.register(this);
    }
    
    public abstract TpsCheckRequest parse(Request request, RequestMeta meta);
    
    public abstract String getPointName();
    
    public abstract String getName();
}
