/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.utils;

import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Request Utils.
 *
 * @author xiweng.yy
 */
public class RequestUtil {
    
    /**
     * Transfer {@link Service} to HTTP API request parameters.
     *
     * @param service {@link Service} object
     * @return HTTP API request parameters
     */
    public static Map<String, String> toParameters(Service service) {
        Map<String, String> params = new HashMap<>(8);
        params.put("namespaceId", service.getNamespaceId());
        params.put("groupName", service.getGroupName());
        params.put("serviceName", service.getName());
        params.put("metadata", JacksonUtils.toJson(service.getMetadata()));
        params.put("ephemeral", String.valueOf(service.isEphemeral()));
        params.put("protectThreshold", String.valueOf(service.getProtectThreshold()));
        params.put("selector", JacksonUtils.toJson(service.getSelector()));
        return params;
    }
}
