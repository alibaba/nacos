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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.auth.AuthManager;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.AuthConfigs;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.parser.ResourceParser;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.core.remote.AbstractRequestFilter;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * request auth filter for remote.
 *
 * @author liuzunfei
 * @version $Id: RemoteRequestAuthFilter.java, v 0.1 2020年09月14日 12:38 PM liuzunfei Exp $
 */
@Component
public class RemoteRequestAuthFilter extends AbstractRequestFilter {
    
    @Autowired
    private AuthConfigs authConfigs;
    
    @Autowired
    private AuthManager authManager;
    
    private Method getMethod(Class handlerClazz) throws NacosException {
        try {
            Method method = handlerClazz.getMethod("handle", Request.class, RequestMeta.class);
            return method;
        } catch (NoSuchMethodException e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    private Class getResponseClazz(Class handlerClazz) throws NacosException {
        ParameterizedType parameterizedType = (ParameterizedType) handlerClazz.getGenericSuperclass();
        try {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            return Class.forName(actualTypeArguments[1].getTypeName());
            
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public Response filter(Request request, RequestMeta meta, Class handlerClazz) {
        
        Response response = null;
        try {
            response = (Response) getResponseClazz(handlerClazz).newInstance();
        } catch (Exception e) {
            Loggers.AUTH.error("auth fail, request: {},exception:", request.getClass().getSimpleName(), e);
            
        }
        
        try {
            
            Method method = getMethod(handlerClazz);
            if (method.isAnnotationPresent(Secured.class) && authConfigs.isAuthEnabled()) {
                
                if (Loggers.AUTH.isDebugEnabled()) {
                    Loggers.AUTH.debug("auth start, request: {}", request.getClass().getSimpleName());
                }
                
                Secured secured = method.getAnnotation(Secured.class);
                String action = secured.action().toString();
                String resource = secured.resource();
                
                if (StringUtils.isBlank(resource)) {
                    ResourceParser parser = secured.parser().newInstance();
                    resource = parser.parseName(request);
                }
                
                if (StringUtils.isBlank(resource)) {
                    // deny if we don't find any resource:
                    throw new AccessException("resource name invalid!");
                }
                
                authManager.auth(new Permission(resource, action), authManager.loginRemote(request));
                
            }
        } catch (AccessException e) {
            if (Loggers.AUTH.isDebugEnabled()) {
                Loggers.AUTH.debug("access denied, request: {}, reason: {}", request.getClass().getSimpleName(),
                        e.getErrMsg());
            }
            response.setErrorInfo(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return response;
        } catch (IllegalArgumentException e) {
            response.setErrorInfo(HttpServletResponse.SC_BAD_REQUEST, ExceptionUtil.getAllExceptionMsg(e));
            return response;
        } catch (Exception e) {
            response.setErrorInfo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server failed," + e.getMessage());
            return response;
        }
        
        return response;
    }
}
