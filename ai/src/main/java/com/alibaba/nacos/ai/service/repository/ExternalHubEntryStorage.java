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
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** Exact Hub operations for external SQL databases. */
@Service
@Conditional(value = ConditionOnExternalStorage.class)
public class ExternalHubEntryStorage implements HubEntryStorage {
    
    private static final String COLUMNS = "id, gmt_create, gmt_modified, name, type, c_desc, "
        + "status, namespace_id, biz_tags, ext, c_from, version_info, meta_version, "
        + "scope, owner, download_count";
    
    private static final String SOURCE = "hub";
    
    private final JdbcTemplate jdbcTemplate;
    
    private final AiResourceMapper mapper;
    
    public ExternalHubEntryStorage() {
        this.jdbcTemplate = DynamicDataSource.getInstance().getDataSource().getJdbcTemplate();
        boolean logEnabled = EnvUtil.getProperty(
            CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        MapperManager manager = MapperManager.instance(logEnabled);
        String datasourceType = DynamicDataSource.getInstance().getDataSource().getDataSourceType();
        this.mapper = manager.findMapper(datasourceType, TableConstant.AI_RESOURCE);
    }
    
    @Override
    public boolean incrementDownloads(HubResourceType type, long entryId, long versionId,
        String internalName, long increment) {
        if (entryId <= 0 || versionId <= 0 || internalName == null || increment <= 0) {
            return false;
        }
        TransactionTemplate transaction = new TransactionTemplate(
            new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        return Boolean.TRUE.equals(transaction.execute(status -> {
            int version = jdbcTemplate.update(
                "UPDATE ai_resource_version SET download_count=download_count+? "
                    + "WHERE id=? AND namespace_id=? AND name=? AND type=?",
                increment, versionId, AiConstants.Hub.NAMESPACE_ID, internalName,
                type.resourceType());
            int meta = jdbcTemplate.update("UPDATE ai_resource SET download_count=download_count+? "
                + "WHERE id=? AND namespace_id=? AND name=? AND type=? AND c_from=?",
                increment, entryId, AiConstants.Hub.NAMESPACE_ID, internalName, type.resourceType(),
                SOURCE);
            if (version != 1 || meta != 1) {
                status.setRollbackOnly();
                return false;
            }
            return true;
        }));
    }
    
    @Override
    public AiResource findByInternalName(String internalName) {
        return findByInternalName(HubResourceType.SKILL, internalName);
    }
    
    @Override
    public AiResource findByInternalName(HubResourceType type, String internalName) {
        String sql = "SELECT " + COLUMNS + " FROM ai_resource WHERE namespace_id=? "
            + "AND name=? AND type=? AND c_from=?";
        try {
            return jdbcTemplate.queryForObject(sql,
                new Object[] {AiConstants.Hub.NAMESPACE_ID, internalName, type.resourceType(),
                    SOURCE},
                AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    @Override
    public AiResource findByEntryId(long hubEntryId) {
        return findByEntryId(HubResourceType.SKILL, hubEntryId);
    }
    
    @Override
    public AiResource findByEntryId(HubResourceType type, long hubEntryId) {
        String sql = "SELECT " + COLUMNS + " FROM ai_resource WHERE id=? "
            + "AND namespace_id=? AND type=? AND c_from=?";
        try {
            return jdbcTemplate.queryForObject(sql,
                new Object[] {hubEntryId, AiConstants.Hub.NAMESPACE_ID, type.resourceType(),
                    SOURCE},
                AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    @Override
    public long maxEntryId() {
        return maxEntryId(HubResourceType.SKILL);
    }
    
    @Override
    public long maxEntryId(HubResourceType type) {
        Long max = jdbcTemplate.queryForObject(
            "SELECT MAX(id) FROM ai_resource WHERE namespace_id=? AND type=? AND c_from=?",
            Long.class, AiConstants.Hub.NAMESPACE_ID, type.resourceType(), SOURCE);
        return max == null ? 0L : max;
    }
    
    @Override
    public Long nextEntryId(long afterId, long maxId) {
        return nextEntryId(HubResourceType.SKILL, afterId, maxId);
    }
    
    @Override
    public Long nextEntryId(HubResourceType type, long afterId, long maxId) {
        return jdbcTemplate.queryForObject(
            "SELECT MIN(id) FROM ai_resource WHERE namespace_id=? AND type=? AND c_from=? "
                + "AND id>? AND id<=?",
            Long.class,
            AiConstants.Hub.NAMESPACE_ID, type.resourceType(), SOURCE, afterId, maxId);
    }
    
    @Override
    public List<AiResource> entriesInIdRange(long fromId, long toId) {
        return entriesInIdRange(HubResourceType.SKILL, fromId, toId);
    }
    
    @Override
    public List<AiResource> entriesInIdRange(HubResourceType type, long fromId, long toId) {
        String sql = "SELECT " + COLUMNS + " FROM ai_resource WHERE namespace_id=? "
            + "AND type=? AND c_from=? AND id>=? AND id<=? ORDER BY id ASC";
        return jdbcTemplate.query(sql,
            new Object[] {AiConstants.Hub.NAMESPACE_ID, type.resourceType(), SOURCE, fromId, toId},
            AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
    }
    
    @Override
    public boolean updateMetaCas(AiResource existing, long expectedMetaVersion,
        AiResource next) {
        return updateMetaCas(HubResourceType.SKILL, existing, expectedMetaVersion, next);
    }
    
    @Override
    public boolean updateMetaCas(HubResourceType type, AiResource existing,
        long expectedMetaVersion,
        AiResource next) {
        if (existing == null || !AiConstants.Hub.NAMESPACE_ID.equals(existing.getNamespaceId())
            || !type.resourceType().equals(existing.getType())
            || !SOURCE.equals(existing.getFrom())) {
            return false;
        }
        String sql = "UPDATE ai_resource SET status=?, c_desc=?, biz_tags=?, ext=?, "
            + "version_info=?, meta_version=meta_version+1, gmt_modified="
            + mapper.getFunction("NOW()") + " WHERE id=? AND namespace_id=? AND name=? "
            + "AND type=? AND c_from=? AND meta_version=?";
        return jdbcTemplate.update(sql, next.getStatus(), next.getDesc(), next.getBizTags(),
            next.getExt(), next.getVersionInfo(), existing.getId(), AiConstants.Hub.NAMESPACE_ID,
            existing.getName(), type.resourceType(), SOURCE, expectedMetaVersion) == 1;
    }
}
