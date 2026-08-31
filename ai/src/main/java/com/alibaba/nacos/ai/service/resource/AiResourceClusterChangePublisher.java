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

import com.alibaba.nacos.ai.event.AiResourceChangedEvent;

/**
 * Publishes a best-effort logical AI resource change hint to peer server nodes.
 *
 * @author Nacos
 */
public interface AiResourceClusterChangePublisher {
    
    AiResourceClusterChangePublisher NOOP = new AiResourceClusterChangePublisher() {
        
        @Override
        public void publish(AiResourceChangedEvent event) {
        }
    };
    
    /**
     * Publish one hint without changing the owning business operation outcome.
     *
     * @param event logical resource change
     */
    void publish(AiResourceChangedEvent event);
}
