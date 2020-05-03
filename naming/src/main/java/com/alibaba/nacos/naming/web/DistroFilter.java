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
package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.OverrideParameterRequestWrapper;
import com.alibaba.nacos.core.utils.ReuseHttpRequest;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author nacos
 */
public class DistroFilter implements Filter {

    private static final String SLASH= "/";
    private static final String DOUBLE_SLASH = "://";

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private ControllerMethodsCache controllerMethodsCache;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        ReuseHttpRequest req = new ReuseHttpServletRequest((HttpServletRequest) servletRequest);
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        String urlString = req.getRequestURI();

        if (StringUtils.isNotBlank(req.getQueryString())) {
            urlString += "?" + req.getQueryString();
        }

        try {
            String path = new URI(req.getRequestURI()).getPath();
            String serviceName = req.getParameter(CommonParams.SERVICE_NAME);
            // For client under 0.8.0:
            if (StringUtils.isBlank(serviceName)) {
                serviceName = req.getParameter("dom");
            }

            if (StringUtils.isNotBlank(serviceName)) {
                serviceName = serviceName.trim();
            }
            Method method = controllerMethodsCache.getMethod(req.getMethod(), path);

            if (method == null) {
                throw new NoSuchMethodException(req.getMethod() + " " + path);
            }

            String groupName = req.getParameter(CommonParams.GROUP_NAME);
            if (StringUtils.isBlank(groupName)) {
                groupName = Constants.DEFAULT_GROUP;
            }

            // use groupName@@serviceName as new service name:
            String groupedServiceName = serviceName;
            if (StringUtils.isNotBlank(serviceName) && !serviceName.contains(Constants.SERVICE_INFO_SPLITER)) {
                groupedServiceName = groupName + Constants.SERVICE_INFO_SPLITER + serviceName;
            }

            // proxy request to other server if necessary:
            if (method.isAnnotationPresent(CanDistro.class) && !distroMapper.responsible(groupedServiceName)) {

                String userAgent = req.getHeader(HttpHeaderConsts.USER_AGENT_HEADER);

                if (StringUtils.isNotBlank(userAgent) && userAgent.contains(UtilsAndCommons.NACOS_SERVER_HEADER)) {
                    // This request is sent from peer server, should not be redirected again:
                    Loggers.SRV_LOG.error("receive invalid redirect request from peer {}", req.getRemoteAddr());
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "receive invalid redirect request from peer " + req.getRemoteAddr());
                    return;
                }

                if (urlString.startsWith(SLASH)) {
                    urlString = urlString.substring(1);
                }

                final String targetServer = distroMapper.mapSrv(groupedServiceName);

                final String reqUrl =
                        req.getScheme() + DOUBLE_SLASH + targetServer + SLASH + urlString;

                HttpHeaders headers = new HttpHeaders();
                Enumeration<String> headerNames = req.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    headers.set(headerName, req.getHeader(headerName));
                }

                headers.set("Content-Type", "application/x-www-form-urlencoded;charset="
                        + Charsets.UTF_8.name());
                headers.set("Accept-Charset", Charsets.UTF_8.name());
                headers.set(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION);
                headers.set(HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION);

                try {
                    HttpEntity<Object> httpEntity = new HttpEntity<>(req.getBody(), headers);
                    ResponseEntity<String> result = restTemplate
                            .exchange(reqUrl, Objects.requireNonNull(
                                    HttpMethod.resolve(req.getMethod()), "req.getMethod() is null"), httpEntity,
                                    String.class);
                    resp.setCharacterEncoding("UTF-8");
                    resp.getWriter().write(Objects.requireNonNull(result.getBody(), "result.getBody() is null"));
                    resp.setStatus(result.getStatusCodeValue());
                } catch (Exception ignore) {
                    Loggers.SRV_LOG.warn("[DISTRO-FILTER] request failed: " + distroMapper.mapSrv(groupedServiceName) + urlString);
                }
                return;
            }

            OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(req);
            requestWrapper.addParameter(CommonParams.SERVICE_NAME, groupedServiceName);
            filterChain.doFilter(requestWrapper, resp);
        } catch (AccessControlException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "access denied: " + ExceptionUtil.getAllExceptionMsg(e));
            return;
        } catch (NoSuchMethodException e) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                "no such api:" + req.getMethod() + ":" + req.getRequestURI());
            return;
        } catch (Throwable e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Server failed," + ExceptionUtil.getAllExceptionMsg(e));
            return;
        }

    }

    @Override
    public void destroy() {

    }
}
