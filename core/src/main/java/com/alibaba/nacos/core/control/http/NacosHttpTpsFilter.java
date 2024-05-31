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

package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.control.TpsControlConfig;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;

import javax.servlet.AsyncContext;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Nacos http tps control cut point filter.
 *
 * @author xiweng.yy
 */
public class NacosHttpTpsFilter implements Filter {
    
    private ControllerMethodsCache controllerMethodsCache;
    
    private TpsControlManager tpsControlManager;
    
    public NacosHttpTpsFilter(ControllerMethodsCache controllerMethodsCache) {
        this.controllerMethodsCache = controllerMethodsCache;
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }
    
    private void initTpsControlManager() {
        if (tpsControlManager == null) {
            tpsControlManager = ControlManagerCenter.getInstance().getTpsControlManager();
        }
    }
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;
        
        Method method = controllerMethodsCache.getMethod(httpServletRequest);
        try {
            if (method != null && method.isAnnotationPresent(TpsControl.class)
                    && TpsControlConfig.isTpsControlEnabled()) {
                TpsControl tpsControl = method.getAnnotation(TpsControl.class);
                String pointName = tpsControl.pointName();
                String parserName = StringUtils.isBlank(tpsControl.name()) ? pointName : tpsControl.name();
                HttpTpsCheckRequestParser parser = HttpTpsCheckRequestParserRegistry.getParser(parserName);
                TpsCheckRequest httpTpsCheckRequest = null;
                if (parser != null) {
                    httpTpsCheckRequest = parser.parse(httpServletRequest);
                }
                if (httpTpsCheckRequest == null) {
                    httpTpsCheckRequest = new TpsCheckRequest();
                }
                if (StringUtils.isBlank(httpTpsCheckRequest.getPointName())) {
                    httpTpsCheckRequest.setPointName(pointName);
                }
                initTpsControlManager();
                TpsCheckResponse checkResponse = tpsControlManager.check(httpTpsCheckRequest);
                if (!checkResponse.isSuccess()) {
                    AsyncContext asyncContext = httpServletRequest.startAsync();
                    asyncContext.setTimeout(0);
                    RpcScheduledExecutor.CONTROL_SCHEDULER.schedule(
                            () -> generate503Response(httpServletRequest, response, checkResponse.getMessage(),
                                    asyncContext), 1000L, TimeUnit.MILLISECONDS);
                    return;
                }
                
            }
        } catch (Throwable throwable) {
            Loggers.TPS.warn("Fail to  http tps check", throwable);
        }
        
        filterChain.doFilter(httpServletRequest, response);
    }
    
    @Override
    public void destroy() {
        Filter.super.destroy();
    }
    
    void generate503Response(HttpServletRequest request, HttpServletResponse response, String message,
            AsyncContext asyncContext) {
        
        try {
            // Disable cache.
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-cache,no-store");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getOutputStream().println(message);
            asyncContext.complete();
        } catch (Exception ex) {
            Loggers.TPS.error("Error to generate tps 503 response", ex);
        }
    }
}
