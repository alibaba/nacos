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
import com.alibaba.nacos.api.model.Page;

/**
 * Persist service for ai_resource.
 *
 * @author nacos
 * @since 3.2.0
 */
public interface AiResourcePersistService {

    long insert(AiResource resource);

    AiResource find(String namespaceId, String name, String type);

    Page<AiResource> list(String namespaceId, String type, String nameLike, String bizTagsLike, int pageNo, int pageSize);

    /**
     * Update meta with optimistic lock on meta_version.
     *
     * @return true if updated successfully (affectedRows == 1)
     */
    boolean updateMetaCas(String namespaceId, String name, String type, long expectedMetaVersion, AiResource newValue);

    int delete(String namespaceId, String name, String type);
}

