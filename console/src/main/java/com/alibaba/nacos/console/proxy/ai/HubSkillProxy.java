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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.HubSkillHandler;
import org.springframework.stereotype.Component;

/**
 * Hub Skill console proxy.
 *
 * @author nacos
 */
@Component
public class HubSkillProxy {
    
    private final HubSkillHandler hubSkillHandler;
    
    public HubSkillProxy(HubSkillHandler hubSkillHandler) {
        this.hubSkillHandler = hubSkillHandler;
    }
    
    /**
     * Delegate the Hub Skill share operation.
     *
     * @param request validated request
     * @return operation result
     * @throws NacosException if the operation fails
     */
    public HubShareResult share(HubShareSkillRequest request) throws NacosException {
        return hubSkillHandler.share(request);
    }
    
    /**
     * Delegate the Hub Skill adjust operation.
     *
     * @param request validated request
     * @return operation result
     * @throws NacosException if the operation fails
     */
    public HubShareResult adjust(HubAdjustShareRequest request) throws NacosException {
        return hubSkillHandler.adjust(request);
    }
    
    /**
     * Delegate the Hub Skill unshare operation.
     *
     * @param request validated request
     * @return operation result
     * @throws NacosException if the operation fails
     */
    public HubShareResult unshare(HubUnshareRequest request) throws NacosException {
        return hubSkillHandler.unshare(request);
    }
    
    /**
     * Delegate the Hub Skill forceUnshare operation.
     *
     * @param request validated request
     * @return operation result
     * @throws NacosException if the operation fails
     */
    public HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException {
        return hubSkillHandler.forceUnshare(request);
    }
}
