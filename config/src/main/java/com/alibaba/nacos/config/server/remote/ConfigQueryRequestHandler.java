/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.config.server.remote;

import java.util.List;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRequestTypeConstants;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.RequestHandler;

import com.google.common.collect.Lists;

/**
 * @author liuzunfei
 * @version $Id: ConfigQueryRequestHandler.java, v 0.1 2020年07月14日 9:54 AM liuzunfei Exp $
 */
public class ConfigQueryRequestHandler  extends RequestHandler {

    @Override
    public Request parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, ConfigQueryRequest.class);
    }

    @Override
    public Response handle(Request request, RequestMeta requestMeta) throws NacosException {
        ConfigQueryRequest configQueryRequest = (ConfigQueryRequest)request;

        String group = configQueryRequest.getGroup();
        String dataId = configQueryRequest.getDataId();
        String tenant = configQueryRequest.getTenant();
        return new ConfigQueryResponse(200,"Not Found");
    }


    private void getContext(){
        //TODO
    }


    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(ConfigRequestTypeConstants.QUERY_CONFIG);
    }
}
