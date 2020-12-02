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
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import org.apache.commons.lang3.StringUtils;

/**
 * Distro service name tag generator for v1.x.
 *
 * @author xiweng.yy
 */
public class DistroServiceNameTagGenerator implements DistroTagGenerator {
    
    @Override
    public String getResponsibleTag(ReuseHttpServletRequest request) {
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
        return groupedServiceName;
    }
}
