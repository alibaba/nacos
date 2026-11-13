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

package com.alibaba.nacos.console.handler.ai;

import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.exception.NacosException;

/** Separate source-owner and governance-only read interfaces. */
public interface HubSkillStateHandler {
    
    /** Inspect source-owner share state. */
    HubSkillShareState bySource(String namespaceId, String sourceName) throws NacosException;
    
    /** Inspect the state of an entry for Hub governance. */
    HubSkillShareState governanceState(Long hubEntryId) throws NacosException;
}
