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

import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
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
import java.util.Map;

/**
 * Filter incoming traffic to refuse or revise unexpected requests.
 *
 * @author nkorange
 * @since 1.0.0
 */
public class TrafficReviseFilter implements Filter {
    
    @Autowired
    private ServerStatusManager serverStatusManager;
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        // request limit if exist:
        String urlString = req.getRequestURI() + "?" + req.getQueryString();
        Map<String, Integer> limitedUrlMap = switchDomain.getLimitedUrlMap();
        
        if (limitedUrlMap != null && limitedUrlMap.size() > 0) {
            for (Map.Entry<String, Integer> entry : limitedUrlMap.entrySet()) {
                String limitedUrl = entry.getKey();
                if (StringUtils.startsWith(urlString, limitedUrl)) {
                    resp.setStatus(entry.getValue());
                    return;
                }
            }
        }
        
        // if server is UP:
        if (serverStatusManager.getServerStatus() == ServerStatus.UP) {
            filterChain.doFilter(req, resp);
            return;
        }
        
        // requests from peer server should be let pass:
        String agent = WebUtils.getUserAgent(req);
        
        if (StringUtils.startsWith(agent, Constants.NACOS_SERVER_HEADER)) {
            filterChain.doFilter(req, resp);
            return;
        }
        
        // write operation should be let pass in WRITE_ONLY status:
        if (serverStatusManager.getServerStatus() == ServerStatus.WRITE_ONLY && !HttpMethod.GET
                .equals(req.getMethod())) {
            filterChain.doFilter(req, resp);
            return;
        }
        
        // read operation should be let pass in READ_ONLY status:
        if (serverStatusManager.getServerStatus() == ServerStatus.READ_ONLY && HttpMethod.GET.equals(req.getMethod())) {
            filterChain.doFilter(req, resp);
            return;
        }
        
        final String statusMsg = "server is " + serverStatusManager.getServerStatus().name() + "now";
        if (serverStatusManager.getErrorMsg().isPresent()) {
            resp.getWriter().write(statusMsg + ", detailed error message: " + serverStatusManager.getErrorMsg());
        } else {
            resp.getWriter().write(statusMsg  + ", please try again later!");
        }
        resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
