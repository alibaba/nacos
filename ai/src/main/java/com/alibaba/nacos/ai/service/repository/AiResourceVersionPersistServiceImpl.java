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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.extrnal.ExternalStoragePaginationHelperImpl;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceVersionMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.Arrays;

/**
 * Jdbc based persist service for {@link AiResourceVersion}.
 *
 * @author nacos
 * @since 3.2.0
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Service
public class AiResourceVersionPersistServiceImpl implements AiResourceVersionPersistService {
    
    private final DataSourceService dataSourceService;
    
    private final JdbcTemplate jt;
    
    private final MapperManager mapperManager;
    
    public AiResourceVersionPersistServiceImpl() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jt = dataSourceService.getJdbcTemplate();
        Boolean isDataSourceLogEnable =
            EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    @Override
    public long insert(AiResourceVersion version) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.insert(
            Arrays.asList("type", "author", "name", "c_desc", "status", "version", "namespace_id",
                "storage", "publish_pipeline_info", "gmt_create@NOW()", "gmt_modified@NOW()"));
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
            ps.setString(1, version.getType());
            ps.setString(2, version.getAuthor());
            ps.setString(3, version.getName());
            ps.setString(4, version.getDesc());
            ps.setString(5, version.getStatus());
            ps.setString(6, version.getVersion());
            ps.setString(7, normalizeNamespaceId(version.getNamespaceId()));
            ps.setString(8, version.getStorage());
            ps.setString(9, version.getPublishPipelineInfo());
            return ps;
        }, keyHolder);
        
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("insert ai_resource_version failed, no generated key");
        }
        return key.longValue();
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
        try {
            return jt.queryForObject(sql,
                new Object[] {normalizeNamespaceId(namespaceId), name, type, version},
                AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    @Override
    public Page<AiResourceVersion> list(String namespaceId, String name, String type, String status,
        int pageNo,
        int pageSize) {
        PaginationHelper<AiResourceVersion> helper = new ExternalStoragePaginationHelperImpl<>(jt);
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
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name", "type", "version"));
        return jt.update(sql, normalizeNamespaceId(namespaceId), name, type, version);
    }
    
    @Override
    public int deleteByName(String namespaceId, String name) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name"));
        return jt.update(sql, normalizeNamespaceId(namespaceId), name);
    }
    
    @Override
    public int deleteByNameAndType(String namespaceId, String name, String type) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name", "type"));
        return jt.update(sql, normalizeNamespaceId(namespaceId), name, type);
    }
    
    @Override
    public int updateStatus(String namespaceId, String name, String type, String version,
        String status) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql =
            "UPDATE ai_resource_version SET status=?, gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        return jt.update(sql, status, normalizeNamespaceId(namespaceId), name, type, version);
    }
    
    @Override
    public int updateStorage(String namespaceId, String name, String type, String version,
        String storage) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql =
            "UPDATE ai_resource_version SET storage=?, gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        return jt.update(sql, storage, normalizeNamespaceId(namespaceId), name, type, version);
    }
    
    @Override
    public int updateStorageAndDesc(String namespaceId, String name, String type, String version,
        String storage,
        String desc) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = "UPDATE ai_resource_version SET storage=?, c_desc=?, gmt_modified="
            + mapper.getFunction("NOW()")
            + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        return jt.update(sql, storage, desc, normalizeNamespaceId(namespaceId), name, type,
            version);
    }
    
    @Override
    public int updateStorageMd5(String namespaceId, String name, String type, String version,
        String contentMd5) {
        AiResourceVersion existed = find(namespaceId, name, type, version);
        if (existed == null) {
            return 0;
        }
        String mergedStorage = AiResourceVersionStorageJsonUtil
            .mergeContentMd5(existed.getStorage(), contentMd5);
        return updateStorage(namespaceId, name, type, version, mergedStorage);
    }
    
    @Override
    public int updatePublishPipelineInfo(String namespaceId, String name, String type,
        String version,
        String publishPipelineInfo) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql = "UPDATE ai_resource_version SET publish_pipeline_info=?, gmt_modified="
            + mapper.getFunction("NOW()")
            + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        return jt.update(sql, publishPipelineInfo, normalizeNamespaceId(namespaceId), name, type,
            version);
    }
    
    @Override
    public int incrementDownloadCount(String namespaceId, String name, String type, String version,
        long increment) {
        AiResourceVersionMapper mapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.AI_RESOURCE_VERSION);
        String sql =
            "UPDATE ai_resource_version SET download_count = download_count + ?, gmt_modified="
                + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND version=?";
        return jt.update(sql, increment, normalizeNamespaceId(namespaceId), name, type, version);
    }
    
    private String normalizeNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId) ? Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
}
