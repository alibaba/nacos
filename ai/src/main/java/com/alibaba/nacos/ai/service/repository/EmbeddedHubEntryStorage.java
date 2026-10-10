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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/** Hub-only metadata operations routed through embedded consensus-backed database writes. */
@Service
@Conditional(value = ConditionOnEmbeddedStorage.class)
public class EmbeddedHubEntryStorage implements HubEntryStorage {
    
    private static final String COLUMNS = "id, gmt_create, gmt_modified, name, type, c_desc, "
        + "status, namespace_id, biz_tags, ext, c_from, version_info, meta_version, "
        + "scope, owner, download_count";
    
    private static final String RESOURCE_KIND = "skill";
    
    private static final String SOURCE = "hub";
    
    private final DatabaseOperate databaseOperate;
    
    private final AiResourceMapper mapper;
    
    public EmbeddedHubEntryStorage(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
        boolean logEnabled = EnvUtil.getProperty(
            CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        MapperManager manager = MapperManager.instance(logEnabled);
        String datasourceType = DynamicDataSource.getInstance().getDataSource().getDataSourceType();
        this.mapper = manager.findMapper(datasourceType, TableConstant.AI_RESOURCE);
    }
    
    @Override
    public AiResource findByInternalName(String internalName) {
        String sql = "SELECT " + COLUMNS + " FROM ai_resource WHERE namespace_id=? "
            + "AND name=? AND type=? AND c_from=?";
        return databaseOperate.queryOne(sql,
            new Object[] {AiConstants.Hub.NAMESPACE_ID, internalName, RESOURCE_KIND, SOURCE},
            AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
    }
    
    @Override
    public AiResource findByEntryId(long hubEntryId) {
        String sql = "SELECT " + COLUMNS + " FROM ai_resource WHERE id=? "
            + "AND namespace_id=? AND type=? AND c_from=?";
        return databaseOperate.queryOne(sql,
            new Object[] {hubEntryId, AiConstants.Hub.NAMESPACE_ID, RESOURCE_KIND, SOURCE},
            AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
    }
    
    @Override
    public boolean updateMetaCas(AiResource existing, long expectedMetaVersion,
        AiResource next) {
        String sql = "UPDATE ai_resource SET status=?, c_desc=?, biz_tags=?, ext=?, "
            + "version_info=?, meta_version=meta_version+1, gmt_modified="
            + mapper.getFunction("NOW()") + " WHERE id=? AND namespace_id=? AND name=? "
            + "AND type=? AND c_from=? AND meta_version=?";
        Object[] args = new Object[] {next.getStatus(), next.getDesc(), next.getBizTags(),
            next.getExt(), next.getVersionInfo(), existing.getId(), AiConstants.Hub.NAMESPACE_ID,
            existing.getName(), RESOURCE_KIND, SOURCE, expectedMetaVersion};
        // A zero-row CAS must fail inside the transaction, including Raft apply.
        // Acknowledgement alone cannot distinguish a stale identical-target write.
        EmbeddedStorageContextHolder.addSqlContext(true, sql, args);
        return Boolean.TRUE.equals(databaseOperate.blockUpdate());
    }
}
