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
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.model.Resource;
import com.alibaba.nacos.auth.parser.ResourceParser;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Naming resource parser.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class NamingResourceParser implements ResourceParser {
    
    private static final String AUTH_NAMING_PREFIX = "naming/";
    
    @Override
    public String parseName(Object request) {
        
        HttpServletRequest req = (HttpServletRequest) request;
        
        String namespaceId = NamespaceUtil.processNamespaceParameter(req.getParameter(CommonParams.NAMESPACE_ID));
        String serviceName = req.getParameter(CommonParams.SERVICE_NAME);
        String groupName = req.getParameter(CommonParams.GROUP_NAME);
        if (StringUtils.isBlank(groupName)) {
            groupName = NamingUtils.getGroupName(serviceName);
        }
        serviceName = NamingUtils.getServiceName(serviceName);
        
        StringBuilder sb = new StringBuilder();
        
        if (StringUtils.isNotBlank(namespaceId)) {
            sb.append(namespaceId);
        }
        
        sb.append(Resource.SPLITTER);
        
        if (StringUtils.isBlank(serviceName)) {
            sb.append("*").append(Resource.SPLITTER).append(AUTH_NAMING_PREFIX).append("*");
        } else {
            sb.append(groupName).append(Resource.SPLITTER).append(AUTH_NAMING_PREFIX).append(serviceName);
        }
        
        return sb.toString();
    }
}
