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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.extrnal.ExternalStoragePaginationHelperImpl;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
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
 * Jdbc based persist service for {@link AiResource}.
 *
 * <p>Uses datasource-plugin {@link AiResourceMapper} to keep SQL generation consistent with Nacos config.</p>
 *
 * @author nacos
 * @since 3.2.0
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Service
public class AiResourcePersistServiceImpl implements AiResourcePersistService {
    
    private final DataSourceService dataSourceService;
    
    private final JdbcTemplate jt;
    
    private final MapperManager mapperManager;
    
    public AiResourcePersistServiceImpl() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jt = dataSourceService.getJdbcTemplate();
        Boolean isDataSourceLogEnable =
            EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    @Override
    public long insert(AiResource resource) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        String sql = mapper.insert(
            Arrays.asList("name", "type", "c_desc", "status", "namespace_id", "biz_tags", "ext",
                "c_from", "version_info", "meta_version", "scope", "owner", "gmt_create@NOW()",
                "gmt_modified@NOW()"));
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
            ps.setString(1, resource.getName());
            ps.setString(2, resource.getType());
            ps.setString(3, resource.getDesc());
            ps.setString(4, resource.getStatus());
            ps.setString(5, normalizeNamespaceId(resource.getNamespaceId()));
            ps.setString(6, resource.getBizTags());
            ps.setString(7, resource.getExt());
            ps.setString(8, resource.getFrom() == null ? "local" : resource.getFrom());
            ps.setString(9, resource.getVersionInfo());
            ps.setLong(10, resource.getMetaVersion() == null ? 1L : resource.getMetaVersion());
            ps.setString(11, resource.getScope() == null ? VisibilityConstants.SCOPE_PRIVATE
                : resource.getScope());
            ps.setString(12, resource.getOwner() == null ? "" : resource.getOwner());
            return ps;
        }, keyHolder);
        
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("insert ai_resource failed, no generated key");
        }
        return key.longValue();
    }
    
    @Override
    public AiResource find(String namespaceId, String name, String type) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        String sql = mapper.select(
            Arrays.asList("id", "gmt_create", "gmt_modified", "name", "type", "c_desc", "status",
                "namespace_id",
                "biz_tags", "ext", "c_from", "version_info", "meta_version", "scope", "owner",
                "download_count"),
            Arrays.asList("namespace_id", "name", "type"));
        try {
            return jt.queryForObject(sql,
                new Object[] {normalizeNamespaceId(namespaceId), name, type},
                AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    @Override
    public Page<AiResource> list(QueryCondition queryCondition, int pageNo, int pageSize) {
        if (queryCondition == null) {
            queryCondition = new QueryCondition();
        }
        PaginationHelper<AiResource> helper = new ExternalStoragePaginationHelperImpl<>(jt);
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        MapperContext context = buildListContext(queryCondition, pageNo, pageSize);
        mergeQueryConditionToContext(context, queryCondition);
        context.putWhereParameter(AiResourceMapper.QUERY_CONDITION_ALWAYS_EMPTY,
            queryCondition.isAlwaysEmpty());
        MapperResult count = mapper.findAiResourceCountRows(context);
        MapperResult fetch = mapper.findAiResourceFetchRows(context);
        return helper.fetchPageLimit(count, fetch, pageNo, pageSize,
            AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
    }
    
    @Override
    public boolean updateMetaCas(String namespaceId, String name, String type,
        long expectedMetaVersion,
        AiResource newValue) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        
        String sql =
            "UPDATE ai_resource SET status=?, c_desc=?, biz_tags=?, ext=?, version_info=?, meta_version=meta_version+1, "
                + "gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=? AND meta_version=?";
        
        Object[] args = new Object[] {newValue.getStatus(), newValue.getDesc(),
            newValue.getBizTags(), newValue.getExt(),
            newValue.getVersionInfo(), normalizeNamespaceId(namespaceId), name, type,
            expectedMetaVersion};
        int rows = jt.update(sql, args);
        return rows == 1;
    }
    
    @Override
    public boolean updateSourceCas(String namespaceId, String name, String type,
        long expectedMetaVersion,
        String source) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        
        String sql = "UPDATE ai_resource SET c_from=?, meta_version=meta_version+1, "
            + "gmt_modified=" + mapper.getFunction("NOW()")
            + " WHERE namespace_id=? AND name=? AND type=? AND meta_version=?";
        
        int rows = jt.update(sql, source, normalizeNamespaceId(namespaceId), name, type,
            expectedMetaVersion);
        return rows == 1;
    }
    
    @Override
    public int delete(String namespaceId, String name, String type) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name", "type"));
        return jt.update(sql, normalizeNamespaceId(namespaceId), name, type);
    }
    
    @Override
    public boolean updateScope(String namespaceId, String name, String type, String scope) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        String sql = "UPDATE ai_resource SET scope=?, gmt_modified=" + mapper.getFunction("NOW()")
            + " WHERE namespace_id=? AND name=? AND type=?";
        int rows = jt.update(sql, scope, normalizeNamespaceId(namespaceId), name, type);
        return rows == 1;
    }
    
    @Override
    public boolean incrementDownloadCount(String namespaceId, String name, String type,
        long increment) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
            TableConstant.AI_RESOURCE);
        String sql = "UPDATE ai_resource SET download_count = download_count + ?, gmt_modified="
            + mapper.getFunction("NOW()")
            + " WHERE namespace_id=? AND name=? AND type=?";
        int rows = jt.update(sql, increment, normalizeNamespaceId(namespaceId), name, type);
        return rows == 1;
    }
    
    private MapperContext buildListContext(QueryCondition queryCondition, int pageNo,
        int pageSize) {
        MapperContext context = new MapperContext((pageNo - 1) * pageSize, pageSize);
        context.putWhereParameter(FieldConstant.NAMESPACE_ID,
            normalizeNamespaceId(queryCondition.getNamespaceId()));
        return context;
    }
    
    private void mergeQueryConditionToContext(MapperContext context, QueryCondition condition) {
        if (context == null || condition == null) {
            return;
        }
        if (StringUtils.isNotBlank(condition.getType())) {
            context.putWhereParameter(FieldConstant.TYPE, condition.getType());
        }
        if (StringUtils.isNotBlank(condition.getNameLike())) {
            context.putWhereParameter(FieldConstant.NAME, condition.getNameLike());
        }
        if (StringUtils.isNotBlank(condition.getBizTagsLike())) {
            context.putWhereParameter(FieldConstant.BIZ_TAGS, condition.getBizTagsLike());
        }
        if (StringUtils.isNotBlank(condition.getScope())) {
            context.putWhereParameter(FieldConstant.SCOPE, condition.getScope());
        }
        if (StringUtils.isNotBlank(condition.getOwner())) {
            context.putWhereParameter(FieldConstant.OWNER, condition.getOwner());
        }
        if (StringUtils.isNotBlank(condition.getOrderBy())) {
            context.putWhereParameter(FieldConstant.ORDER_BY, condition.getOrderBy());
        }
        if (condition.getOrGroup() != null && !condition.getOrGroup().isEmpty()) {
            context.putWhereParameter(AiResourceMapper.QUERY_CONDITION_OR_GROUP,
                condition.getOrGroup());
        }
    }
    
    private String normalizeNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId) ? Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
}
