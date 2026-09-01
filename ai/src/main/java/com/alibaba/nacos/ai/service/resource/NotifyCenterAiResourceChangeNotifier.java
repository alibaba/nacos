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

package com.alibaba.nacos.ai.service.resource;

import com.alibaba.nacos.ai.event.AiResourceChangeOperation;
import com.alibaba.nacos.ai.event.AiResourceChangedEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * NotifyCenter-backed AI resource change publisher.
 *
 * @author Nacos
 */
@Component
public class NotifyCenterAiResourceChangeNotifier implements AiResourceChangeNotifier {
    
    private AiResourceClusterChangePublisher clusterChangePublisher =
        AiResourceClusterChangePublisher.NOOP;
    
    @Autowired(required = false)
    public void setClusterChangePublisher(AiResourceClusterChangePublisher clusterChangePublisher) {
        if (clusterChangePublisher != null) {
            this.clusterChangePublisher = clusterChangePublisher;
        }
    }
    
    @Override
    public void notifyChanged(String namespaceId, String resourceType, String resourceName,
        AiResourceChangeOperation operation, boolean storageChanged) {
        AiResourceChangedEvent event = new AiResourceChangedEvent(namespaceId, resourceType,
            resourceName, operation, storageChanged);
        NotifyCenter.publishEvent(event);
        clusterChangePublisher.publish(event);
    }
}
