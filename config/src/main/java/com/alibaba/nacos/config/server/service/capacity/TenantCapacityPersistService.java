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
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.google.common.collect.Lists;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;

/**
 * Tenant Capacity Service.
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Service
public class TenantCapacityPersistService {
    
    private static final TenantCapacityRowMapper TENANT_CAPACITY_ROW_MAPPER = new TenantCapacityRowMapper();
    
    private JdbcTemplate jdbcTemplate;
    
    private DataSourceService dataSourceService;
    
    @PostConstruct
    public void init() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jdbcTemplate = dataSourceService.getJdbcTemplate();
    }
    
    private static final class TenantCapacityRowMapper implements RowMapper<TenantCapacity> {
        
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
        String sql =
                "SELECT id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, tenant_id FROM tenant_capacity "
                        + "WHERE tenant_id=?";
        List<TenantCapacity> list = jdbcTemplate.query(sql, new Object[] {tenantId}, TENANT_CAPACITY_ROW_MAPPER);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
    
    /**
     * Insert TenantCapacity.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean insertTenantCapacity(final TenantCapacity tenantCapacity) {
        final String sql =
                "INSERT INTO tenant_capacity (tenant_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                        + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;";
        try {
            GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator preparedStatementCreator = new PreparedStatementCreator() {
                @Override
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
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
        
    }
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithDefaultQuotaLimit(TenantCapacity tenantCapacity) {
        String sql =
                "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` <"
                        + " ? AND quota = 0";
        try {
            int affectRow = jdbcTemplate.update(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant(),
                    tenantCapacity.getQuota());
            return affectRow == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Increment UsageWithQuotaLimit.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithQuotaLimit(TenantCapacity tenantCapacity) {
        String sql =
                "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < "
                        + "quota AND quota != 0";
        try {
            return jdbcTemplate.update(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
            
        }
    }
    
    /**
     * Increment Usage.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsage(TenantCapacity tenantCapacity) {
        String sql = "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?";
        try {
            int affectRow = jdbcTemplate.update(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant());
            return affectRow == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * DecrementUsage.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean decrementUsage(TenantCapacity tenantCapacity) {
        String sql = "UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0";
        try {
            return jdbcTemplate.update(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Update TenantCapacity.
     *
     * @param tenant tenant string value.
     * @param quota quota int value.
     * @param maxSize maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize maxAggrSize int value.
     * @return operate result.
     */
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
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    public boolean updateQuota(String tenant, Integer quota) {
        return updateTenantCapacity(tenant, quota, null, null, null);
    }
    
    /**
     * Correct Usage.
     *
     * @param tenant tenant.
     * @param gmtModified gmtModified.
     * @return operate result.
     */
    public boolean correctUsage(String tenant, Timestamp gmtModified) {
        String sql = "UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                + "gmt_modified = ? WHERE tenant_id = ?";
        try {
            return jdbcTemplate.update(sql, tenant, gmtModified, tenant) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Get TenantCapacity List, only including id and tenantId value.
     *
     * @param lastId   lastId long value.
     * @param pageSize pageSize int value.
     * @return TenantCapacity List.
     */
    public List<TenantCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        String sql = "SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?";
        
        if (PropertyUtil.isEmbeddedStorage()) {
            sql = "SELECT id, tenant_id FROM tenant_capacity WHERE id>? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        }
        
        try {
            return jdbcTemplate.query(sql, new Object[] {lastId, pageSize}, new RowMapper<TenantCapacity>() {
                @Override
                public TenantCapacity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    TenantCapacity tenantCapacity = new TenantCapacity();
                    tenantCapacity.setId(rs.getLong("id"));
                    tenantCapacity.setTenant(rs.getString("tenant_id"));
                    return tenantCapacity;
                }
            });
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Delete TenantCapacity.
     *
     * @param tenant tenant string value.
     * @return operate result.
     */
    public boolean deleteTenantCapacity(final String tenant) {
        try {
            PreparedStatementCreator preparedStatementCreator = new PreparedStatementCreator() {
                @Override
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection
                            .prepareStatement("DELETE FROM tenant_capacity WHERE tenant_id = ?;");
                    ps.setString(1, tenant);
                    return ps;
                }
            };
            return jdbcTemplate.update(preparedStatementCreator) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
}
