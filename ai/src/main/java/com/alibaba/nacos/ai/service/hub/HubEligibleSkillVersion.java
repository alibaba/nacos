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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;

/**
 * One qualified source Skill version and the reference pinned to its database IDs.
 *
 * <p>This is a validation snapshot, not authorization or a database lock. Callers must
 * recheck eligibility and permissions before committing a Hub write.</p>
 */
public final class HubEligibleSkillVersion {
    
    private final AiResource sourceMeta;
    
    private final AiResourceVersion sourceVersion;
    
    private final HubResourceReference reference;
    
    HubEligibleSkillVersion(AiResource sourceMeta, AiResourceVersion sourceVersion,
        HubResourceReference reference) {
        this.sourceMeta = sourceMeta;
        this.sourceVersion = sourceVersion;
        this.reference = reference;
    }
    
    public AiResource getSourceMeta() {
        return sourceMeta;
    }
    
    public AiResourceVersion getSourceVersion() {
        return sourceVersion;
    }
    
    public HubResourceReference getReference() {
        return reference;
    }
}
