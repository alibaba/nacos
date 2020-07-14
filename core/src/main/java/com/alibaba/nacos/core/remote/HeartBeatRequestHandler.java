/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.core.remote;

import java.util.List;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.HeartBeatRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.RequestTypeConstants;
import com.alibaba.nacos.api.remote.response.HeartBeatResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author liuzunfei
 * @version $Id: HeartBeatRequestHandler.java, v 0.1 2020年07月14日 1:58 PM liuzunfei Exp $
 */
@Component
public class HeartBeatRequestHandler extends RequestHandler{

    @Autowired
    ConnectionManager connectionManager;

    @Override
    public Request parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, HeartBeatRequest.class);
    }

    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        String connectionId = meta.getConnectionId();
        connectionManager.refreshActiveTime(connectionId);
        return new HeartBeatResponse(200,"heart beat success");
    }
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(RequestTypeConstants.HEART_BEAT);
    }
}
