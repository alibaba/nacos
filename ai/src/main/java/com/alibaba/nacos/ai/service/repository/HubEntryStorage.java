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

import java.util.List;

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
     * Find the maximum ID belonging to a Hub Skill entry at the start of a scan.
     * An empty Hub returns 0. This bounds a single directory listing run.
     *
     * @return upper bound on Hub entry IDs, or 0 for an empty directory
     */
    long maxEntryId();
    
    /**
     * Seek the smallest Hub entry ID strictly greater than {@code afterId} and
     * not greater than {@code maxId}. Returns null when the scan has finished.
     *
     * <p>Using an indexed ID seek prevents offset scans from skipping entries
     * when earlier rows change or are removed.</p>
     *
     * @param afterId exclusive starting ID, nonnegative
     * @param maxId inclusive upper bound captured at scan start
     * @return next Hub entry ID, or null
     */
    Long nextEntryId(long afterId, long maxId);
    
    /**
     * Read a bounded, ascending range of Hub Skill entries. The caller sets
     * a numeric ID window (not a row-count limit), so the SQL is portable to
     * Derby, MySQL, PostgreSQL and Oracle without dialect-specific LIMIT syntax.
     * The returned rows are not authorization decisions.
     *
     * @param fromId inclusive first ID
     * @param toId inclusive last ID
     * @return Hub Skill rows sorted by ID ascending
     */
    List<AiResource> entriesInIdRange(long fromId, long toId);
    
    /**
     * Persist the complete effective Hub metadata in a single optimistic write.
     *
     * @return true only if this write took effect, false on optimistic conflict
     */
    boolean updateMetaCas(AiResource existing, long expectedMetaVersion, AiResource next);
}
