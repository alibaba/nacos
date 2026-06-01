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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

import java.util.Properties;

final class AiMaintainerHttpContext {
    
    private final ClientHttpProxy clientHttpProxy;
    
    AiMaintainerHttpContext(Properties properties) throws NacosException {
        this(new ClientHttpProxy(properties));
    }
    
    AiMaintainerHttpContext(ClientHttpProxy clientHttpProxy) {
        this.clientHttpProxy = clientHttpProxy;
        ParamUtil.initSerialization();
    }
    
    ClientHttpProxy getClientHttpProxy() {
        return clientHttpProxy;
    }
    
    String resolveNamespace(String namespaceId) {
        return StringUtils.isBlank(namespaceId) ? Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
    
    RequestResource buildRequestResource(String namespaceId, String resourceName) {
        RequestResource.Builder builder = RequestResource.aiBuilder();
        builder.setNamespace(namespaceId);
        builder.setGroup(Constants.DEFAULT_GROUP);
        builder.setResource(null == resourceName ? StringUtils.EMPTY : resourceName);
        return builder.build();
    }
    
    HttpRequest.Builder buildHttpRequestBuilder(RequestResource resource) {
        return new HttpRequest.Builder().setResource(resource);
    }
}
