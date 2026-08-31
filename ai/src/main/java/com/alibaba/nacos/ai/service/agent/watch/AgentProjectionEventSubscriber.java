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
import com.alibaba.nacos.ai.event.AgentDefinitionChangedEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Maps logical Agent and physical Naming changes to active shared Projections.
 *
 * @author Nacos
 */
@Component
public class AgentProjectionEventSubscriber extends SmartSubscriber {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AgentProjectionEventSubscriber.class);
    
    private final AgentProjectionService projectionService;
    
    public AgentProjectionEventSubscriber(AgentProjectionService projectionService) {
        this.projectionService = projectionService;
    }
    
    @PostConstruct
    public void register() {
        NotifyCenter.registerSubscriber(this, NamingEventPublisherFactory.getInstance());
    }
    
    @PreDestroy
    public void deregister() {
        NotifyCenter.deregisterSubscriber(this);
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        return Arrays.<Class<? extends Event>>asList(AgentDefinitionChangedEvent.class,
            ServiceEvent.ServiceChangedEvent.class);
    }
    
    @Override
    public void onEvent(Event event) {
        if (event instanceof AgentDefinitionChangedEvent) {
            AgentDefinitionChangedEvent changed = (AgentDefinitionChangedEvent) event;
            projectionService.onAgentChanged(changed.getNamespaceId(), changed.getAgentName());
            return;
        }
        Service service = ((ServiceEvent.ServiceChangedEvent) event).getService();
        if (Constants.Agent.AGENT_ENDPOINT_GROUP.equals(service.getGroup())) {
            LOGGER.debug("Runtime service change invalidates Agent projections.");
            projectionService.onRuntimeServiceChanged(service);
        }
    }
}
