package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

import javax.servlet.http.HttpServletRequest;

public abstract class HttpTpsCheckRequestParser {
    
    public HttpTpsCheckRequestParser() {
        registerParser();
    }
    
    public void registerParser() {
        TpsCheckRequestParserRegistry.register(this);
    }
    
    public abstract TpsCheckRequest parse(HttpServletRequest httpServletRequest);
    
    public abstract String getPointName();
    
    public abstract String getName();
    
}
