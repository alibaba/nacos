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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import org.springframework.jdbc.core.RowMapper;

/**
 * Row mappers for ai_resource and ai_resource_version.
 */
final class AiResourceRowMappers {
    
    private AiResourceRowMappers() {
    }
    
    static final RowMapper<AiResource> AI_RESOURCE_ROW_MAPPER = (rs, rowNum) -> {
        AiResource r = new AiResource();
        r.setId(rs.getLong("id"));
        r.setGmtCreate(rs.getTimestamp("gmt_create"));
        r.setGmtModified(rs.getTimestamp("gmt_modified"));
        r.setName(rs.getString("name"));
        r.setType(rs.getString("type"));
        r.setDesc(rs.getString("c_desc"));
        r.setStatus(rs.getString("status"));
        r.setNamespaceId(rs.getString("namespace_id"));
        r.setBizTags(rs.getString("biz_tags"));
        r.setExt(rs.getString("ext"));
        r.setFrom(rs.getString("c_from"));
        r.setVersionInfo(rs.getString("version_info"));
        r.setMetaVersion(rs.getLong("meta_version"));
        r.setScope(rs.getString("scope"));
        r.setOwner(rs.getString("owner"));
        r.setDownloadCount(rs.getLong("download_count"));
        return r;
    };
    
    static final RowMapper<AiResourceVersion> AI_RESOURCE_VERSION_ROW_MAPPER = (rs, rowNum) -> {
        AiResourceVersion v = new AiResourceVersion();
        v.setId(rs.getLong("id"));
        v.setGmtCreate(rs.getTimestamp("gmt_create"));
        v.setGmtModified(rs.getTimestamp("gmt_modified"));
        v.setType(rs.getString("type"));
        v.setAuthor(rs.getString("author"));
        v.setName(rs.getString("name"));
        v.setDesc(rs.getString("c_desc"));
        v.setStatus(rs.getString("status"));
        v.setVersion(rs.getString("version"));
        v.setNamespaceId(rs.getString("namespace_id"));
        v.setStorage(rs.getString("storage"));
        v.setPublishPipelineInfo(rs.getString("publish_pipeline_info"));
        v.setDownloadCount(rs.getLong("download_count"));
        return v;
    };
}
