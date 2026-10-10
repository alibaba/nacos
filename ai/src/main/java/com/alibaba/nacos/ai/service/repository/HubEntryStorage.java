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

package com.alibaba.nacos.ai.service.repository;

import com.alibaba.nacos.ai.model.AiResource;

/**
 * Hub-only metadata reads and identity-pinned compare-and-set operations.
 *
 * <p>All operations are constrained to the reserved Hub namespace, Skill type and
 * {@code c_from=hub}. This avoids changing ordinary AI resource persistence.</p>
 */
public interface HubEntryStorage {
    
    /** Find exactly one Hub entry by its stable internal name. */
    AiResource findByInternalName(String internalName);
    
    /** Find a Hub entry by immutable database ID, or null if it is not a Hub Skill. */
    AiResource findByEntryId(long hubEntryId);
    
    /**
     * Persist the complete effective Hub metadata in a single optimistic write.
     *
     * @return true only if this write took effect, false on optimistic conflict
     */
    boolean updateMetaCas(AiResource existing, long expectedMetaVersion, AiResource next);
}
