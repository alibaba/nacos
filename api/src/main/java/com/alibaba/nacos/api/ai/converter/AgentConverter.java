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

package com.alibaba.nacos.api.ai.converter;

import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;

/**
 * Explicit, local conversion of a discovery snapshot into an application protocol model.
 *
 * @param <T> protocol model returned by this converter
 * @author Nacos
 * @since 3.3.0
 */
@FunctionalInterface
public interface AgentConverter<T> {
    
    /**
     * Convert a snapshot without changing it or performing network operations.
     *
     * @param source complete or legally filtered discovery snapshot
     * @return an independent protocol model
     * @throws IllegalArgumentException when the snapshot cannot be converted
     */
    T convert(AgentDiscoveryResult source);
}
