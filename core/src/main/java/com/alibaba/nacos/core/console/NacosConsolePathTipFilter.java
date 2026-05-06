/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.console;

import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * nacos console path filter.
 * @author cxhello
 * @date 2025/7/17
 */
public class NacosConsolePathTipFilter implements Filter {
    
    private static final String NACOS_SERVER_CONTEXT_PATH = "nacos.server.contextPath";
    
    private static final String NACOS_CONSOLE_PORT = "nacos.console.port";
    
    private static final String NACOS_CONSOLE_CONTEXT_PATH = "nacos.console.contextPath";
    
    private static final String NACOS_SERVER_DEFAULT_CONTEXT_PATH = "/nacos";
    
    private static final String NACOS_CONSOLE_DEFAULT_PORT = "8080";
    
    private static final String NACOS_CONSOLE_DEFAULT_PATH = "/";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
            String contextPath = normalizeContextPath(EnvUtil.getProperty(NACOS_SERVER_CONTEXT_PATH, NACOS_SERVER_DEFAULT_CONTEXT_PATH));
            String indexPath = contextPath + "index.html";
            String requestUri = httpServletRequest.getRequestURI();
            if (requestUri.equals(contextPath) || requestUri.equals(indexPath)) {
                writeConsoleInfo(httpServletResponse);
                return;
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
    
    private String normalizeContextPath(String contextPath) {
        if (StringUtils.isBlank(contextPath)) {
            return NACOS_CONSOLE_DEFAULT_PATH;
        }
        if (contextPath.length() > 1 && !contextPath.endsWith(NACOS_CONSOLE_DEFAULT_PATH)) {
            return contextPath + NACOS_CONSOLE_DEFAULT_PATH;
        }
        return contextPath;
    }
    
    private void writeConsoleInfo(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.setContentType(MediaType.TEXT_PLAIN);
        String port = EnvUtil.getProperty(NACOS_CONSOLE_PORT, NACOS_CONSOLE_DEFAULT_PORT);
        String consoleContextPath = EnvUtil.getProperty(NACOS_CONSOLE_CONTEXT_PATH);
        consoleContextPath = StringUtils.isBlank(consoleContextPath) ? NACOS_CONSOLE_DEFAULT_PATH : consoleContextPath;
        httpServletResponse.getWriter().write(String.format("Nacos Console default port is %s, and the path is %s.", port, consoleContextPath));
    }

}
