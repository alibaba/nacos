package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

import javax.servlet.http.HttpServletRequest;

public interface HttpTpsCheckParser {
    
    TpsCheckRequest parse(HttpServletRequest httpServletRequest);
    
}
