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

import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

/**
 * Dedicated read contract for the global Hub Skill directory.
 *
 * <p>Implementations must enforce Hub share authorization and revalidate the
 * current Hub pointer, source metadata identity, pinned source version ID,
 * visibility and online state before returning results. Pagination totals are
 * computed after these eligibility checks, never by filtering one page.
 * Content reads must revalidate immediately before exposing source content.
 * A caller's ordinary source READ permission is independent from the narrowly
 * scoped authorization to read an actively shared pinned version.</p>
 *
 * @author nacos
 */
public interface HubSkillReadService {
    
    /**
     * Browse eligible Skill references with correct total count and pagination.
     *
     * @param request page and filter options, without a client namespace ID
     * @return only visible, active Hub Skill summaries
     * @throws NacosException if the request or search runtime is unavailable
     */
    Page<HubSkillSummary> list(HubSkillListRequest request) throws NacosException;
    
    /**
     * Read a single effective Hub reference, without returning its source storage.
     *
     * @param hubEntryId the Hub database entry ID, not a name or source ID
     * @return safe read-only details, including optional authorized source deep link
     * @throws NacosException if the entry is invalid, withdrawn, or not visible
     */
    HubSkillDetail getDetail(Long hubEntryId) throws NacosException;
    
    /**
     * Read the fixed shared version from its actual source storage.
     *
     * @param hubEntryId the Hub database entry ID
     * @return safe public content of the pinned version
     * @throws NacosException if authorization or any fresh eligibility check fails
     */
    HubSkillContent getContent(Long hubEntryId) throws NacosException;
    
    /**
     * Download real bytes of the fixed, currently eligible shared version.
     *
     * @param hubEntryId immutable Hub Entry ID
     * @return ZIP package with safe display metadata
     * @throws NacosException when authorization, eligibility or storage fails
     */
    HubSkillDownload download(Long hubEntryId) throws NacosException;
}
