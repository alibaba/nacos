/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.parser.http;

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;

/**
 * Naming Http resource parser.
 *
 * @author xiweng.yy
 */
public class NamingHttpResourceParser extends AbstractHttpResourceParser {
    
    @Override
    protected String getNamespaceId(HttpServletRequest request) {
        return NamespaceUtil.processNamespaceParameter(request.getParameter(CommonParams.NAMESPACE_ID));
        
    }
    
    /**
     * Group name from http request might be in service name with format ${group}@@${service}. So if group name is blank
     * or {@code null}, should try to get group name from service.
     *
     * @param request http request
     * @return group
     */
    @Override
    protected String getGroup(HttpServletRequest request) {
        String groupName = request.getParameter(CommonParams.GROUP_NAME);
        if (StringUtils.isBlank(groupName)) {
            String serviceName = request.getParameter(CommonParams.SERVICE_NAME);
            groupName = NamingUtils.getGroupName(serviceName);
        }
        return StringUtils.isBlank(groupName) ? StringUtils.EMPTY : groupName;
    }
    
    @Override
    protected String getResourceName(HttpServletRequest request) {
        // See comment in #getGroup
        String serviceName = NamingUtils.getServiceName(request.getParameter(CommonParams.SERVICE_NAME));
        return StringUtils.isBlank(serviceName) ? StringUtils.EMPTY : serviceName;
    }
    
    @Override
    protected Properties getProperties(HttpServletRequest request) {
        return new Properties();
    }
}
