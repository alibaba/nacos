/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
