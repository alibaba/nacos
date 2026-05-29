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
import com.alibaba.nacos.ai.utils.AiResourceVersionStorageJsonUtil;
import com.alibaba.nacos.api.common.Constants;
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
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceVersionMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Embedded (Derby) persist service for {@link AiResourceVersion}.
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service
public class EmbeddedAiResourceVersionPersistServiceImpl
    implements AiResourceVersionPersistService {
    
    private final DatabaseOperate databaseOperate;
    
    private final DataSourceService dataSourceService;
    
    private final MapperManager mapperManager;
    
    public EmbeddedAiResourceVersionPersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable =
            EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        NotifyCenter.registerToSharePublisher(DerbyImportEvent.class);
    }
    
    @Override
    public long insert(AiResourceVersion version) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.insert(
            Arrays.asList("type", "author", "name", "c_desc", "status", "version", "namespace_id",
                "storage", "publish_pipeline_info", "gmt_create@NOW()", "gmt_modified@NOW()"));
        
        Object[] args = new Object[] {version.getType(), version.getAuthor(), version.getName(),
            version.getDesc(),
            version.getStatus(), version.getVersion(),
            normalizeNamespaceId(version.getNamespaceId()),
            version.getStorage(), version.getPublishPipelineInfo()};
        
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
        Boolean success = databaseOperate.blockUpdate();
        if (success == null || !success) {
            throw new IllegalStateException("insert ai_resource_version failed");
        }
        
        AiResourceVersion inserted =
            find(version.getNamespaceId(), version.getName(), version.getType(),
                version.getVersion());
        if (inserted == null || inserted.getId() == null) {
            throw new IllegalStateException(
                "insert ai_resource_version failed, cannot query inserted row");
        }
        return inserted.getId();
    }
    
    @Override
    public AiResourceVersion find(String namespaceId, String name, String type, String version) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.select(
            Arrays.asList("id", "gmt_create", "gmt_modified", "type", "author", "name", "c_desc",
                "status", "version",
                "namespace_id", "storage", "publish_pipeline_info", "download_count"),
            Arrays.asList("namespace_id", "name", "type", "version"));
        return databaseOperate.queryOne(sql,
            new Object[] {normalizeNamespaceId(namespaceId), name, type, version},
            AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER);
    }
    
    @Override
    public Page<AiResourceVersion> list(String namespaceId, String name, String type, String status,
        int pageNo,
        int pageSize) {
        PaginationHelper<AiResourceVersion> helper =
            new EmbeddedPaginationHelperImpl<>(databaseOperate);
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        
        MapperContext context = new MapperContext((pageNo - 1) * pageSize, pageSize);
        context.putWhereParameter(FieldConstant.NAMESPACE_ID, normalizeNamespaceId(namespaceId));
        context.putWhereParameter(FieldConstant.NAME, name);
        if (StringUtils.isNotBlank(type)) {
            context.putWhereParameter(FieldConstant.TYPE, type);
        }
        if (StringUtils.isNotBlank(status)) {
            context.putWhereParameter(FieldConstant.STATUS, status);
        }
        
        MapperResult count = mapper.findAiResourceVersionCountRows(context);
        MapperResult fetch = mapper.findAiResourceVersionFetchRows(context);
        return helper.fetchPageLimit(count, fetch, pageNo, pageSize,
            AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER);
    }
    
    @Override
    public int delete(String namespaceId, String name, String type, String version) {
        AiResourceVersion existed = find(namespaceId, name, type, version);
        if (existed == null) {
            return 0;
        }
        
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name", "type", "version"));
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {normalizeNamespaceId(namespaceId), name, type, version});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    @Override
    public int deleteByName(String namespaceId, String name) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name"));
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {normalizeNamespaceId(namespaceId), name});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    @Override
    public int deleteByNameAndType(String namespaceId, String name, String type) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name", "type"));
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {normalizeNamespaceId(namespaceId), name, type});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    @Override
    public int updateStatus(String namespaceId, String name, String type, String version,
        String status) {
        if (find(namespaceId, name, type, version) == null) {
            return 0;
        }
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql =
            "UPDATE ai_resource_version SET status=?, gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {status, normalizeNamespaceId(namespaceId), name, type, version});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    @Override
    public int updateStorage(String namespaceId, String name, String type, String version,
        String storage) {
        if (find(namespaceId, name, type, version) == null) {
            return 0;
        }
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql =
            "UPDATE ai_resource_version SET storage=?, gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {storage, normalizeNamespaceId(namespaceId), name, type, version});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    @Override
    public int updateStorageAndDesc(String namespaceId, String name, String type, String version,
        String storage,
        String desc) {
        if (find(namespaceId, name, type, version) == null) {
            return 0;
        }
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = "UPDATE ai_resource_version SET storage=?, c_desc=?, gmt_modified="
            + mapper.getFunction("NOW()")
            + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {storage, desc, normalizeNamespaceId(namespaceId), name, type, version});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    @Override
    public int updateStorageMd5(String namespaceId, String name, String type, String version,
        String contentMd5) {
        AiResourceVersion existed = find(namespaceId, name, type, version);
        if (existed == null) {
            return 0;
        }
        String mergedStorage =
            AiResourceVersionStorageJsonUtil.mergeContentMd5(existed.getStorage(), contentMd5);
        return updateStorage(namespaceId, name, type, version, mergedStorage);
    }
    
    @Override
    public int updatePublishPipelineInfo(String namespaceId, String name, String type,
        String version,
        String publishPipelineInfo) {
        if (find(namespaceId, name, type, version) == null) {
            return 0;
        }
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = "UPDATE ai_resource_version SET publish_pipeline_info=?, gmt_modified="
            + mapper.getFunction("NOW()")
            + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {publishPipelineInfo, normalizeNamespaceId(namespaceId), name, type,
                version});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    @Override
    public int incrementDownloadCount(String namespaceId, String name, String type, String version,
        long increment) {
        if (find(namespaceId, name, type, version) == null) {
            return 0;
        }
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql =
            "UPDATE ai_resource_version SET download_count = download_count + ?, gmt_modified="
                + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        
        EmbeddedStorageContextHolder.addSqlContext(sql,
            new Object[] {increment, normalizeNamespaceId(namespaceId), name, type, version});
        Boolean success = databaseOperate.blockUpdate();
        return (success != null && success) ? 1 : 0;
    }
    
    private String normalizeNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId) ? Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
}
