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

package com.alibaba.nacos.auth.persist;

import com.alibaba.nacos.auth.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.auth.model.Page;
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import com.alibaba.nacos.auth.persist.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.auth.persist.repository.embedded.AuthEmbeddedStoragePersistServiceImpl;
import com.alibaba.nacos.auth.users.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * There is no self-augmented primary key.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class AuthEmbeddedUserPersistServiceImpl implements UserPersistService {
    
    public static final RowMapper<User> USER_ROW_MAPPER = new UserRowMapper();
    
    @Autowired
    private DatabaseOperate databaseOperate;
    
    @Autowired
    private AuthEmbeddedStoragePersistServiceImpl persistService;
    
    @Override
    public User findUserByUsername(String username) {
        String sql = "SELECT username,password FROM users WHERE username=? ";
        return databaseOperate.queryOne(sql, new Object[] {username}, USER_ROW_MAPPER);
    }
 
    @Override
    public List<String> findUserLikeUsername(String username) {
        String sql = "SELECT username FROM users WHERE username LIKE ? ";
        return databaseOperate.queryMany(sql, new String[] {"%" + username + "%"}, String.class);
    }
    
    @Override
    public Page<User> getUsers(int pageNo, int pageSize) {
        
        PaginationHelper<User> helper = persistService.createPaginationHelper();
        
        String sqlCountRows = "SELECT count(*) FROM users WHERE ";
        
        String sqlFetchRows = "SELECT username,password FROM users WHERE ";
        
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
