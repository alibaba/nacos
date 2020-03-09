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

import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.transaction.DatabaseOperate;
import com.alibaba.nacos.config.server.service.transaction.SqlContextUtils;
import com.alibaba.nacos.config.server.utils.PaginationHelper;
import com.alibaba.nacos.core.notify.NotifyCenter;
import java.util.ArrayList;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.alibaba.nacos.config.server.service.RowMapperManager.ROLE_INFO_ROW_MAPPER;


/**
 * Role CRUD service
 *
 * @author nkorange
 * @since 1.2.0
 */
@Service
public class RolePersistService extends PersistService {

    @Autowired
    private DatabaseOperate databaseOperate;

    @PostConstruct
    protected void postConstruct() {
        NotifyCenter.registerPublisher(RoleChangeEvent::new, RoleChangeEvent.class);
    }

    public Page<RoleInfo> getRoles(int pageNo, int pageSize) {

        PaginationHelper<RoleInfo> helper = new PaginationHelper<>();

        String sqlCountRows = "select count(*) from (select distinct role from roles) roles where ";
        String sqlFetchRows
                = "select role,username from roles where ";

        String where = " 1=1 ";

        Page<RoleInfo> pageInfo = helper.fetchPage(databaseOperate, sqlCountRows
                        + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                pageSize, ROLE_INFO_ROW_MAPPER);
        if (pageInfo == null) {
            pageInfo = new Page<>();
            pageInfo.setTotalCount(0);
            pageInfo.setPageItems(new ArrayList<>());
        }
        return pageInfo;

    }

    public Page<RoleInfo> getRolesByUserName(String username, int pageNo, int pageSize) {

        PaginationHelper<RoleInfo> helper = new PaginationHelper<>();

        String sqlCountRows = "select count(*) from roles where ";
        String sqlFetchRows
                = "select role,username from roles where ";

        String where = " username='" + username + "' ";

        if (StringUtils.isBlank(username)) {
            where = " 1=1 ";
        }

        return helper.fetchPage(databaseOperate, sqlCountRows
                        + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                pageSize, ROLE_INFO_ROW_MAPPER);

    }

    public void addRole(String role, String userName) {

        String sql = "INSERT into roles (role, username) VALUES (?, ?)";

        try {
            SqlContextUtils.addSqlContext(sql, role, userName);
            databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            NotifyCenter.publishEvent(RoleChangeEvent.class, new RoleChangeEvent());
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    public void deleteRole(String role) {
        String sql = "DELETE from roles WHERE role=?";
        try {
            SqlContextUtils.addSqlContext(sql, role);
            databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            NotifyCenter.publishEvent(RoleChangeEvent.class, new RoleChangeEvent());
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    public void deleteRole(String role, String username) {
        String sql = "DELETE from roles WHERE role=? and username=?";
        try {
            SqlContextUtils.addSqlContext(sql, role, username);
            databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            NotifyCenter.publishEvent(RoleChangeEvent.class, new RoleChangeEvent());
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

}
