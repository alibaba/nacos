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

import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.ai.AiResourceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.BasicContext;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class AgentDiscoveryTraceServiceTest {
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testSourceAndResolvedVersionWithoutQueryOrSnapshotPayload() {
        BasicContext context = RequestContextHolder.getContext().getBasicContext();
        context.setApp("order-service");
        context.getAddressContext().setSourceIp("192.0.2.1");
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "reader");
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identity);
        AgentDiscoveryRequest request = request();
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setMetadataSelector(Collections.singletonMap("secret", "sensitive-filter"));
        request.setFilter(filter);
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setVersion("2.0.0");
        result.setDescription("sensitive-snapshot");
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            AgentDiscoveryTraceService.record(request, result, BasicContext.HTTP_PROTOCOL,
                "client", "watch-notify");
            AiResourceTraceEvent event = capturedEvent(notifyCenter);
            assertEquals("agent", event.getResourceType());
            assertEquals("demo", event.getResourceId());
            assertEquals("2.0.0", event.getVersion());
            assertEquals("reader", event.getOperator());
            assertEquals("192.0.2.1", event.getClientIp());
            assertEquals("DISCOVER", event.getOperation());
            assertEquals("SUCCESS", event.getStatus());
            JsonNode ext = JacksonUtils.toObj(event.getExt());
            assertEquals("tenant", ext.get("namespaceId").asText());
            assertEquals("order-service", ext.get("app").asText());
            assertEquals("HTTP", ext.get("protocol").asText());
            assertEquals("client", ext.get("clientId").asText());
            assertEquals("latest", ext.get("requestedLabel").asText());
            assertTrue(ext.get("subscriptionTriggered").asBoolean());
            assertFalse(ext.has("connectionId"));
            assertFalse(event.getExt().contains("sensitive"));
        }
    }
    
    @Test
    void testFailedLabelLookupDoesNotInventVersionOrDirectQueryAttribution() {
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            AgentDiscoveryTraceService.record(request(), null, BasicContext.GRPC_PROTOCOL,
                "connection", "future-trigger");
            AiResourceTraceEvent event = capturedEvent(notifyCenter);
            assertEquals("FAILURE", event.getStatus());
            assertNull(event.getVersion());
            JsonNode ext = JacksonUtils.toObj(event.getExt());
            assertEquals("connection", ext.get("connectionId").asText());
            assertEquals("latest", ext.get("requestedLabel").asText());
            assertFalse(ext.get("subscriptionTriggered").asBoolean());
            assertFalse(ext.has("clientId"));
        }
    }
    
    @Test
    void testInvalidReferenceStillRecordsFailureWithoutInventingIdentity() {
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            AgentDiscoveryTraceService.record(request, null, BasicContext.GRPC_PROTOCOL,
                "connection", null);
            AiResourceTraceEvent event = capturedEvent(notifyCenter);
            assertEquals("FAILURE", event.getStatus());
            assertNull(event.getResourceId());
            assertNull(event.getVersion());
            JsonNode ext = JacksonUtils.toObj(event.getExt());
            assertEquals("public", ext.get("namespaceId").asText());
            assertFalse(ext.has("requestedVersion"));
            assertFalse(ext.has("requestedLabel"));
            assertFalse(ext.get("subscriptionTriggered").asBoolean());
        }
    }
    
    @Test
    void testDiagnosticsAreBestEffort() {
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            notifyCenter.when(() -> NotifyCenter.publishEvent(any(AiResourceTraceEvent.class)))
                .thenThrow(new IllegalStateException("trace unavailable"));
            assertDoesNotThrow(() -> AgentDiscoveryTraceService.record(request(),
                new AgentDiscoveryResult(), BasicContext.HTTP_PROTOCOL, null, null));
            assertDoesNotThrow(() -> AgentDiscoveryTraceService.record(null, null,
                BasicContext.HTTP_PROTOCOL, null, null));
        }
    }
    
    private AgentDiscoveryRequest request() {
        AgentReference reference = new AgentReference();
        reference.setAgentName("demo");
        reference.setLabel("latest");
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("tenant");
        request.setReference(reference);
        return request;
    }
    
    private AiResourceTraceEvent capturedEvent(MockedStatic<NotifyCenter> notifyCenter) {
        ArgumentCaptor<AiResourceTraceEvent> event =
            ArgumentCaptor.forClass(AiResourceTraceEvent.class);
        notifyCenter.verify(() -> NotifyCenter.publishEvent(event.capture()));
        return event.getValue();
    }
}
