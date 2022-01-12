/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.persist;

import com.alibaba.nacos.auth.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.auth.model.Page;
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import com.alibaba.nacos.auth.persist.repository.externel.ExternalStoragePersistServiceImpl;
import com.alibaba.nacos.auth.users.User;
import com.alibaba.nacos.auth.util.LogUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Implemetation of ExternalUserPersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalUserPersistServiceImpl implements UserPersistService {
    
    public static final RowMapper<User> USER_ROW_MAPPER = new UserRowMapper();
    
    @Autowired
    private ExternalStoragePersistServiceImpl persistService;
    
    private JdbcTemplate jt;
    
    @PostConstruct
    protected void init() {
        jt = persistService.getJdbcTemplate();
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
            jt.update(sql, username, password, true);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    /**
     * Execute find user by username operation.
     *
     * @param username username string value.
     * @return User model.
     */
    @Override
    public User findUserByUsername(String username) {
        String sql = "SELECT username,password FROM users WHERE username=? ";
        try {
            return this.jt.queryForObject(sql, new Object[] {username}, USER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public Page<User> getUsers(int pageNo, int pageSize) {
        
        PaginationHelper<User> helper = persistService.createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM users WHERE ";
        
        String sqlFetchRows = "SELECT username,password FROM users WHERE ";
        
        String where = " 1=1 ";
        
        try {
            Page<User> pageInfo = helper
                    .fetchPage(sqlCountRows + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                            pageSize, USER_ROW_MAPPER);
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
    
    public static final class UserRowMapper implements RowMapper<User> {
        
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setUsername(rs.getString("username"));
            user.setPassword(rs.getString("password"));
            return user;
        }
    }
}
