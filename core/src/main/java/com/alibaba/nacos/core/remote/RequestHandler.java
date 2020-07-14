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
package com.alibaba.nacos.core.remote;

import java.util.List;

import javax.annotation.PostConstruct;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author liuzunfei
 * @version $Id: RequestHandler.java, v 0.1 2020年07月13日 8:22 PM liuzunfei Exp $
 */
public abstract class RequestHandler<T> {


    @Autowired
    private RequestHandlerRegistry requestHandlerRegistry;

    @PostConstruct
    public void init(){
        requestHandlerRegistry.registryHandler(this);
    }

    /**
     *
     * @param bodyString
     * @param <T>
     * @return
     */
    abstract public <T extends  Request> T parseBodyString(String bodyString);

    /**
     * handler request
     * @param request
     * @return
     * @throws NacosException
     */
    abstract public Response handle(Request request, RequestMeta meta) throws NacosException;

    /**
     * retrun the request type that this handler can handler
     * @return
     */
    abstract public List<String> getRequestTypes();

}
