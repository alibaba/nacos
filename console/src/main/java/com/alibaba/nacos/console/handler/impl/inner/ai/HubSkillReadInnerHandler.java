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

package com.alibaba.nacos.console.handler.impl.inner.ai;

import com.alibaba.nacos.ai.service.hub.HubSkillReadService;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.HubSkillReadHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.stereotype.Component;

@Component
@EnabledInnerHandler
@EnabledAiHandler
public class HubSkillReadInnerHandler implements HubSkillReadHandler {
    
    private final HubSkillReadService service;
    
    public HubSkillReadInnerHandler(HubSkillReadService service) {
        this.service = service;
    }
    
    @Override
    public Page<HubSkillSummary> list(HubSkillListRequest request) throws NacosException {
        return service.list(request);
    }
    
    @Override
    public HubSkillDetail detail(Long hubEntryId) throws NacosException {
        return service.getDetail(hubEntryId);
    }
    
    @Override
    public HubSkillContent content(Long hubEntryId) throws NacosException {
        return service.getContent(hubEntryId);
    }
    
    @Override
    public HubSkillDownload download(Long hubEntryId) throws NacosException {
        return service.download(hubEntryId);
    }
}
