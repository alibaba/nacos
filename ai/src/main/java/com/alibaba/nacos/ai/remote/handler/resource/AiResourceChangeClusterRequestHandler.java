/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.remote.handler.resource;

import com.alibaba.nacos.ai.event.AiResourceChangeOperation;
import com.alibaba.nacos.ai.event.AiResourceChangedEvent;
import com.alibaba.nacos.api.ai.remote.request.cluster.AiResourceChangeClusterRequest;
import com.alibaba.nacos.api.ai.remote.response.cluster.AiResourceChangeClusterResponse;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.grpc.InvokeSource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.stereotype.Component;

/**
 * Publishes a peer server's logical AI resource change into the local event pipeline.
 *
 * @author Nacos
 */
@Since("3.4.0")
@Component
@InvokeSource(source = {RemoteConstants.LABEL_SOURCE_CLUSTER})
public class AiResourceChangeClusterRequestHandler extends
    RequestHandler<AiResourceChangeClusterRequest, AiResourceChangeClusterResponse> {
    
    private static final int MAX_RESOURCE_TYPE_LENGTH = 64;
    
    private static final int MAX_RESOURCE_NAME_LENGTH = 512;
    
    @Override
    @Secured(signType = SignType.AI, apiType = ApiType.INNER_API)
    public AiResourceChangeClusterResponse handle(AiResourceChangeClusterRequest request,
        RequestMeta meta) {
        AgentValidationUtils.validateNamespaceId(request.getNamespaceId());
        validateRequired(request.getResourceType(), MAX_RESOURCE_TYPE_LENGTH, "resourceType");
        validateRequired(request.getResourceName(), MAX_RESOURCE_NAME_LENGTH, "resourceName");
        AiResourceChangeOperation operation = parseOperation(request.getOperation());
        NotifyCenter.publishEvent(new AiResourceChangedEvent(request.getNamespaceId(),
            request.getResourceType(), request.getResourceName(), operation,
            request.isStorageChanged()));
        return new AiResourceChangeClusterResponse();
    }
    
    private void validateRequired(String value, int maxLength, String field) {
        if (StringUtils.isBlank(value) || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1 to " + maxLength
                + " characters");
        }
    }
    
    private AiResourceChangeOperation parseOperation(String operation) {
        try {
            return AiResourceChangeOperation.valueOf(operation);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unsupported AI resource change operation: "
                + operation, e);
        }
    }
}
