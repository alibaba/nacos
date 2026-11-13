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

import com.alibaba.nacos.ai.model.hub.HubResourceType;

/** ID- and metaVersion-pinned mutations used only for typed reserved draft metadata. */
public interface HubResourceDraftCommitStorage {
    
    boolean publish(HubResourceType type, long id, String namespaceId, String name,
        long expectedMetaVersion);
    
    boolean disable(HubResourceType type, long id, String namespaceId, String name,
        long expectedMetaVersion);
    
    boolean deleteDisabled(HubResourceType type, long id, String namespaceId, String name,
        long expectedMetaVersion);
    
    /** Delete only this draft primary key while its metadata reservation remains owned. */
    boolean deleteDraftVersion(HubResourceType type, long id, String namespaceId, String name,
        long expectedMetaVersion,
        long versionId, String version);
}
