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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datafilter.constant.DataFilterConstants;
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
import java.sql.Statement;
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
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }

    @Override
    public long insert(AiResource resource) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = mapper.insert(Arrays.asList("name", "type", "c_desc", "status", "namespace_id", "biz_tags", "ext",
                "version_info", "meta_version", "scope", "owner", "gmt_create@NOW()", "gmt_modified@NOW()"));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, resource.getName());
            ps.setString(2, resource.getType());
            ps.setString(3, resource.getDesc());
            ps.setString(4, resource.getStatus());
            ps.setString(5, StringUtils.defaultEmptyIfBlank(resource.getNamespaceId()));
            ps.setString(6, resource.getBizTags());
            ps.setString(7, resource.getExt());
            ps.setString(8, resource.getVersionInfo());
            ps.setLong(9, resource.getMetaVersion() == null ? 1L : resource.getMetaVersion());
            ps.setString(10, resource.getScope() == null ? DataFilterConstants.SCOPE_PRIVATE : resource.getScope());
            ps.setString(11, resource.getOwner() == null ? "" : resource.getOwner());
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
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = mapper.select(
                Arrays.asList("id", "gmt_create", "gmt_modified", "name", "type", "c_desc", "status", "namespace_id",
                        "biz_tags", "ext", "version_info", "meta_version", "scope", "owner", "download_count"),
                Arrays.asList("namespace_id", "name", "type"));
        try {
            return jt.queryForObject(sql, new Object[] {StringUtils.defaultEmptyIfBlank(namespaceId), name, type},
                    AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public Page<AiResource> list(String namespaceId, String type, String nameLike, String bizTagsLike, int pageNo,
            int pageSize) {
        return list(namespaceId, type, nameLike, bizTagsLike, null, pageNo, pageSize);
    }

    @Override
    public Page<AiResource> list(String namespaceId, String type, String nameLike, String bizTagsLike, String orderBy,
            int pageNo, int pageSize) {
        PaginationHelper<AiResource> helper = new ExternalStoragePaginationHelperImpl<>(jt);
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
        if (StringUtils.isNotBlank(orderBy)) {
            context.putWhereParameter(FieldConstant.ORDER_BY, orderBy);
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
        int rows = jt.update(sql, args);
        return rows == 1;
    }

    @Override
    public int delete(String namespaceId, String name, String type) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = mapper.delete(Arrays.asList("namespace_id", "name", "type"));
        return jt.update(sql, StringUtils.defaultEmptyIfBlank(namespaceId), name, type);
    }
    
    @Override
    public boolean updateScope(String namespaceId, String name, String type, String scope) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = "UPDATE ai_resource SET scope=?, gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=?";
        int rows = jt.update(sql, scope, StringUtils.defaultEmptyIfBlank(namespaceId), name, type);
        return rows == 1;
    }

    @Override
    public boolean incrementDownloadCount(String namespaceId, String name, String type, long increment) {
        AiResourceMapper mapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.AI_RESOURCE);
        String sql = "UPDATE ai_resource SET download_count = download_count + ?, gmt_modified=" + mapper.getFunction("NOW()")
                + " WHERE namespace_id=? AND name=? AND type=?";
        int rows = jt.update(sql, increment, StringUtils.defaultEmptyIfBlank(namespaceId), name, type);
        return rows == 1;
    }
}

