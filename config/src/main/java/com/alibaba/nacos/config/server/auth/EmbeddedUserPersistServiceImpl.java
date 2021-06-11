/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.config.server.service.repository.embedded.EmbeddedStoragePersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.USER_ROW_MAPPER;

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
    
    @Autowired
    private EmbeddedStoragePersistServiceImpl persistService;
    
    /**
     * Execute create user operation.
     *
     * @param username username string value.
     * @param password password string value.
     */
    @Override
    public void createUser(String username, String password) {
        String sql = "INSERT into users (username, password, enabled) VALUES (?, ?, ?)";
        
        try {
            EmbeddedStorageContextUtils.addSqlContext(sql, username, password, true);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    /**
     * Execute delete user operation.
     *
     * @param username username string value.
     */
    @Override
    public void deleteUser(String username) {
        String sql = "DELETE from users WHERE username=?";
        try {
            EmbeddedStorageContextUtils.addSqlContext(sql, username);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
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
            EmbeddedStorageContextUtils
                    .addSqlContext("UPDATE users SET password = ? WHERE username=?", password, username);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public User findUserByUsername(String username) {
        String sql = "SELECT username,password FROM users WHERE username=? ";
        return databaseOperate.queryOne(sql, new Object[] {username}, USER_ROW_MAPPER);
    }
    
    @Override
    public Page<User> getUsers(int pageNo, int pageSize) {
        
        PaginationHelper<User> helper = persistService.createPaginationHelper();
        
        String sqlCountRows = "select count(*) from users where ";
        String sqlFetchRows = "select username,password from users where ";
        
        String where = " 1=1 ";
        Page<User> pageInfo = helper
                .fetchPage(sqlCountRows + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                        pageSize, USER_ROW_MAPPER);
        if (pageInfo == null) {
            pageInfo = new Page<>();
            pageInfo.setTotalCount(0);
            pageInfo.setPageItems(new ArrayList<>());
        }
        return pageInfo;
    }
    
    @Override
    public List<String> findUserLikeUsername(String username) {
        String sql = "SELECT username FROM users WHERE username like ? ";
        return databaseOperate.queryMany(sql, new String[] {"%" + username + "%"}, String.class);
    }
}
