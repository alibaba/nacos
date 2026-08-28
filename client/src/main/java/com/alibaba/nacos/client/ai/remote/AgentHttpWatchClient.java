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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * HTTP binding used by the Agent Watch batch transport.
 *
 * @author Nacos
 */
public interface AgentHttpWatchClient {
    
    /**
     * Execute one request-scoped Agent Watch batch long poll.
     *
     * @param request complete current Watch generation
     * @return opaque invalidation response
     * @throws NacosException when the HTTP binding cannot complete the request
     */
    AgentWatchBatchResponse watchAgents(AgentWatchBatchRequest request) throws NacosException;
}
