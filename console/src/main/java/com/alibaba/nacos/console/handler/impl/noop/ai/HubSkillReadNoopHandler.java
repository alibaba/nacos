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

import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.HubSkillReadHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(value = HubSkillReadHandler.class,
    ignored = HubSkillReadNoopHandler.class)
public class HubSkillReadNoopHandler implements HubSkillReadHandler {
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, "AI Hub Skill read service is unavailable");
    }
    
    @Override
    public Page<HubSkillSummary> list(HubSkillListRequest request) throws NacosException {
        throw unavailable();
    }
    
    @Override
    public HubSkillDetail detail(Long id) throws NacosException {
        throw unavailable();
    }
    
    @Override
    public HubSkillContent content(Long id) throws NacosException {
        throw unavailable();
    }
    
    @Override
    public HubSkillDownload download(Long hubEntryId) throws NacosException {
        throw unavailable();
    }
}
