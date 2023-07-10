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

package com.alibaba.nacos.config.server.filter;

import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.HttpParamExtractorManager;
import com.alibaba.nacos.sys.env.EnvUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Config param check filter.
 *
 * @author zhuoguang
 */
public class ConfigParamCheckFilter implements Filter {
    
    private static final String MODULE = "config";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        boolean ifParamCheck = EnvUtil.getProperty("nacos.paramcheck", Boolean.class, true);
        if (!ifParamCheck) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        try {
            String uri = req.getRequestURI();
            String method = req.getMethod();
            HttpParamExtractorManager extractorManager = HttpParamExtractorManager.getInstance();
            AbstractHttpParamExtractor paramExtractor = extractorManager.getExtractor(uri, method, MODULE);
            paramExtractor.extractParamAndCheck(req);
            chain.doFilter(req, resp);
        } catch (Exception e) {
            resp.setStatus(400);
            PrintWriter writer = resp.getWriter();
            writer.print(e.getMessage());
            writer.flush();
        }
    }
}
