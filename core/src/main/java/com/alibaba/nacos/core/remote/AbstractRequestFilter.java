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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * interceptor fo request.
 *
 * @author liuzunfei
 * @version $Id: AbstractRequestFilter.java, v 0.1 2020年09月14日 11:46 AM liuzunfei Exp $
 */
public abstract class AbstractRequestFilter {
    
    @Autowired
    private RequestFilters requestFilters;
    
    public AbstractRequestFilter() {
    }
    
    @PostConstruct
    public void init() {
        requestFilters.registerFilter(this);
    }
    
    protected Class getResponseClazz(Class handlerClazz) throws NacosException {
        ParameterizedType parameterizedType = (ParameterizedType) handlerClazz.getGenericSuperclass();
        try {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            return Class.forName(actualTypeArguments[1].getTypeName());
            
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    protected Method getHandleMethod(Class handlerClazz) throws NacosException {
        try {
            Method method = handlerClazz.getMethod("handle", Request.class, RequestMeta.class);
            return method;
        } catch (NoSuchMethodException e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    protected <T> Response getDefaultResponseInstance(Class handlerClazz) throws NacosException {
        ParameterizedType parameterizedType = (ParameterizedType) handlerClazz.getGenericSuperclass();
        try {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            return (Response) Class.forName(actualTypeArguments[1].getTypeName()).newInstance();
            
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    /**
     * filter request.
     *
     * @param request      request.
     * @param meta         request meta.
     * @param handlerClazz request handler clazz.
     * @return response
     * @throws NacosException NacosException.
     */
    protected abstract Response filter(Request request, RequestMeta meta, Class handlerClazz) throws NacosException;
}
