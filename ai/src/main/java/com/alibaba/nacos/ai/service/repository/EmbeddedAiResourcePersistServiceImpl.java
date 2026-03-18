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
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.event.DerbyImportEvent;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedPaginationHelperImpl;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Embedded (Derby) persist service for {@link AiResource}.
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service
public class EmbeddedAiResourcePersistServiceImpl implements AiResourcePersistService {

    private final DatabaseOperate databaseOperate;

    private final DataSourceService dataSourceService;

    private final MapperManager mapperManager;

    public EmbeddedAiResourcePersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        NotifyCenter.registerToSharePublisher(DerbyImportEvent.class);
    }

    @Override
    public long insert(AiResource resource) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = mapper.insert(Arrays.asList("name", "type", "c_desc", "status", "namespace_id", "biz_tags", "ext",
                "version_info", "meta_version", "gmt_create@NOW()", "gmt_modified@NOW()"));

        Object[] args = new Object[] {resource.getName(), resource.getType(), resource.getDesc(), resource.getStatus(),
                StringUtils.defaultEmptyIfBlank(resource.getNamespaceId()), resource.getBizTags(), resource.getExt(),
                resource.getVersionInfo(), resource.getMetaVersion() == null ? 1L : resource.getMetaVersion()};

        EmbeddedStorageContextHolder.addSqlContext(sql, args);
        Boolean success = databaseOperate.blockUpdate();
        if (success == null || !success) {
            throw new IllegalStateException("insert ai_resource failed");
        }

        AiResource inserted = find(resource.getNamespaceId(), resource.getName(), resource.getType());
        if (inserted == null || inserted.getId() == null) {
            throw new IllegalStateException("insert ai_resource failed, cannot query inserted row");
        }
        return inserted.getId();
    }

    @Override
    public AiResource find(String namespaceId, String name, String type) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = mapper.select(
                Arrays.asList("id", "gmt_create", "gmt_modified", "name", "type", "c_desc", "status", "namespace_id",
                        "biz_tags", "ext", "version_info", "meta_version"),
                Arrays.asList("namespace_id", "name", "type"));
        return databaseOperate.queryOne(sql, new Object[] {StringUtils.defaultEmptyIfBlank(namespaceId), name, type},
                AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
    }

    @Override
    public Page<AiResource> list(String namespaceId, String type, String nameLike, String bizTagsLike, int pageNo,
            int pageSize) {
        PaginationHelper<AiResource> helper = new EmbeddedPaginationHelperImpl<>(databaseOperate);
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);

        MapperContext context = new MapperContext((pageNo - 1) * pageSize, pageSize);
        context.putWhereParameter(FieldConstant.NAMESPACE_ID, StringUtils.defaultEmptyIfBlank(namespaceId));
        if (StringUtils.isNotBlank(type)) {
            context.putWhereParameter(FieldConstant.TYPE, type);
        }
        if (StringUtils.isNotBlank(nameLike)) {
            context.putWhereParameter(FieldConstant.NAME, nameLike);
        }
        if (StringUtils.isNotBlank(bizTagsLike)) {
            context.putWhereParameter(FieldConstant.BIZ_TAGS, bizTagsLike);
        }

        MapperResult count = mapper.findAiResourceCountRows(context);
        MapperResult fetch = mapper.findAiResourceFetchRows(context);
        return helper.fetchPageLimit(count, fetch, pageNo, pageSize, AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
    }

    @Override
    public boolean updateMetaCas(String namespaceId, String name, String type, long expectedMetaVersion,
            AiResource newValue) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);

        String sql = "UPDATE ai_resource SET status=?, c_desc=?, biz_tags=?, ext=?, version_info=?, meta_version=meta_version+1, "
                + "gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND meta_version=?";

        Object[] args = new Object[] {newValue.getStatus(), newValue.getDesc(), newValue.getBizTags(), newValue.getExt(),
                newValue.getVersionInfo(), StringUtils.defaultEmptyIfBlank(namespaceId), name, type, expectedMetaVersion};

        EmbeddedStorageContextHolder.addSqlContext(sql, args);
        Boolean success = databaseOperate.blockUpdate();
        if (success == null || !success) {
            return false;
        }
        AiResource updated = find(namespaceId, name, type);
        return updated != null && updated.getMetaVersion() == expectedMetaVersion + 1;
    }

    @Override
    public int delete(String namespaceId, String name, String type) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name", "type"));

        AiResource existed = find(namespaceId, name, type);
        if (existed == null) {
            return 0;
        }

        EmbeddedStorageContextHolder.addSqlContext(sql, new Object[] {StringUtils.defaultEmptyIfBlank(namespaceId), name, type});
        Boolean success = databaseOperate.blockUpdate();
        if (success == null || !success) {
            return 0;
        }
        return 1;
    }
}

