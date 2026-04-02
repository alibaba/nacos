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

import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.api.model.Page;

/**
 * Persist service for ai_resource_version.
 *
 * @author nacos
 * @since 3.2.0
 */
public interface AiResourceVersionPersistService {

    long insert(AiResourceVersion version);

    AiResourceVersion find(String namespaceId, String name, String type, String version);

    Page<AiResourceVersion> list(String namespaceId, String name, String type, String status, int pageNo, int pageSize);

    int delete(String namespaceId, String name, String type, String version);

    int deleteByName(String namespaceId, String name);

    int deleteByNameAndType(String namespaceId, String name, String type);

    int updateStatus(String namespaceId, String name, String type, String version, String status);

    int updateStorage(String namespaceId, String name, String type, String version, String storage);

    int updateStorageAndDesc(String namespaceId, String name, String type, String version, String storage, String desc);

    int updatePublishPipelineInfo(String namespaceId, String name, String type, String version, String publishPipelineInfo);

    /**
     * Increment download count for a specific version.
     *
     * @param namespaceId namespace ID
     * @param name        resource name
     * @param type        resource type
     * @param version     version string
     * @param increment   amount to add
     * @return number of rows affected
     */
    int incrementDownloadCount(String namespaceId, String name, String type, String version, long increment);
}

