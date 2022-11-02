package com.alibaba.nacos.core.remote.control;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;

public interface RemoteTpsCheckParser {
    
    TpsCheckRequest parse(Request request, RequestMeta meta);
}
