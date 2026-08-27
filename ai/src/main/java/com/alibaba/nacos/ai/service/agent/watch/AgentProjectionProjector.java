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
 * Computes the current public fact for one shared Agent projection.
 *
 * @author Nacos
 */
public interface AgentProjectionProjector {
    
    /**
     * Recompute one projection from current durable and Runtime facts.
     *
     * @param key projection key
     * @return current projection state
     */
    AgentProjectionState project(AgentProjectionKey key);
}
