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

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.ROLE_INFO_ROW_MAPPER;

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
import com.alibaba.nacos.plugin.datasource.mapper.RolesMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implemetation of ExternalRolePersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalRolePersistServiceImpl implements RolePersistService {
    
    private JdbcTemplate jt;
    
    private String dataSourceType = "";
    
    private static final String PATTERN_STR = "*";

    private DataSourceService dataSourceService;

    private MapperManager mapperManager;

    public ExternalRolePersistServiceImpl() {
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
    public Page<RoleInfo> getRoles(int pageNo, int pageSize) {
        AuthPaginationHelper<RoleInfo> helper = createPaginationHelper();

        final MapperContext context = new MapperContext();
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);

        RolesMapper rolesMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.ROLES);
        MapperResult sqlCount = rolesMapper.getRolesCountRows(context);
        MapperResult sql = rolesMapper.getRolesFetchRows(context);

        try {
            Page<RoleInfo> pageInfo = helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, ROLE_INFO_ROW_MAPPER);
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
    public Page<RoleInfo> getRolesByUserNameAndRoleName(String username, String role, int pageNo, int pageSize) {
        AuthPaginationHelper<RoleInfo> helper = createPaginationHelper();

        final MapperContext context = new MapperContext();
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);
        context.putWhereParameter(FieldConstant.USER_NAME, username);
        context.putWhereParameter(FieldConstant.ROLE, role);

        RolesMapper rolesMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.ROLES);
        MapperResult sqlCount = rolesMapper.getRolesByUserNameAndRoleNameCountRows(context);
        MapperResult sql = rolesMapper.getRolesByUserNameAndRoleNameFetchRows(context);

        try {
            return helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, ROLE_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute add role operation.
     *
     * @param role     role string value.
     * @param userName username string value.
     */
    @Override
    public void addRole(String role, String userName) {
        RolesMapper rolesMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.ROLES);
        String sql = rolesMapper.insert(Arrays.asList("role", "username"));
        
        try {
            jt.update(sql, role, userName);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute delete role operation.
     *
     * @param role role string value.
     */
    @Override
    public void deleteRole(String role) {
        RolesMapper rolesMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.ROLES);
        String sql = rolesMapper.delete(Collections.singletonList("role"));
        try {
            jt.update(sql, role);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute delete role operation.
     *
     * @param role     role string value.
     * @param username username string value.
     */
    @Override
    public void deleteRole(String role, String username) {
        RolesMapper rolesMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.ROLES);
        String sql = rolesMapper.delete(Arrays.asList("role", "username"));
        try {
            jt.update(sql, role, username);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<String> findRolesLikeRoleName(String role) {
        final MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ROLE, "%" + role + "%");

        RolesMapper rolesMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.ROLES);
        MapperResult sql = rolesMapper.findRolesLikeRoleName(context);

        return this.jt.queryForList(sql.getSql(), sql.getParamList().toArray(), String.class);
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
    public Page<RoleInfo> findRolesLike4Page(String username, String role, int pageNo, int pageSize) {
        AuthPaginationHelper<RoleInfo> helper = createPaginationHelper();

        final MapperContext context = new MapperContext();
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);
        context.putWhereParameter(FieldConstant.USER_NAME, generateLikeArgument(username));
        context.putWhereParameter(FieldConstant.ROLE, generateLikeArgument(role));

        RolesMapper rolesMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.ROLES);
        MapperResult sqlCount = rolesMapper.findRolesLike4PageCountRows(context);
        MapperResult sql = rolesMapper.findRolesLike4PageFetchRows(context);

        try {
            return helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, ROLE_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public <E> AuthPaginationHelper<E> createPaginationHelper() {
        return new AuthExternalPaginationHelperImpl<>(jt, dataSourceType);
    }
}
