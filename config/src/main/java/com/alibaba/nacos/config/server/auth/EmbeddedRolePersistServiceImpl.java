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
import com.alibaba.nacos.config.server.service.repository.EmbeddedStoragePersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.DatabaseOperate;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.ROLE_INFO_ROW_MAPPER;

/**
 * There is no self-augmented primary key
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedRolePersistServiceImpl implements RolePersistService {

	@Autowired
	private DatabaseOperate databaseOperate;

	@Autowired
	private EmbeddedStoragePersistServiceImpl persistService;

	public Page<RoleInfo> getRoles(int pageNo, int pageSize) {

		PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();

		String sqlCountRows = "select count(*) from (select distinct role from roles) roles where ";
		String sqlFetchRows
				= "select role,username from roles where ";

		String where = " 1=1 ";

		Page<RoleInfo> pageInfo = helper.fetchPage(sqlCountRows
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

		PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();

		String sqlCountRows = "select count(*) from roles where ";
		String sqlFetchRows
				= "select role,username from roles where ";

		String where = " username='" + username + "' ";

		if (StringUtils.isBlank(username)) {
			where = " 1=1 ";
		}

		return helper.fetchPage(sqlCountRows
						+ where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
				pageSize, ROLE_INFO_ROW_MAPPER);

	}

	public void addRole(String role, String userName) {

		String sql = "INSERT into roles (role, username) VALUES (?, ?)";

		try {
			EmbeddedStorageContextUtils.addSqlContext(sql, role, userName);
			databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
		} finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void deleteRole(String role) {
		String sql = "DELETE from roles WHERE role=?";
		try {
			EmbeddedStorageContextUtils.addSqlContext(sql, role);
			databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
		} finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void deleteRole(String role, String username) {
		String sql = "DELETE from roles WHERE role=? and username=?";
		try {
			EmbeddedStorageContextUtils.addSqlContext(sql, role, username);
			databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
		} finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

}
