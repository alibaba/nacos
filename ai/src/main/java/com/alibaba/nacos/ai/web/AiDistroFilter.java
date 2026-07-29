/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.web;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.impl.HttpConnectionBasedClient;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * Routes stateful Agent HTTP requests to the owner of their stable HTTP client id.
 *
 * @author Nacos
 */
public class AiDistroFilter implements Filter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiDistroFilter.class);
    
    private static final int PROXY_CONNECT_TIMEOUT = 2000;
    
    private static final int PROXY_READ_TIMEOUT = 2000;
    
    private final DistroMapper distroMapper;
    
    public AiDistroFilter(DistroMapper distroMapper) {
        this.distroMapper = distroMapper;
    }
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
        ReuseHttpServletRequest request =
            new ReuseHttpServletRequest((HttpServletRequest) servletRequest);
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String externalClientId = request.getHeader(ClientConstants.HTTP_CLIENT_ID_HEADER);
        if (StringUtils.isBlank(externalClientId)) {
            filterChain.doFilter(request, response);
            return;
        }
        String internalClientId =
            HttpConnectionBasedClient.getInternalClientId(externalClientId);
        if (distroMapper.responsible(internalClientId)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isPeerRequest(request)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "receive invalid redirect request from peer " + request.getRemoteAddr());
            return;
        }
        proxyRequest(request, response, internalClientId);
    }
    
    private boolean isPeerRequest(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaderConsts.USER_AGENT_HEADER);
        return StringUtils.isNotBlank(userAgent)
            && userAgent.contains(UtilsAndCommons.NACOS_SERVER_HEADER);
    }
    
    private void proxyRequest(ReuseHttpServletRequest request, HttpServletResponse response,
        String internalClientId) throws IOException {
        try {
            String targetServer = distroMapper.mapSrv(internalClientId);
            List<String> headers = getHeaders(request);
            String body =
                IoUtils.toString(request.getInputStream(), StandardCharsets.UTF_8.name());
            RestResult<String> result = HttpClient.request(buildTargetUrl(targetServer, request),
                headers, Collections.emptyMap(), body, PROXY_CONNECT_TIMEOUT,
                PROXY_READ_TIMEOUT, StandardCharsets.UTF_8.name(), request.getMethod());
            WebUtils.response(response, result.ok() ? result.getData() : result.getMessage(),
                result.getCode());
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "access denied: " + ExceptionUtil.getAllExceptionMsg(e));
        } catch (Exception e) {
            LOGGER.warn("[AI-DISTRO-FILTER] Server failed for {}", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Server failed, " + ExceptionUtil.getAllExceptionMsg(e));
        }
    }
    
    private List<String> getHeaders(HttpServletRequest request) {
        List<String> result = new ArrayList<>(16);
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            result.add(name);
            result.add(request.getHeader(name));
        }
        return result;
    }
    
    private String buildTargetUrl(String targetServer, HttpServletRequest request) {
        String result = HTTP_PREFIX + targetServer + request.getRequestURI();
        if (StringUtils.isNotBlank(request.getQueryString())) {
            result += "?" + request.getQueryString();
        }
        return result;
    }
}
