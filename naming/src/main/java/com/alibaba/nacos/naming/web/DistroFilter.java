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

import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.raft.RaftCore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author nacos
 */
public class DistroFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @SuppressFBWarnings("HRS_REQUEST_PARAMETER_TO_HTTP_HEADER")
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        String urlString = req.getRequestURI() + "?" + req.getQueryString();
        Map<String, Integer> limitedUrlMap = Switch.getLimitedUrlMap();

        if (limitedUrlMap != null && limitedUrlMap.size() > 0) {
            for (Map.Entry<String, Integer> entry : limitedUrlMap.entrySet()) {
                String limitedUrl = entry.getKey();
                if (StringUtils.startsWith(urlString, limitedUrl)) {
                    resp.setStatus(entry.getValue());
                    return;
                }
            }
        }

        if (req.getRequestURI().contains(UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT) && !RaftCore.isLeader()) {

            if (HttpMethod.PUT.name().equals(req.getMethod()) && HttpMethod.DELETE.name().equals(req.getMethod())) {
                String url = "http://" + RaftCore.getLeader().ip + req.getRequestURI() + "?" + req.getQueryString();
                try {
                    resp.sendRedirect(url);
                } catch (Exception ignore) {
                    Loggers.SRV_LOG.warn("[DISTRO-FILTER] request failed: " + url);
                }
                return;
            }
        }

        if (!Switch.isDistroEnabled()) {
            filterChain.doFilter(req, resp);
            return;
        }

        if (!canDistro(urlString)) {
            filterChain.doFilter(req, resp);
            return;
        }

        String redirect = req.getParameter("redirect");
        String dom = req.getParameter("domainString");
        String targetIP = req.getParameter("targetIP");
        if (StringUtils.isEmpty(dom)) {
            dom = req.getParameter("dom");
        }

        if (StringUtils.isEmpty(dom)) {
            filterChain.doFilter(req, resp);
            return;
        }

        if (StringUtils.isEmpty(redirect) && StringUtils.isEmpty(targetIP)) {
            if (!DistroMapper.responsible(dom)) {

                String url = "http://" + DistroMapper.mapSrv(dom) + ":" + req.getServerPort()
                        + req.getRequestURI() + "?" + req.getQueryString();
                try {
                    resp.sendRedirect(url);
                } catch (Exception ignore) {
                    Loggers.SRV_LOG.warn("[DISTRO-FILTER] request failed: " + url);
                }
            }
        }

        filterChain.doFilter(req, resp);
    }

    @Override
    public void destroy() {

    }

    public boolean canDistro(String urlString) {
        return urlString.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.API_IP_FOR_DOM) ||
                urlString.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.API_DOM);
    }
}
