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

package com.alibaba.nacos.ai.service.trace;

import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.BasicContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Best-effort diagnostics for external Agent Discover queries.
 *
 * @author nacos
 */
public final class AgentDiscoveryTraceService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentDiscoveryTraceService.class);
    
    private AgentDiscoveryTraceService() {
    }
    
    /**
     * Record the outcome without letting diagnostic failures affect the query.
     *
     * @param request normalized Discover request
     * @param result resolved snapshot, or null when discovery failed
     * @param protocol HTTP or GRPC
     * @param sourceId HTTP Client id or gRPC connection id
     * @param trigger optional notification attribution header
     */
    public static void record(AgentDiscoveryRequest request, AgentDiscoveryResult result,
        String protocol, String sourceId, String trigger) {
        try {
            AgentReference reference = request.getReference();
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("namespaceId", request.getNamespaceId());
            ext.put("protocol", protocol);
            ext.put("app", RequestContextHolder.getContext().getBasicContext().getApp());
            ext.put(BasicContext.HTTP_PROTOCOL.equals(protocol) ? "clientId" : "connectionId",
                sourceId);
            ext.put("subscriptionTriggered",
                AiConstants.Agent.DISCOVERY_TRIGGER_WATCH_NOTIFY.equals(trigger));
            ext.put("requestedVersion", reference == null ? null : reference.getVersion());
            ext.put("requestedLabel", reference == null ? null : reference.getLabel());
            String version = result == null
                ? (reference == null ? null : reference.getVersion()) : result.getVersion();
            AiResourceTraceService.log("agent", reference == null ? null : reference.getAgentName(),
                version, AiResourceTraceService.OP_DISCOVER,
                result == null ? AiResourceTraceService.STATUS_FAILURE
                    : AiResourceTraceService.STATUS_SUCCESS,
                VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp(),
                JacksonUtils.toJson(ext));
        } catch (Exception e) {
            LOGGER.warn("Failed to publish Agent Discover trace event", e);
        }
    }
}
