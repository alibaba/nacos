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

import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.HubSkillReadHandler;
import org.springframework.stereotype.Component;

@Component
public class HubSkillReadProxy {
    
    private final HubSkillReadHandler handler;
    
    public HubSkillReadProxy(HubSkillReadHandler handler) {
        this.handler = handler;
    }
    
    public Page<HubSkillSummary> list(HubSkillListRequest request) throws NacosException {
        return handler.list(request);
    }
    
    public HubSkillDetail detail(Long id) throws NacosException {
        return handler.detail(id);
    }
    
    public HubSkillContent content(Long id) throws NacosException {
        return handler.content(id);
    }
    
    public HubSkillDownload download(Long hubEntryId) throws NacosException {
        return handler.download(hubEntryId);
    }
}
