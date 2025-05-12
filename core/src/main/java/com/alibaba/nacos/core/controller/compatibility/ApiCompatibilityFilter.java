/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.auth.InnerApiAuthEnabled;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * HTTP Filter for API Compatibility.
 *
 * @author xiweng.yy
 */
public class ApiCompatibilityFilter implements Filter {
    
    private static final String MESSAGE_NO_REPLACED_API = "Current API will be deprecated, If wanted continue to use, "
            + "please set `%s=true` in application.properties.";
    
    private static final String MESSAGE_REPLACED_API =
            "Current API will be deprecated, please use API(s) `%s` instead, "
                    + "or set `%s=true` in application.properties.";
    
    private final ControllerMethodsCache methodsCache;
    
    private final InnerApiAuthEnabled innerApiAuthEnabled;
    
    public ApiCompatibilityFilter(ControllerMethodsCache methodsCache, InnerApiAuthEnabled innerApiAuthEnabled) {
        this.methodsCache = methodsCache;
        this.innerApiAuthEnabled = innerApiAuthEnabled;
    }
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        try {
            Method method = methodsCache.getMethod(request);
            if (method == null) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
            Compatibility compatibility = method.getAnnotation(Compatibility.class);
            if (null == compatibility) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
            ApiType apiType = compatibility.apiType();
            switch (apiType) {
                case ADMIN_API:
                    if (!ApiCompatibilityConfig.getInstance().isAdminApiCompatibility()) {
                        responseReject(response, compatibility, ApiCompatibilityConfig.ADMIN_API_COMPATIBILITY_KEY);
                        return;
                    }
                    break;
                case OPEN_API:
                    if (!ApiCompatibilityConfig.getInstance().isClientApiCompatibility()) {
                        responseReject(response, compatibility, ApiCompatibilityConfig.CLIENT_API_COMPATIBILITY_KEY);
                        return;
                    }
                    break;
                case CONSOLE_API:
                    if (!ApiCompatibilityConfig.getInstance().isConsoleApiCompatibility()) {
                        responseReject(response, compatibility, ApiCompatibilityConfig.CONSOLE_API_COMPATIBILITY_KEY);
                        return;
                    }
                    break;
                case INNER_API:
                    if (innerApiAuthEnabled.isEnabled()) {
                        Result<String> result = Result.failure(ErrorCode.API_DEPRECATED.getCode(),
                                String.format("Old Inner API %s is deprecated", request.getRequestURI()), null);
                        response.sendError(HttpServletResponse.SC_GONE, JacksonUtils.toJson(result));
                        return;
                    }
                    break;
                default:
                    filterChain.doFilter(servletRequest, servletResponse);
                    return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception e) {
            Loggers.CORE.error("Filter for API {} Compatibility failed.", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Handle API Compatibility failed, please see log for detail.");
        }
    }
    
    private void responseReject(HttpServletResponse response, Compatibility compatibility, String switchName)
            throws IOException {
        String message;
        String replacedApis = compatibility.alternatives();
        if (StringUtils.isBlank(replacedApis)) {
            message = String.format(MESSAGE_NO_REPLACED_API, switchName);
        } else {
            message = String.format(MESSAGE_REPLACED_API, replacedApis, switchName);
        }
        Result<String> result = Result.failure(ErrorCode.API_DEPRECATED.getCode(), message, null);
        response.sendError(HttpServletResponse.SC_GONE, JacksonUtils.toJson(result));
    }
}
