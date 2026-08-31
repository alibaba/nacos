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

import com.alibaba.nacos.ai.event.AgentDefinitionChangedEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * NotifyCenter-backed Agent Projection change publisher.
 *
 * @author Nacos
 */
@Component
public class NotifyCenterAgentProjectionChangeNotifier
    implements AgentProjectionChangeNotifier {
    
    private AgentProjectionClusterChangePublisher clusterChangePublisher =
        AgentProjectionClusterChangePublisher.NOOP;
    
    @Autowired(required = false)
    public void setClusterChangePublisher(
        AgentProjectionClusterChangePublisher clusterChangePublisher) {
        if (clusterChangePublisher != null) {
            this.clusterChangePublisher = clusterChangePublisher;
        }
    }
    
    @Override
    public void notifyDefinitionChanged(String namespaceId, String agentName) {
        NotifyCenter.publishEvent(new AgentDefinitionChangedEvent(namespaceId, agentName));
        clusterChangePublisher.publish(namespaceId, agentName);
    }
}
