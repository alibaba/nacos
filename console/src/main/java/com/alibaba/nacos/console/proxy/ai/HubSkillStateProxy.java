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

import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.ai.HubSkillStateHandler;
import org.springframework.stereotype.Component;

@Component
public class HubSkillStateProxy {
    
    private final HubSkillStateHandler handler;
    
    public HubSkillStateProxy(HubSkillStateHandler handler) {
        this.handler = handler;
    }
    
    /** Inspect the exact source share under source WRITE authorization. */
    public HubSkillShareState bySource(String namespaceId, String name) throws NacosException {
        return handler.bySource(namespaceId, name);
    }
    
    /** Inspect governance state under existing Console administrator authority. */
    public HubSkillShareState governanceState(Long id) throws NacosException {
        return handler.governanceState(id);
    }
}
