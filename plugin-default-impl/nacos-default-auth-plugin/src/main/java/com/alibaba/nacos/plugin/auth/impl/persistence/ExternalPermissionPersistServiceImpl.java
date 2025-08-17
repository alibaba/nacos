/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.PERMISSION_ROW_MAPPER;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.impl.persistence.extrnal.AuthExternalPaginationHelperImpl;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.PermissionsMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import java.util.ArrayList;
import java.util.Arrays;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implemetation of ExternalPermissionPersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalPermissionPersistServiceImpl implements PermissionPersistService {
    
    private JdbcTemplate jt;
    
    private String dataSourceType = "";
    
    private static final String PATTERN_STR = "*";

    private DataSourceService dataSourceService;

    private MapperManager mapperManager;

    public ExternalPermissionPersistServiceImpl() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    @PostConstruct
    protected void init() {
        DataSourceService dataSource = DynamicDataSource.getInstance().getDataSource();
        jt = dataSource.getJdbcTemplate();
        dataSourceType = dataSource.getDataSourceType();
    }
    
    @Override
    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        AuthPaginationHelper<PermissionInfo> helper = createPaginationHelper();

        final MapperContext context = new MapperContext();
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);
        context.putWhereParameter(FieldConstant.ROLE, role);

        PermissionsMapper permissionsMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.PERMISSIONS);
        MapperResult sqlCount = permissionsMapper.getPermissionsCountRows(context);
        MapperResult sql = permissionsMapper.getPermissionsFetchRows(context);

        try {
            Page<PermissionInfo> pageInfo = helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, PERMISSION_ROW_MAPPER);
            
            if (pageInfo == null) {
                pageInfo = new Page<>();
                pageInfo.setTotalCount(0);
                pageInfo.setPageItems(new ArrayList<>());
            }
            
            return pageInfo;
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute add permission operation.
     *
     * @param role     role string value.
     * @param resource resource string value.
     * @param action   action string value.
     */
    @Override
    public void addPermission(String role, String resource, String action) {
        PermissionsMapper permissionsMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.PERMISSIONS);
        String sql = permissionsMapper.insert(Arrays.asList("role", "resource", "action"));
        try {
            jt.update(sql, role, resource, action);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute delete permission operation.
     *
     * @param role     role string value.
     * @param resource resource string value.
     * @param action   action string value.
     */
    @Override
    public void deletePermission(String role, String resource, String action) {
        PermissionsMapper permissionsMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.PERMISSIONS);
        String sql = permissionsMapper.delete(Arrays.asList("role", "resource", "action"));
        try {
            jt.update(sql, role, resource, action);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<PermissionInfo> findPermissionsLike4Page(String role, int pageNo, int pageSize) {
        AuthPaginationHelper<PermissionInfo> helper = createPaginationHelper();

        final MapperContext context = new MapperContext();
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);
        context.putWhereParameter(FieldConstant.ROLE, generateLikeArgument(role));

        PermissionsMapper permissionsMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.PERMISSIONS);
        MapperResult sqlCount = permissionsMapper.findPermissionsLike4PageCountRows(context);
        MapperResult sql = permissionsMapper.findPermissionsLike4PageFetchRows(context);

        try {
            Page<PermissionInfo> pageInfo = helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, PERMISSION_ROW_MAPPER);
            
            if (pageInfo == null) {
                pageInfo = new Page<>();
                pageInfo.setTotalCount(0);
                pageInfo.setPageItems(new ArrayList<>());
            }
            
            return pageInfo;
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public String generateLikeArgument(String s) {
        if (StringUtils.isBlank(s)) {
            return s;
        }

        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }
    
    @Override
    public <E> AuthPaginationHelper<E> createPaginationHelper() {
        return new AuthExternalPaginationHelperImpl<E>(jt, dataSourceType);
    }
}
