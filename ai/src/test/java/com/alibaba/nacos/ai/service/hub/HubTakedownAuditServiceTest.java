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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.ai.AiResourceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/** Verifies that reasons and source identities reach Nacos's existing trace stream. */
class HubTakedownAuditServiceTest {
    
    @Test
    void forceUnshareEmitsReasonAndActor() throws Exception {
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            "ns-a", 101L, "skill-a", 501L, "1.0.0");
        try (MockedStatic<AiResourceTraceService> trace = mockStatic(AiResourceTraceService.class);
            MockedStatic<VisibilityHelper> context = mockStatic(VisibilityHelper.class)) {
            context.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("hub-admin");
            context.when(VisibilityHelper::resolveClientIp).thenReturn("127.0.0.1");
            new HubTakedownAuditService().recordUnshare(901L, reference, 8L, true,
                "policy violation");
            trace.verify(() -> AiResourceTraceService.logSuccess(eq("skill"), eq("hub-entry-901"),
                eq("1.0.0"), eq("HUB_FORCE_UNSHARE"), eq("hub-admin"), eq("127.0.0.1"),
                argThat(ext -> ext.contains("policy violation")
                    && ext.contains("sourceResourceId") && ext.contains("501"))));
        }
    }
    
    @Test
    void normalUnshareDoesNotClaimAdministrativeReason() throws Exception {
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            "ns-a", 101L, "skill-a", 501L, "1.0.0");
        try (MockedStatic<AiResourceTraceService> trace = mockStatic(AiResourceTraceService.class);
            MockedStatic<VisibilityHelper> context = mockStatic(VisibilityHelper.class)) {
            context.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("source-owner");
            context.when(VisibilityHelper::resolveClientIp).thenReturn("127.0.0.2");
            new HubTakedownAuditService().recordUnshare(901L, reference, 8L, false, null);
            trace.verify(() -> AiResourceTraceService.logSuccess(eq("skill"), eq("hub-entry-901"),
                eq("1.0.0"), eq("HUB_UNSHARE"), eq("source-owner"), eq("127.0.0.2"),
                argThat(ext -> !ext.contains("reason") && ext.contains("metaVersion"))));
        }
    }
    
    @Test
    void realTraceServiceBuildsActorReasonAndSourceIdentityEvent() throws Exception {
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            "ns-a", 101L, "skill-a", 501L, "1.0.0");
        try (MockedStatic<NotifyCenter> notify = mockStatic(NotifyCenter.class);
            MockedStatic<VisibilityHelper> context = mockStatic(VisibilityHelper.class)) {
            context.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("hub-admin");
            context.when(VisibilityHelper::resolveClientIp).thenReturn("127.0.0.1");
            new HubTakedownAuditService().recordUnshare(
                901L, reference, 8L, true, "policy violation");
            
            ArgumentCaptor<AiResourceTraceEvent> event =
                ArgumentCaptor.forClass(AiResourceTraceEvent.class);
            notify.verify(() -> NotifyCenter.publishEvent(event.capture()));
            AiResourceTraceEvent emitted = event.getValue();
            assertEquals("hub-admin", emitted.getOperator());
            assertEquals("127.0.0.1", emitted.getClientIp());
            assertEquals("skill", emitted.getResourceType());
            assertEquals("hub-entry-901", emitted.getResourceId());
            assertEquals("1.0.0", emitted.getVersion());
            assertEquals("HUB_FORCE_UNSHARE", emitted.getOperation());
            assertEquals("SUCCESS", emitted.getStatus());
            Map<?, ?> details = JacksonUtils.toObj(emitted.getExt(), Map.class);
            assertEquals(901L, ((Number) details.get("hubEntryId")).longValue());
            assertEquals(8L, ((Number) details.get("metaVersion")).longValue());
            assertEquals("ns-a", details.get("sourceNamespaceId"));
            assertEquals(101L, ((Number) details.get("sourceResourceId")).longValue());
            assertEquals(501L, ((Number) details.get("sourceVersionId")).longValue());
            assertEquals("policy violation", details.get("reason"));
        }
    }
    
    @Test
    void tracePublicationFailureDoesNotEscapeAuditBoundary() throws Exception {
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            "ns-a", 101L, "skill-a", 501L, "1.0.0");
        try (MockedStatic<NotifyCenter> notify = mockStatic(NotifyCenter.class);
            MockedStatic<VisibilityHelper> context = mockStatic(VisibilityHelper.class)) {
            context.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("hub-admin");
            context.when(VisibilityHelper::resolveClientIp).thenReturn("127.0.0.1");
            notify.when(() -> NotifyCenter.publishEvent(any(AiResourceTraceEvent.class)))
                .thenThrow(new IllegalStateException("trace unavailable"));
            
            assertDoesNotThrow(() -> new HubTakedownAuditService().recordUnshare(
                901L, reference, 8L, true, "policy violation"));
        }
    }
}
