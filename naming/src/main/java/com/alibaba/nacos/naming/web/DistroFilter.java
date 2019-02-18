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

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jmx.export.UnableToRegisterMBeanException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author nacos
 */
public class DistroFilter implements Filter {

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private SwitchDomain switchDomain;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @SuppressFBWarnings("HRS_REQUEST_PARAMETER_TO_HTTP_HEADER")
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

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

        String serviceName = req.getParameter(CommonParams.SERVICE_NAME);

        if (StringUtils.isNoneBlank(serviceName) && !HttpMethod.GET.name().equals(req.getMethod())
            && !distroMapper.responsible(serviceName)) {

            String url = "http://" + distroMapper.mapSrv(serviceName) +
                req.getRequestURI() + "?" + req.getQueryString();
            try {
                resp.setCharacterEncoding("utf-8");
                resp.getWriter().write(distroMapper.mapSrv(serviceName));
                resp.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
            } catch (Exception ignore) {
                Loggers.SRV_LOG.warn("[DISTRO-FILTER] request failed: " + url);
            }
            return;
        }

        String groupName = req.getParameter(CommonParams.GROUP_NAME);
        if (StringUtils.isBlank(groupName)) {
            groupName = UtilsAndCommons.DEFAULT_GROUP_NAME;
        }

        OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(req);
        requestWrapper.addParameter(CommonParams.SERVICE_NAME, groupName + UtilsAndCommons.GROUP_SERVICE_CONNECTOR + serviceName);

        filterChain.doFilter(requestWrapper, resp);
    }

    @Override
    public void destroy() {

    }
}
