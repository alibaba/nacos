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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

import static com.alibaba.nacos.config.server.service.RowMapperManager.PERMISSION_ROW_MAPPER;

/**
 * Permission CRUD service
 *
 * @author nkorange
 * @since 1.2.0
 */
@Service
public class PermissionPersistService extends PersistService {

    @Autowired
    private DatabaseOperate databaseOperate;

    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        PaginationHelper<PermissionInfo> helper = new PaginationHelper<>();

        String sqlCountRows = "select count(*) from permissions where ";
        String sqlFetchRows
                = "select role,resource,action from permissions where ";

        String where = " role='" + role + "' ";

        if (StringUtils.isBlank(role)) {
            where = " 1=1 ";
        }

        Page<PermissionInfo> pageInfo = helper.fetchPage(databaseOperate, sqlCountRows
                        + where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
                pageSize, PERMISSION_ROW_MAPPER);

        if (pageInfo == null) {
            pageInfo = new Page<>();
            pageInfo.setTotalCount(0);
            pageInfo.setPageItems(new ArrayList<>());
        }

        return pageInfo;

    }

    public void addPermission(String role, String resource, String action) {

        String sql = "INSERT into permissions (role, resource, action) VALUES (?, ?, ?)";
        SqlContextUtils.addSqlContext(sql, role, resource, action);
        databaseOperate.updateAuto();
    }

    public void deletePermission(String role, String resource, String action) {

        String sql = "DELETE from permissions WHERE role=? and resource=? and action=?";
        SqlContextUtils.addSqlContext(sql, role, resource, action);
        databaseOperate.updateAuto();

    }

}
