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

/**
 * Publishes a best-effort logical Agent change hint to peer server nodes.
 *
 * @author Nacos
 */
public interface AgentProjectionClusterChangePublisher {
    
    AgentProjectionClusterChangePublisher NOOP = new AgentProjectionClusterChangePublisher() {
        
        @Override
        public void publish(String namespaceId, String agentName) {
        }
    };
    
    /**
     * Publish one hint without changing the outcome of the owning business operation.
     *
     * @param namespaceId namespace identifier
     * @param agentName logical Agent name
     */
    void publish(String namespaceId, String agentName);
}
