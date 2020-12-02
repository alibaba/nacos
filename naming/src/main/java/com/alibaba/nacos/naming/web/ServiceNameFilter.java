/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.OverrideParameterRequestWrapper;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Service name filter. This class is created for adapting 1.x. client and old openAPI.
 * <p>
 * Because the old version will auto combined group and serviceName in old {@link DistroFilter}. So client and openAPI
 * can ignore group name.
 * </p>
 *
 * @author xiweng.yy
 */
public class ServiceNameFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;
        try {
            String serviceName = request.getParameter(CommonParams.SERVICE_NAME);
            
            if (StringUtils.isNotBlank(serviceName)) {
                serviceName = serviceName.trim();
            }
            String groupName = request.getParameter(CommonParams.GROUP_NAME);
            if (StringUtils.isBlank(groupName)) {
                groupName = Constants.DEFAULT_GROUP;
            }
            
            // use groupName@@serviceName as new service name:
            String groupedServiceName = serviceName;
            if (StringUtils.isNotBlank(serviceName) && !serviceName.contains(Constants.SERVICE_INFO_SPLITER)) {
                groupedServiceName = groupName + Constants.SERVICE_INFO_SPLITER + serviceName;
            }
            OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(request);
            requestWrapper.addParameter(CommonParams.SERVICE_NAME, groupedServiceName);
            filterChain.doFilter(requestWrapper, servletResponse);
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Service name filter error," + ExceptionUtil.getAllExceptionMsg(e));
        }
    }
}
