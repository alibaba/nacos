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
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Persistence operations for one shared Skill reference in the internal Hub namespace.
 *
 * <p>All writes are server-side only. No method grants access to Hub through ordinary
 * Skill APIs. Source eligibility and authorization are enforced by the caller.</p>
 */
public interface HubShareRepository {
    
    /** Reserve one disabled entry, idempotently by the stable internal name. */
    AiResource reserveEntry(HubResourceReference reference, String description)
        throws NacosException;
    
    /** Lookup a Hub entry by its generated immutable primary key. */
    AiResource findByEntryId(long hubEntryId);
    
    /** Lookup a Hub entry from its source identity, never by source name alone. */
    AiResource findBySource(HubResourceReference source) throws NacosException;
    
    /**
     * Prepare one immutable Hub version containing the exact source descriptor.
     * An existing version may only be reused when every source identity field matches.
     */
    AiResourceVersion ensureReferenceVersion(long hubEntryId,
        HubResourceReference reference) throws NacosException;
    
    /**
     * Validate an existing immutable Hub version without inserting or updating rows.
     *
     * @param hubEntryId immutable Hub entry ID
     * @param reference expected source descriptor
     * @return the matching, online Hub version
     * @throws NacosException if the entry or existing version is missing or inconsistent
     */
    AiResourceVersion requireReferenceVersion(long hubEntryId,
        HubResourceReference reference) throws NacosException;
    
    /** Atomically publish the prepared version as the sole effective reference. */
    boolean publish(long hubEntryId, long expectedMetaVersion,
        HubResourceReference reference) throws NacosException;
    
    /** Disable the effective entry without changing or deleting its source Skill. */
    boolean disable(long hubEntryId, long expectedMetaVersion) throws NacosException;
}
