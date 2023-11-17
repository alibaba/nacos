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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.paramcheck.AbstractParamChecker;
import com.alibaba.nacos.common.paramcheck.ParamCheckResponse;
import com.alibaba.nacos.common.paramcheck.ParamCheckerManager;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.plugin.control.Loggers;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * ParamCheckerFilter to http filter.
 *
 * @author 985492783@qq.com
 * @date 2023/11/7 17:40
 */
public class ParamCheckerFilter implements Filter {
    
    private final ControllerMethodsCache methodsCache;
    
    public ParamCheckerFilter(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        boolean paramCheckEnabled = ServerParamCheckConfig.getInstance().isParamCheckEnabled();
        if (!paramCheckEnabled) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        try {
            Method method = methodsCache.getMethod(req);
            if (method == null) {
                chain.doFilter(req, resp);
                return;
            }
            ExtractorManager.Extractor extractor = method.getAnnotation(ExtractorManager.Extractor.class);
            if (extractor == null) {
                extractor = method.getDeclaringClass().getAnnotation(ExtractorManager.Extractor.class);
                if (extractor == null) {
                    chain.doFilter(request, response);
                    return;
                }
            }
            AbstractHttpParamExtractor httpParamExtractor = ExtractorManager.getHttpExtractor(extractor);
            List<ParamInfo> paramInfoList = httpParamExtractor.extractParam(req);
            ParamCheckerManager paramCheckerManager = ParamCheckerManager.getInstance();
            AbstractParamChecker paramChecker = paramCheckerManager.getParamChecker(ServerParamCheckConfig.getInstance().getActiveParamChecker());
            ParamCheckResponse paramCheckResponse = paramChecker.checkParamInfoList(paramInfoList);
            if (paramCheckResponse.isSuccess()) {
                chain.doFilter(req, resp);
            } else {
                Loggers.CONTROL.info("Param check invalid,{},url:{}", paramCheckResponse.getMessage(), req.getRequestURI());
                generate400Response(resp, paramCheckResponse.getMessage());
            }
        } catch (NacosException e) {
            Loggers.CONTROL.error("exception: {}", e.getMessage());
            throw new NacosRuntimeException(ErrorCode.UnKnowError.getCode(), e);
        }
        
    }
    
    /**
     * Generate 400 response.
     *
     * @param response the response
     * @param message  the message
     */
    public void generate400Response(HttpServletResponse response, String message) {
        try {
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-cache,no-store");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getOutputStream().println(message);
        } catch (Exception ex) {
            Loggers.CONTROL.error("Error to generate tps 400 response", ex);
        }
    }
}
