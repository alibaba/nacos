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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.HubSkillHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/** Disabled-AI fallback. Never silently accept a Hub mutation. */
@Service
@ConditionalOnMissingBean(value = HubSkillHandler.class,
    ignored = HubSkillNoopHandler.class)
public class HubSkillNoopHandler implements HubSkillHandler {
    
    private NacosApiException disabled() {
        return new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, "Nacos AI Hub Skill service is unavailable");
    }
    
    @Override
    public HubShareResult share(HubShareSkillRequest request) throws NacosException {
        throw disabled();
    }
    
    @Override
    public HubShareResult adjust(HubAdjustShareRequest request) throws NacosException {
        throw disabled();
    }
    
    @Override
    public HubShareResult unshare(HubUnshareRequest request) throws NacosException {
        throw disabled();
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request) throws NacosException {
        throw disabled();
    }
}
