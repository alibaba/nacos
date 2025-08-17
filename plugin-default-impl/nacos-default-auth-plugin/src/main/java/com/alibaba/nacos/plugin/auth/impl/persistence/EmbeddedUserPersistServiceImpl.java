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

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.USER_ROW_MAPPER;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.auth.impl.persistence.embedded.AuthEmbeddedPaginationHelperImpl;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.UsersMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * There is no self-augmented primary key.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedUserPersistServiceImpl implements UserPersistService {
    
    @Autowired
    private DatabaseOperate databaseOperate;
    
    private static final String PATTERN_STR = "*";
    
    private static final String SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE = " ESCAPE '\\' ";

    private DataSourceService dataSourceService;

    private MapperManager mapperManager;

    public EmbeddedUserPersistServiceImpl() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    /**
     * Execute create user operation.
     *
     * @param username username string value.
     * @param password password string value.
     */
    @Override
    public void createUser(String username, String password) {
        UsersMapper usersMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        String sql = usersMapper.insert(Arrays.asList("username", "password", "enabled"));
        try {
            EmbeddedStorageContextHolder.addSqlContext(sql, username, password, true);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    /**
     * Execute delete user operation.
     *
     * @param username username string value.
     */
    @Override
    public void deleteUser(String username) {
        UsersMapper usersMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        String sql = usersMapper.delete(Collections.singletonList("username"));
        try {
            EmbeddedStorageContextHolder.addSqlContext(sql, username);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    /**
     * Execute update user password operation.
     *
     * @param username username string value.
     * @param password password string value.
     */
    @Override
    public void updateUserPassword(String username, String password) {
        UsersMapper usersMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        String sql = usersMapper.update(Collections.singletonList("password"), Collections.singletonList("username"));
        try {
            EmbeddedStorageContextHolder.addSqlContext(sql, password, username);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public User findUserByUsername(String username) {
        final MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.USER_NAME, username);

        UsersMapper usersMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        MapperResult sql = usersMapper.findUserByUsername(context);

        return databaseOperate.queryOne(sql.getSql(), sql.getParamList().toArray(), USER_ROW_MAPPER);
    }
    
    @Override
    public Page<User> getUsers(int pageNo, int pageSize, String username) {
        AuthPaginationHelper<User> helper = createPaginationHelper();

        final MapperContext context = new MapperContext();
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);
        context.putWhereParameter(FieldConstant.USER_NAME, username);

        UsersMapper usersMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        MapperResult sqlCount = usersMapper.getUsersCountRows(context);
        MapperResult sql = usersMapper.getUsersFetchRows(context);

        Page<User> pageInfo = helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, USER_ROW_MAPPER);
        if (pageInfo == null) {
            pageInfo = new Page<>();
            pageInfo.setTotalCount(0);
            pageInfo.setPageItems(new ArrayList<>());
        }
        return pageInfo;
    }
    
    @Override
    public List<String> findUserLikeUsername(String username) {
        final MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.USER_NAME, "%" + username + "%");

        UsersMapper usersMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        MapperResult sql = usersMapper.findUserLikeUsername(context);

        return databaseOperate.queryMany(sql.getSql(), sql.getParamList().toArray(), String.class);
    }
    
    @Override
    public Page<User> findUsersLike4Page(String username, int pageNo, int pageSize) {
        final AuthPaginationHelper<User> helper = createPaginationHelper();

        final MapperContext context = new MapperContext();
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);
        context.putWhereParameter(FieldConstant.USER_NAME, generateLikeArgument(username));

        UsersMapper usersMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        MapperResult sqlCount = usersMapper.findUsersLike4PageCountRows(context);
        MapperResult sql = usersMapper.findUsersLike4PageFetchRows(context);

        return helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, USER_ROW_MAPPER);
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
        return new AuthEmbeddedPaginationHelperImpl<>(databaseOperate);
    }
}
