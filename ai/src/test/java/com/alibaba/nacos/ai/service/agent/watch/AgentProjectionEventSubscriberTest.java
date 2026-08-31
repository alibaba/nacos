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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.event.AiResourceChangeOperation;
import com.alibaba.nacos.ai.event.AiResourceChangedEvent;
import com.alibaba.nacos.ai.service.resource.AiResourceChangeNotifier;
import com.alibaba.nacos.ai.service.resource.AiResourceClusterChangePublisher;
import com.alibaba.nacos.ai.service.resource.NotifyCenterAiResourceChangeNotifier;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AgentProjectionEventSubscriberTest {
    
    @AfterEach
    void tearDown() {
        AiResourceStorageRouter.reset();
    }
    
    @Test
    void testNoopNotifierAcceptsDefinitionChange() {
        assertDoesNotThrow(() -> AiResourceChangeNotifier.NOOP.notifyChanged("tenant", "agent",
            "AgentA", AiResourceChangeOperation.UPDATE, false));
    }
    
    @Test
    void testMapsLogicalAndAgentRuntimeEvents() {
        AgentProjectionService projectionService = mock(AgentProjectionService.class);
        AgentProjectionEventSubscriber subscriber =
            new AgentProjectionEventSubscriber(projectionService);
        AiResourceChangedEvent definition = new AiResourceChangedEvent("tenant", "agent",
            "AgentA", AiResourceChangeOperation.UPDATE, false);
        AiResourceChangedEvent skill = new AiResourceChangedEvent("tenant", "skill", "SkillA",
            AiResourceChangeOperation.UPDATE, true);
        Service runtime = Service.newService("tenant", Constants.Agent.AGENT_ENDPOINT_GROUP,
            "rad-AgentA-a2a");
        Service ordinary = Service.newService("tenant", "DEFAULT_GROUP", "ordinary");
        
        assertEquals(Arrays.asList(AiResourceChangedEvent.class,
            ServiceEvent.ServiceChangedEvent.class), subscriber.subscribeTypes());
        assertEquals("tenant", definition.getNamespaceId());
        assertEquals("AgentA", definition.getResourceName());
        subscriber.onEvent(definition);
        subscriber.onEvent(skill);
        subscriber.onEvent(new ServiceEvent.ServiceChangedEvent(runtime, "instances"));
        subscriber.onEvent(new ServiceEvent.ServiceChangedEvent(ordinary, "instances"));
        subscriber.onStorageChanged(
            new AiResourceStorageChangeEvent("nacos_config", "agent", "agent-key"));
        subscriber.onStorageChanged(
            new AiResourceStorageChangeEvent("nacos_config", "skill", "skill-key"));
        subscriber.onStorageChanged(null);
        
        verify(projectionService).onAgentChanged("tenant", "AgentA");
        verify(projectionService).onAgentStorageChanged();
        verify(projectionService).onRuntimeServiceChanged(runtime);
        verify(projectionService, never()).onRuntimeServiceChanged(ordinary);
        verify(projectionService, never()).onAgentChanged("tenant", "SkillA");
    }
    
    @Test
    void testLifecycleRegistersWithNamingPublisher() {
        AgentProjectionEventSubscriber subscriber =
            new AgentProjectionEventSubscriber(mock(AgentProjectionService.class));
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            subscriber.register();
            subscriber.deregister();
            notifyCenter.verify(() -> NotifyCenter.registerSubscriber(subscriber,
                NamingEventPublisherFactory.getInstance()));
            notifyCenter.verify(() -> NotifyCenter.deregisterSubscriber(subscriber));
        }
    }
    
    @Test
    void testNotifierPublishesDefinitionEvent() {
        NotifyCenterAiResourceChangeNotifier notifier =
            new NotifyCenterAiResourceChangeNotifier();
        AiResourceClusterChangePublisher clusterChangePublisher =
            mock(AiResourceClusterChangePublisher.class);
        notifier.setClusterChangePublisher(clusterChangePublisher);
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            notifier.notifyChanged("tenant", "agent", "AgentA",
                AiResourceChangeOperation.UPDATE, true);
            notifyCenter.verify(() -> NotifyCenter.publishEvent(eventCaptor.capture()));
            assertTrue(eventCaptor.getValue() instanceof AiResourceChangedEvent);
            AiResourceChangedEvent event = (AiResourceChangedEvent) eventCaptor.getValue();
            assertEquals("tenant", event.getNamespaceId());
            assertEquals("AgentA", event.getResourceName());
            assertTrue(event.isStorageChanged());
            verify(clusterChangePublisher).publish(event);
        }
    }
    
    @Test
    void testNotifierIgnoresNullOptionalClusterPublisher() {
        NotifyCenterAiResourceChangeNotifier notifier =
            new NotifyCenterAiResourceChangeNotifier();
        notifier.setClusterChangePublisher(null);
        try (MockedStatic<NotifyCenter> notifyCenter = mockStatic(NotifyCenter.class)) {
            ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            notifier.notifyChanged("tenant", "agent", "AgentA",
                AiResourceChangeOperation.CREATE, false);
            notifyCenter.verify(() -> NotifyCenter.publishEvent(eventCaptor.capture()));
            assertTrue(eventCaptor.getValue() instanceof AiResourceChangedEvent);
        }
    }
    
    @Test
    void testNotifyCenterDeliversLogicalAndRuntimeEventsThroughNamingPublisher() {
        AgentProjectionService projectionService = mock(AgentProjectionService.class);
        AgentProjectionEventSubscriber subscriber =
            new AgentProjectionEventSubscriber(projectionService);
        Service runtime = Service.newService("tenant", Constants.Agent.AGENT_ENDPOINT_GROUP,
            "rad-AgentA-a2a");
        subscriber.register();
        try {
            new NotifyCenterAiResourceChangeNotifier().notifyChanged("tenant", "agent",
                "AgentA", AiResourceChangeOperation.UPDATE, false);
            NotifyCenter.publishEvent(
                new ServiceEvent.ServiceChangedEvent(runtime, "instances"));
            
            verify(projectionService, timeout(3000L)).onAgentChanged("tenant", "AgentA");
            verify(projectionService, timeout(3000L)).onRuntimeServiceChanged(runtime);
        } finally {
            subscriber.deregister();
        }
    }
}
