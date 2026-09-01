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
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AiResourceChangeClusterRequestHandlerTest {
    
    @Test
    void testInvalidRequestIsRejected() {
        AiResourceChangeClusterRequestHandler handler =
            new AiResourceChangeClusterRequestHandler();
        AiResourceChangeClusterRequest blankType = validRequest();
        blankType.setResourceType("");
        assertThrows(IllegalArgumentException.class,
            () -> handler.handle(blankType, mock(RequestMeta.class)));
        
        AiResourceChangeClusterRequest blankName = validRequest();
        blankName.setResourceName("");
        assertThrows(IllegalArgumentException.class,
            () -> handler.handle(blankName, mock(RequestMeta.class)));
        
        AiResourceChangeClusterRequest invalidOperation = validRequest();
        invalidOperation.setOperation("UPSERT");
        assertThrows(IllegalArgumentException.class,
            () -> handler.handle(invalidOperation, mock(RequestMeta.class)));
    }
    
    @Test
    void testLocalResourceEventIsPublished() {
        AiResourceChangeClusterRequestHandler handler =
            new AiResourceChangeClusterRequestHandler();
        AiResourceChangeClusterRequest request = validRequest();
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            AiResourceChangeClusterResponse response =
                handler.handle(request, mock(RequestMeta.class));
            
            assertTrue(response.isSuccess());
            notifyCenter.verify(() -> NotifyCenter.publishEvent(eventCaptor.capture()));
            AiResourceChangedEvent event = (AiResourceChangedEvent) eventCaptor.getValue();
            assertEquals("tenant", event.getNamespaceId());
            assertEquals("agent", event.getResourceType());
            assertEquals("agent-name", event.getResourceName());
            assertEquals(AiResourceChangeOperation.UPDATE, event.getOperation());
            assertTrue(event.isStorageChanged());
        }
    }
    
    private AiResourceChangeClusterRequest validRequest() {
        AiResourceChangeClusterRequest result = new AiResourceChangeClusterRequest();
        result.setNamespaceId("tenant");
        result.setResourceType("agent");
        result.setResourceName("agent-name");
        result.setOperation(AiResourceChangeOperation.UPDATE.name());
        result.setStorageChanged(true);
        return result;
    }
}
