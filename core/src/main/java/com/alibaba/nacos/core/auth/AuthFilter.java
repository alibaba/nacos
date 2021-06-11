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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.auth.AuthManager;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.AuthConfigs;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.parser.ResourceParser;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.WebUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified filter to handle authentication and authorization.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class AuthFilter implements Filter {
    
    @Autowired
    private AuthConfigs authConfigs;
    
    @Autowired
    private AuthManager authManager;
    
    @Autowired
    private ControllerMethodsCache methodsCache;
    
    private Map<Class<? extends ResourceParser>, ResourceParser> parserInstance = new ConcurrentHashMap<>();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!authConfigs.isAuthEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        if (authConfigs.isEnableUserAgentAuthWhite()) {
            String userAgent = WebUtils.getUserAgent(req);
            if (StringUtils.startsWith(userAgent, Constants.NACOS_SERVER_HEADER)) {
                chain.doFilter(request, response);
                return;
            }
        } else if (StringUtils.isNotBlank(authConfigs.getServerIdentityKey()) && StringUtils
                .isNotBlank(authConfigs.getServerIdentityValue())) {
            String serverIdentity = req.getHeader(authConfigs.getServerIdentityKey());
            if (authConfigs.getServerIdentityValue().equals(serverIdentity)) {
                chain.doFilter(request, response);
                return;
            }
            Loggers.AUTH.warn("Invalid server identity value for {} from {}", authConfigs.getServerIdentityKey(),
                    req.getRemoteHost());
        } else {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                            + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`");
            return;
        }
        
        try {
            
            Method method = methodsCache.getMethod(req);
            
            if (method == null) {
                chain.doFilter(request, response);
                return;
            }
            
            if (method.isAnnotationPresent(Secured.class) && authConfigs.isAuthEnabled()) {
                
                if (Loggers.AUTH.isDebugEnabled()) {
                    Loggers.AUTH.debug("auth start, request: {} {}", req.getMethod(), req.getRequestURI());
                }
                
                Secured secured = method.getAnnotation(Secured.class);
                String action = secured.action().toString();
                String resource = secured.resource();
                
                if (StringUtils.isBlank(resource)) {
                    ResourceParser parser = getResourceParser(secured.parser());
                    resource = parser.parseName(req);
                }
                
                if (StringUtils.isBlank(resource)) {
                    // deny if we don't find any resource:
                    throw new AccessException("resource name invalid!");
                }
                
                authManager.auth(new Permission(resource, action), authManager.login(req));
                
            }
            chain.doFilter(request, response);
        } catch (AccessException e) {
            if (Loggers.AUTH.isDebugEnabled()) {
                Loggers.AUTH.debug("access denied, request: {} {}, reason: {}", req.getMethod(), req.getRequestURI(),
                        e.getErrMsg());
            }
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, e.getErrMsg());
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, ExceptionUtil.getAllExceptionMsg(e));
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server failed," + e.getMessage());
        }
    }
    
    private ResourceParser getResourceParser(Class<? extends ResourceParser> parseClass)
            throws IllegalAccessException, InstantiationException {
        ResourceParser parser = parserInstance.get(parseClass);
        if (parser == null) {
            parser = parseClass.newInstance();
            parserInstance.put(parseClass, parser);
        }
        return parser;
    }
}
