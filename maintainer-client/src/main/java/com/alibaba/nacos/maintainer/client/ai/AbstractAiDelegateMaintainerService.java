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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

import java.util.Map;

abstract class AbstractAiDelegateMaintainerService {
    
    protected final AiMaintainerHttpContext context;
    
    protected AbstractAiDelegateMaintainerService(AiMaintainerHttpContext context) {
        this.context = context;
    }
    
    protected HttpRestResult<String> executeSyncHttpRequest(HttpRequest request)
        throws NacosException {
        return context.getClientHttpProxy().executeSyncHttpRequest(request);
    }
    
    protected String resolveNamespace(String namespaceId) {
        return context.resolveNamespace(namespaceId);
    }
    
    protected RequestResource buildRequestResource(String namespaceId, String resourceName) {
        return context.buildRequestResource(namespaceId, resourceName);
    }
    
    protected HttpRequest.Builder buildHttpRequestBuilder(RequestResource resource) {
        return context.buildHttpRequestBuilder(resource);
    }
    
    protected void putIfNotBlank(Map<String, String> params, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            params.put(key, value);
        }
    }
}
