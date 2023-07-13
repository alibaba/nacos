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

package com.alibaba.nacos.console.filter;

import com.alibaba.nacos.common.paramcheck.AbstractParamChecker;
import com.alibaba.nacos.common.paramcheck.ParamCheckerManager;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.HttpParamExtractorManager;
import com.alibaba.nacos.core.paramcheck.ServerParamCheckConfig;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * console param check filter.
 *
 * @author zhuoguang
 */
public class ConsoleParamCheckFilter implements Filter {
    
    private static final String MODULE = "console";
    
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
            String uri = req.getRequestURI();
            String method = req.getMethod();
            HttpParamExtractorManager extractorManager = HttpParamExtractorManager.getInstance();
            AbstractHttpParamExtractor paramExtractor = extractorManager.getExtractor(uri, method, MODULE);
            List<ParamInfo> paramInfoList = paramExtractor.extractParam(req);
            ParamCheckerManager paramCheckerManager = ParamCheckerManager.getInstance();
            AbstractParamChecker paramChecker = paramCheckerManager.getParamChecker(ServerParamCheckConfig.getInstance().getActiveParamChecker());
            paramChecker.checkParamInfoList(paramInfoList);
            chain.doFilter(request, resp);
        } catch (Exception e) {
            resp.setStatus(400);
            PrintWriter writer = resp.getWriter();
            writer.print(e.getMessage());
            writer.flush();
        }
    }
}
