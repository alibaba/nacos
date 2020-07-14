/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.NamingRequestTypeConstants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.AsyncListenContext;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * handler to handle service instance change listen request.
 *
 * @author liuzunfei
 * @version $Id: ServiceInstanceChangeListenRequestHandler.java, v 0.1 2020年07月14日 7:55 PM liuzunfei Exp $
 */
@Component
public class ServiceInstanceChangeListenRequestHandler extends RequestHandler {
    
    @Autowired
    AsyncListenContext asyncListenContext;
    
    private static final String LISTEN_CONTEXT_TYPE = "CONFIG";
    
    @Override
    public Request parseBodyString(String bodyString) {
        return null;
    }
    
    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        return null;
    }
    
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(NamingRequestTypeConstants.SERVICE_INSTANCE_CHANGE);
    }
}
