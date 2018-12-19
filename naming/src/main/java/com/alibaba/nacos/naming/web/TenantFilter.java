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
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A filter to intercept tenant parameter.
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @since 0.8.0
 */
public class TenantFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String tenantId = WebUtils.optional(req, Constants.REQUEST_PARAM_TENANT_ID, StringUtils.EMPTY);
        String serviceName = WebUtils.optional(req, Constants.REQUEST_PARAM_SERVICE_NAME, StringUtils.EMPTY);

        if (StringUtils.isBlank(tenantId) || StringUtils.isBlank(serviceName)) {
            chain.doFilter(req, resp);
            return;
        }

        OverrideParameterRequestWrapper requestWrapper = new OverrideParameterRequestWrapper(req);
        requestWrapper.addParameter(Constants.REQUEST_PARAM_SERVICE_NAME,
            serviceName + UtilsAndCommons.SERVICE_TENANT_CONNECTOR + tenantId);


        chain.doFilter(requestWrapper, resp);
    }

    @Override
    public void destroy() {

    }
}
