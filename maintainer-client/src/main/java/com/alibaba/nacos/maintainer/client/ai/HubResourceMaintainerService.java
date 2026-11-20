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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareResourceRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.annotation.Since;

/**
 * Management-only Hub operations for mcp, agent, prompt and agentspec.
 * Content reads continue through each resource's existing business facade.
 */
@Since("3.3.0")
interface HubResourceMaintainerService {
    
    /** Share an explicit published source version, requiring independent source READ and WRITE. */
    HubShareResult share(String resourceType, HubShareResourceRequest request)
        throws NacosException;
    
    /** Adjust the single shared version with optimistic metadata revision checking. */
    HubShareResult adjust(String resourceType, HubAdjustShareRequest request) throws NacosException;
    
    /** Revoke an entry with source WRITE and optimistic revision checking. */
    HubShareResult unshare(String resourceType, HubUnshareRequest request) throws NacosException;
    
    /** Revoke an entry with independent administrator authority and a recorded reason. */
    HubShareResult forceUnshare(String resourceType, HubForceUnshareRequest request)
        throws NacosException;
    
    /** Read exact mutation state after source WRITE authorization; null means never shared. */
    HubResourceShareState sourceState(String resourceType, String namespaceId, String name)
        throws NacosException;
    
    /** Read exact governance state after administrator authorization. */
    HubResourceShareState governanceState(String resourceType, Long entryId) throws NacosException;
    
}
