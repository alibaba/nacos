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
package com.alibaba.nacos.config.server.service.capacity;

import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.service.DataSourceService;
import com.alibaba.nacos.config.server.service.DynamicDataSource;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.List;

import static com.alibaba.nacos.common.util.SystemUtils.STANDALONE_MODE;
import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * Tenant Capacity Service
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Service
public class TenantCapacityPersistService {

    private static final TenantCapacityRowMapper
        TENANT_CAPACITY_ROW_MAPPER = new TenantCapacityRowMapper();
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DynamicDataSource dynamicDataSource;
    private DataSourceService dataSourceService;

    @PostConstruct
    public void init() {
        this.dataSourceService = dynamicDataSource.getDataSource();
        this.jdbcTemplate = dataSourceService.getJdbcTemplate();
    }

    private static final class TenantCapacityRowMapper implements
        RowMapper<TenantCapacity> {
        @Override
        public TenantCapacity mapRow(ResultSet rs, int rowNum) throws SQLException {
            TenantCapacity tenantCapacity = new TenantCapacity();
            tenantCapacity.setId(rs.getLong("id"));
            tenantCapacity.setQuota(rs.getInt("quota"));
            tenantCapacity.setUsage(rs.getInt("usage"));
            tenantCapacity.setMaxSize(rs.getInt("max_size"));
            tenantCapacity.setMaxAggrCount(rs.getInt("max_aggr_count"));
            tenantCapacity.setMaxAggrSize(rs.getInt("max_aggr_size"));
            tenantCapacity.setTenant(rs.getString("tenant_id"));
            return tenantCapacity;
        }
    }

    public TenantCapacity getTenantCapacity(String tenantId) {
        String sql
            = "SELECT id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, tenant_id FROM tenant_capacity "
            + "WHERE tenant_id=?";
        List<TenantCapacity> list = jdbcTemplate.query(sql, new Object[] {tenantId},
            TENANT_CAPACITY_ROW_MAPPER);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public boolean insertTenantCapacity(final TenantCapacity tenantCapacity) {
        final String sql =
            "INSERT INTO tenant_capacity (tenant_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;";
        try {
            GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator preparedStatementCreator = new PreparedStatementCreator() {
                @Override
                @SuppressFBWarnings(value = {"OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE",
                    "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
                    justification = "findbugs does not trust jdbctemplate, sql is constant in practice")
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    String tenant = tenantCapacity.getTenant();
                    ps.setString(1, tenant);
                    ps.setInt(2, tenantCapacity.getQuota());
                    ps.setInt(3, tenantCapacity.getMaxSize());
                    ps.setInt(4, tenantCapacity.getMaxAggrCount());
                    ps.setInt(5, tenantCapacity.getMaxAggrSize());
                    ps.setTimestamp(6, tenantCapacity.getGmtCreate());
                    ps.setTimestamp(7, tenantCapacity.getGmtModified());
                    ps.setString(8, tenantCapacity.getTenant());
                    return ps;
                }
            };
            jdbcTemplate.update(preparedStatementCreator, generatedKeyHolder);
            return generatedKeyHolder.getKey() != null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }

    }

    public boolean incrementUsageWithDefaultQuotaLimit(TenantCapacity tenantCapacity) {
        String sql =
            "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` <"
                + " ? AND quota = 0";
        try {
            int affectRow = jdbcTemplate.update(sql,
                tenantCapacity.getGmtModified(), tenantCapacity.getTenant(), tenantCapacity.getQuota());
            return affectRow == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean incrementUsageWithQuotaLimit(TenantCapacity tenantCapacity) {
        String sql
            = "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < "
            + "quota AND quota != 0";
        try {
            return jdbcTemplate.update(sql,
                tenantCapacity.getGmtModified(), tenantCapacity.getTenant()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;

        }
    }

    public boolean incrementUsage(TenantCapacity tenantCapacity) {
        String sql = "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?";
        try {
            int affectRow = jdbcTemplate.update(sql,
                tenantCapacity.getGmtModified(), tenantCapacity.getTenant());
            return affectRow == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean decrementUsage(TenantCapacity tenantCapacity) {
        String sql =
            "UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0";
        try {
            return jdbcTemplate.update(sql,
                tenantCapacity.getGmtModified(), tenantCapacity.getTenant()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean updateTenantCapacity(String tenant, Integer quota, Integer maxSize, Integer maxAggrCount,
                                        Integer maxAggrSize) {
        List<Object> argList = Lists.newArrayList();
        StringBuilder sql = new StringBuilder("update tenant_capacity set");
        if (quota != null) {
            sql.append(" quota = ?,");
            argList.add(quota);
        }
        if (maxSize != null) {
            sql.append(" max_size = ?,");
            argList.add(maxSize);
        }
        if (maxAggrCount != null) {
            sql.append(" max_aggr_count = ?,");
            argList.add(maxAggrCount);
        }
        if (maxAggrSize != null) {
            sql.append(" max_aggr_size = ?,");
            argList.add(maxAggrSize);
        }
        sql.append(" gmt_modified = ?");
        argList.add(TimeUtils.getCurrentTime());

        sql.append(" where tenant_id = ?");
        argList.add(tenant);
        try {
            return jdbcTemplate.update(sql.toString(), argList.toArray()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean updateQuota(String tenant, Integer quota) {
        return updateTenantCapacity(tenant, quota, null, null, null);
    }

    public boolean correctUsage(String tenant, Timestamp gmtModified) {
        String sql = "UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
            + "gmt_modified = ? WHERE tenant_id = ?";
        try {
            return jdbcTemplate.update(sql, tenant, gmtModified, tenant) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    /**
     * 获取TenantCapacity列表，只有id、tenantId有值
     *
     * @param lastId   id > lastId
     * @param pageSize 页数
     * @return TenantCapacity列表
     */
    public List<TenantCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        String sql = "SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?";

        if (STANDALONE_MODE) {
            sql = "SELECT id, tenant_id FROM tenant_capacity WHERE id>? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        }

        try {
            return jdbcTemplate.query(sql, new Object[] {lastId, pageSize},
                new RowMapper<TenantCapacity>() {
                    @Override
                    public TenantCapacity mapRow(ResultSet rs, int rowNum) throws SQLException {
                        TenantCapacity tenantCapacity = new TenantCapacity();
                        tenantCapacity.setId(rs.getLong("id"));
                        tenantCapacity.setTenant(rs.getString("tenant_id"));
                        return tenantCapacity;
                    }
                });
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean deleteTenantCapacity(final String tenant) {
        try {
            PreparedStatementCreator preparedStatementCreator = new PreparedStatementCreator() {
                @Override
                @SuppressFBWarnings(value = {"OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE",
                    "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
                    justification = "findbugs does not trust jdbctemplate, sql is constant in practice")
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM tenant_capacity WHERE tenant_id = ?;");
                    ps.setString(1, tenant);
                    return ps;
                }
            };
            return jdbcTemplate.update(preparedStatementCreator) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }
}
