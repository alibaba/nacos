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

package com.alibaba.nacos.auth.parser.grpc;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.remote.request.AbstractNamingRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Naming Grpc resource parser.
 *
 * @author xiweng.yy
 */
public class NamingGrpcResourceParser extends AbstractGrpcResourceParser {
    
    @Override
    protected String getNamespaceId(Request request) {
        if (request instanceof AbstractNamingRequest) {
            return ((AbstractNamingRequest) request).getNamespace();
        }
        return (String) ReflectUtils.getFieldValue(request, PropertyKeyConst.NAMESPACE, StringUtils.EMPTY);
    }
    
    @Override
    protected String getGroup(Request request) {
        String groupName;
        if (request instanceof AbstractNamingRequest) {
            groupName = ((AbstractNamingRequest) request).getGroupName();
        } else {
            groupName = (String) ReflectUtils.getFieldValue(request, CommonParams.GROUP_NAME, StringUtils.EMPTY);
        }
        return StringUtils.isBlank(groupName) ? StringUtils.EMPTY : groupName;
    }
    
    @Override
    protected String getResourceName(Request request) {
        String serviceName;
        if (request instanceof AbstractNamingRequest) {
            serviceName = ((AbstractNamingRequest) request).getServiceName();
        } else {
            serviceName = (String) ReflectUtils.getFieldValue(request, CommonParams.SERVICE_NAME, "");
        }
        return StringUtils.isBlank(serviceName) ? StringUtils.EMPTY : serviceName;
    }
}
