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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedPaginationHelperImpl;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.UserMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.plugin.auth.impl.persistence.AuthRowMapperManager.USER_ROW_MAPPER;

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

    private DataSourceService dataSourceService;

    private MapperManager mapperManager;

    private static final String PATTERN_STR = "*";

    private static final String SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE = " ESCAPE '\\' ";


    /**
     * init method.
     */
    @PostConstruct
    public void init() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
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
        String sql = "INSERT INTO users (username, password, enabled) VALUES (?, ?, ?)";

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
        String sql = "DELETE FROM users WHERE username=?";
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
        try {
            EmbeddedStorageContextHolder
                    .addSqlContext("UPDATE users SET password = ? WHERE username=?", password, username);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }

    @Override
    public User findUserByUsername(String username) {
        String sql = "SELECT username,password FROM users WHERE username=? ";
        return databaseOperate.queryOne(sql, new Object[]{username}, USER_ROW_MAPPER);
    }

    @Override
    public Page<User> getUsers(int pageNo, int pageSize, String username) {
        String usernameTemp = StringUtils.isBlank(username) ? StringUtils.EMPTY : username;
        UserMapper userMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.USERS);
        String sqlCountRows = userMapper.count(null);

        MapperContext context = new MapperContext(pageNo, pageSize);
        context.putWhereParameter(FieldConstant.USER_NAME, usernameTemp);

        MapperResult mapperResult = userMapper.getUsers(context);
        String sqlFetchRows = mapperResult.getSql();

        PaginationHelper<User> helper = createPaginationHelper();
        Page<User> userPage = helper.fetchPage(sqlCountRows, sqlFetchRows, mapperResult.getParamList().toArray(), pageNo, pageSize,
                USER_ROW_MAPPER);
        return userPage;
    }

    @Override
    public List<String> findUserLikeUsername(String username) {
        String sql = "SELECT username FROM users WHERE username LIKE ? " + SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE;
        return databaseOperate.queryMany(sql, new String[]{"%" + username + "%"}, String.class);
    }

    @Override
    public Page<User> findUsersLike4Page(String username, int pageNo, int pageSize) {
        String sqlCountRows = "SELECT count(*) FROM users ";
        String sqlFetchRows = "SELECT username,password FROM users ";

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username LIKE ? ");
            where.append(SQL_DERBY_ESCAPE_BACK_SLASH_FOR_LIKE);
            params.add(generateLikeArgument(username));
        }

        PaginationHelper<User> helper = createPaginationHelper();
        return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                USER_ROW_MAPPER);
    }

    @Override
    public String generateLikeArgument(String s) {
        String underscore = "_";
        if (s.contains(underscore)) {
            s = s.replaceAll(underscore, "\\\\_");
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
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<>(databaseOperate);
    }
}
