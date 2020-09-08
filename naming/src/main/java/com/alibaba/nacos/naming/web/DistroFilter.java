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
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.utils.OverrideParameterRequestWrapper;
import com.alibaba.nacos.core.utils.ReuseHttpRequest;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

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
import java.net.URI;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Distro filter.
 *
 * @author nacos
 */
public class DistroFilter implements Filter {
    
    private static final int PROXY_CONNECT_TIMEOUT = 2000;
    
    private static final int PROXY_READ_TIMEOUT = 2000;
    
    @Autowired
    private DistroMapper distroMapper;
    
    @Autowired
    private ControllerMethodsCache controllerMethodsCache;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        
    }
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
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
            Method method = controllerMethodsCache.getMethod(req);
            
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
                
                final String targetServer = distroMapper.mapSrv(groupedServiceName);
                
                List<String> headerList = new ArrayList<>(16);
                Enumeration<String> headers = req.getHeaderNames();
                while (headers.hasMoreElements()) {
                    String headerName = headers.nextElement();
                    headerList.add(headerName);
                    headerList.add(req.getHeader(headerName));
                }
                
                final String body = IoUtils.toString(req.getInputStream(), Charsets.UTF_8.name());
                final Map<String, String> paramsValue = HttpClient.translateParameterMap(req.getParameterMap());
                
                RestResult<String> result = HttpClient
                        .request("http://" + targetServer + req.getRequestURI(), headerList, paramsValue, body,
                                PROXY_CONNECT_TIMEOUT, PROXY_READ_TIMEOUT, Charsets.UTF_8.name(), req.getMethod());
                String data = result.ok() ? result.getData() : result.getMessage();
                try {
                    WebUtils.response(resp, data, result.getCode());
                } catch (Exception ignore) {
                    Loggers.SRV_LOG.warn("[DISTRO-FILTER] request failed: " + distroMapper.mapSrv(groupedServiceName)
                            + urlString);
                }
            } else {
                OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(req);
                requestWrapper.addParameter(CommonParams.SERVICE_NAME, groupedServiceName);
                filterChain.doFilter(requestWrapper, resp);
            }
        } catch (AccessControlException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "access denied: " + ExceptionUtil.getAllExceptionMsg(e));
        } catch (NoSuchMethodException e) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                    "no such api:" + req.getMethod() + ":" + req.getRequestURI());
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Server failed," + ExceptionUtil.getAllExceptionMsg(e));
        }
        
    }
    
    @Override
    public void destroy() {
        
    }
}
