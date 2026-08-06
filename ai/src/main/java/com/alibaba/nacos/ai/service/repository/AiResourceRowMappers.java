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
import com.alibaba.nacos.persistence.repository.RowMapperManager;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Row mappers for ai_resource and ai_resource_version.
 */
@Component
public final class AiResourceRowMappers {
    
    static final RowMapper<AiResource> AI_RESOURCE_ROW_MAPPER = new AiResourceRowMapper();
    
    static final RowMapper<AiResourceVersion> AI_RESOURCE_VERSION_ROW_MAPPER =
        new AiResourceVersionRowMapper();
    
    static {
        RowMapperManager.registerRowMapper(
            AI_RESOURCE_ROW_MAPPER.getClass().getCanonicalName(), AI_RESOURCE_ROW_MAPPER);
        RowMapperManager.registerRowMapper(
            AI_RESOURCE_VERSION_ROW_MAPPER.getClass().getCanonicalName(),
            AI_RESOURCE_VERSION_ROW_MAPPER);
    }
    
    public AiResourceRowMappers() {
    }
    
    private static final class AiResourceRowMapper implements RowMapper<AiResource> {
        
        @Override
        public AiResource mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            AiResource result = new AiResource();
            result.setId(resultSet.getLong("id"));
            result.setGmtCreate(resultSet.getTimestamp("gmt_create"));
            result.setGmtModified(resultSet.getTimestamp("gmt_modified"));
            result.setName(resultSet.getString("name"));
            result.setType(resultSet.getString("type"));
            result.setDesc(resultSet.getString("c_desc"));
            result.setStatus(resultSet.getString("status"));
            result.setNamespaceId(resultSet.getString("namespace_id"));
            result.setBizTags(resultSet.getString("biz_tags"));
            result.setExt(resultSet.getString("ext"));
            result.setFrom(resultSet.getString("c_from"));
            result.setVersionInfo(resultSet.getString("version_info"));
            result.setMetaVersion(resultSet.getLong("meta_version"));
            result.setScope(resultSet.getString("scope"));
            result.setOwner(resultSet.getString("owner"));
            result.setDownloadCount(resultSet.getLong("download_count"));
            return result;
        }
    }
    
    private static final class AiResourceVersionRowMapper
        implements RowMapper<AiResourceVersion> {
        
        @Override
        public AiResourceVersion mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            AiResourceVersion result = new AiResourceVersion();
            result.setId(resultSet.getLong("id"));
            result.setGmtCreate(resultSet.getTimestamp("gmt_create"));
            result.setGmtModified(resultSet.getTimestamp("gmt_modified"));
            result.setType(resultSet.getString("type"));
            result.setAuthor(resultSet.getString("author"));
            result.setName(resultSet.getString("name"));
            result.setDesc(resultSet.getString("c_desc"));
            result.setStatus(resultSet.getString("status"));
            result.setVersion(resultSet.getString("version"));
            result.setNamespaceId(resultSet.getString("namespace_id"));
            result.setStorage(resultSet.getString("storage"));
            result.setPublishPipelineInfo(resultSet.getString("publish_pipeline_info"));
            result.setDownloadCount(resultSet.getLong("download_count"));
            return result;
        }
    }
}
