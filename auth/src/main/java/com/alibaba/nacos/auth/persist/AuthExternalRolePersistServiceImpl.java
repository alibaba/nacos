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
import com.alibaba.nacos.auth.persist.repository.externel.AuthExternalStoragePersistServiceImpl;
import com.alibaba.nacos.auth.roles.RoleInfo;
import com.alibaba.nacos.auth.util.LogUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implemetation of ExternalRolePersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class AuthExternalRolePersistServiceImpl implements RolePersistService {
    
    public static final RoleInfoRowMapper ROLE_INFO_ROW_MAPPER = new RoleInfoRowMapper();
    
    @Autowired
    private AuthExternalStoragePersistServiceImpl persistService;
    
    private JdbcTemplate jt;
    
    @PostConstruct
    protected void init() {
        jt = persistService.getJdbcTemplate();
    }
    
    @Override
    public Page<RoleInfo> getRolesByUserName(String username, int pageNo, int pageSize) {
        
        PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();

        String sqlCountRows = "SELECT count(*) FROM roles WHERE ";

        String sqlFetchRows = "SELECT role,username FROM roles WHERE ";
        
        String where = " username= ? ";
        List<String> params = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            params = Collections.singletonList(username);
        } else {
            where = " 1=1 ";
        }
        
        try {
            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                    ROLE_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    private static final class RoleInfoRowMapper implements RowMapper<RoleInfo> {
        
        @Override
        public RoleInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            RoleInfo roleInfo = new RoleInfo();
            roleInfo.setRole(rs.getString("role"));
            roleInfo.setUsername(rs.getString("username"));
            return roleInfo;
        }
    }
}
