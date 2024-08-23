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

package com.alibaba.nacos.core.context.remote;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.BasicContext;
import com.alibaba.nacos.core.utils.WebUtils;
import org.apache.http.HttpHeaders;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static com.alibaba.nacos.api.common.Constants.CLIENT_APPNAME_HEADER;

/**
 * The Filter to add request context for HTTP protocol.
 *
 * @author xiweng.yy
 */
public class HttpRequestContextFilter implements Filter {
    
    private static final String PATTERN_REQUEST_TARGET = "%s %s";
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        RequestContext requestContext = RequestContextHolder.getContext();
        try {
            requestContext.getBasicContext().setRequestProtocol(BasicContext.HTTP_PROTOCOL);
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            setRequestTarget(request, requestContext);
            setEncoding(request, requestContext);
            setAddressContext(request, requestContext);
            setOtherBasicContext(request, requestContext);
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            RequestContextHolder.removeContext();
        }
    }
    
    private void setRequestTarget(HttpServletRequest request, RequestContext requestContext) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        requestContext.getBasicContext().setRequestTarget(String.format(PATTERN_REQUEST_TARGET, method, uri));
    }
    
    private void setEncoding(HttpServletRequest request, RequestContext requestContext) {
        String encoding = request.getCharacterEncoding();
        if (StringUtils.isNotBlank(encoding)) {
            requestContext.getBasicContext().setEncoding(encoding);
        }
    }
    
    private void setAddressContext(HttpServletRequest request, RequestContext requestContext) {
        String remoteAddress = request.getRemoteAddr();
        int remotePort = request.getRemotePort();
        String sourceIp = WebUtils.getRemoteIp(request);
        String host = request.getHeader(HttpHeaders.HOST);
        requestContext.getBasicContext().getAddressContext().setRemoteIp(remoteAddress);
        requestContext.getBasicContext().getAddressContext().setRemotePort(remotePort);
        requestContext.getBasicContext().getAddressContext().setSourceIp(sourceIp);
        requestContext.getBasicContext().getAddressContext().setHost(host);
    }
    
    private void setOtherBasicContext(HttpServletRequest request, RequestContext requestContext) {
        String userAgent = WebUtils.getUserAgent(request);
        requestContext.getBasicContext().setUserAgent(userAgent);
        String app = getAppName(request);
        if (StringUtils.isNotBlank(app)) {
            requestContext.getBasicContext().setApp(app);
        }
    }
    
    private String getAppName(HttpServletRequest request) {
        String app = request.getHeader(HttpHeaderConsts.APP_FILED);
        if (StringUtils.isBlank(app)) {
            app = request.getHeader(CLIENT_APPNAME_HEADER);
        }
        return app;
    }
}
