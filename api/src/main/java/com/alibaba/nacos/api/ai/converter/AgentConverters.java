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

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;

/**
 * Offline protocol converters; obtaining a converter does not require an AiService.
 *
 * @author Nacos
 * @since 3.3.0
 */
public final class AgentConverters {
    
    private static final AgentConverter<AgentCard> A2A = new DefaultA2aAgentConverter();
    
    private AgentConverters() {
    }
    
    /**
     * Return a reusable A2A converter for Nacos AgentCard models.
     *
     * <p>Uses the first nonempty EndpointSet in discovery order, preserving its address order
     * and unhealthy addresses. The first address supplies the legacy preferred fields.
     * Filtered addresses are never restored from the native descriptor. Conversion does not
     * select a server, perform load balancing or depend on a client's lifecycle. Call it
     * explicitly after discovery or from the application listener.
     *
     * @return stateless converter producing a new independent AgentCard on every call
     */
    public static AgentConverter<AgentCard> a2a() {
        return A2A;
    }
}
