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

import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.ExternalStoragePersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
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

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.ROLE_INFO_ROW_MAPPER;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalRolePersistServiceImpl implements RolePersistService {

	@Autowired
	private ExternalStoragePersistServiceImpl persistService;

	private JdbcTemplate jt;

	@PostConstruct
	protected void init() {
		jt = persistService.getJdbcTemplate();
	}

	public Page<RoleInfo> getRoles(int pageNo, int pageSize) {

		PaginationHelper<RoleInfo> helper = persistService.createPaginationHelper();

		String sqlCountRows = "select count(*) from (select distinct role from roles) roles where ";
		String sqlFetchRows
				= "select role,username from roles where ";

		String where = " 1=1 ";

		try {
			Page<RoleInfo> pageInfo = helper.fetchPage(sqlCountRows
							+ where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
					pageSize, ROLE_INFO_ROW_MAPPER);
			if (pageInfo == null) {
				pageInfo = new Page<>();
				pageInfo.setTotalCount(0);
				pageInfo.setPageItems(new ArrayList<>());
			}
			return pageInfo;
		} catch (CannotGetJdbcConnectionException e) {
			LogUtil.fatalLog.error("[db-error] " + e.toString(), e);
			throw e;
		}
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

		try {
			return helper.fetchPage(sqlCountRows
							+ where, sqlFetchRows + where, new ArrayList<String>().toArray(), pageNo,
					pageSize, ROLE_INFO_ROW_MAPPER);
		} catch (CannotGetJdbcConnectionException e) {
			LogUtil.fatalLog.error("[db-error] " + e.toString(), e);
			throw e;
		}
	}

	public void addRole(String role, String userName) {

		String sql = "INSERT into roles (role, username) VALUES (?, ?)";

		try {
			jt.update(sql, role, userName);
		} catch (CannotGetJdbcConnectionException e) {
			LogUtil.fatalLog.error("[db-error] " + e.toString(), e);
			throw e;
		}
	}

	public void deleteRole(String role) {
		String sql = "DELETE from roles WHERE role=?";
		try {
			jt.update(sql, role);
		} catch (CannotGetJdbcConnectionException e) {
			LogUtil.fatalLog.error("[db-error] " + e.toString(), e);
			throw e;
		}
	}

	public void deleteRole(String role, String username) {
		String sql = "DELETE from roles WHERE role=? and username=?";
		try {
			jt.update(sql, role, username);
		} catch (CannotGetJdbcConnectionException e) {
			LogUtil.fatalLog.error("[db-error] " + e.toString(), e);
			throw e;
		}
	}

	private static final class RoleInfoRowMapper implements RowMapper<RoleInfo> {
		@Override
		public RoleInfo mapRow(ResultSet rs, int rowNum)
				throws SQLException {
			RoleInfo roleInfo = new RoleInfo();
			roleInfo.setRole(rs.getString("role"));
			roleInfo.setUsername(rs.getString("username"));
			return roleInfo;
		}
	}


}
