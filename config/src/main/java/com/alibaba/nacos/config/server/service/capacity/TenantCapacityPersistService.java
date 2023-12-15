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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.TenantCapacityMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    
    private MapperManager mapperManager;
    
    /**
     * init method.
     */
    @PostConstruct
    public void init() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jdbcTemplate = dataSourceService.getJdbcTemplate();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
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
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        String sql = tenantCapacityMapper.select(
                Arrays.asList("id", "quota", "`usage`", "`max_size`", "max_aggr_count", "max_aggr_size", "tenant_id"),
                Collections.singletonList("tenant_id"));
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
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.TENANT_ID, tenantCapacity.getTenant());
        context.putUpdateParameter(FieldConstant.QUOTA, tenantCapacity.getQuota());
        context.putUpdateParameter(FieldConstant.MAX_SIZE, tenantCapacity.getMaxSize());
        context.putUpdateParameter(FieldConstant.MAX_AGGR_SIZE, tenantCapacity.getMaxAggrSize());
        context.putUpdateParameter(FieldConstant.MAX_AGGR_COUNT, tenantCapacity.getMaxAggrCount());
        context.putUpdateParameter(FieldConstant.GMT_CREATE, tenantCapacity.getGmtCreate());
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, tenantCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantCapacity.getTenant());
        
        final MapperResult mapperResult = tenantCapacityMapper.insertTenantCapacity(context);
        try {
            return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) > 0;
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
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, tenantCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantCapacity.getTenant());
        context.putWhereParameter(FieldConstant.USAGE, tenantCapacity.getQuota());
        MapperResult mapperResult = tenantCapacityMapper.incrementUsageWithDefaultQuotaLimit(context);
        
        try {
            int affectRow = jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray());
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
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, tenantCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantCapacity.getTenant());
        MapperResult mapperResult = tenantCapacityMapper.incrementUsageWithQuotaLimit(context);
        try {
            return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) == 1;
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
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, tenantCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantCapacity.getTenant());
        MapperResult mapperResult = tenantCapacityMapper.incrementUsage(context);
        try {
            int affectRow = jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray());
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
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, tenantCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantCapacity.getTenant());
        MapperResult mapperResult = tenantCapacityMapper.decrementUsage(context);
        try {
            return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Update TenantCapacity.
     *
     * @param tenant       tenant string value.
     * @param quota        quota int value.
     * @param maxSize      maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize  maxAggrSize int value.
     * @return operate result.
     */
    public boolean updateTenantCapacity(String tenant, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize) {
        List<Object> argList = CollectionUtils.list();
        
        List<String> columns = new ArrayList<>();
        if (quota != null) {
            columns.add("quota");
            argList.add(quota);
        }
        if (maxSize != null) {
            columns.add("max_size");
            argList.add(maxSize);
        }
        if (maxAggrCount != null) {
            columns.add("max_aggr_count");
            argList.add(maxAggrCount);
        }
        if (maxAggrSize != null) {
            columns.add("max_aggr_size");
            argList.add(maxAggrSize);
        }
        columns.add("gmt_modified");
        argList.add(TimeUtils.getCurrentTime());
        
        List<String> where = new ArrayList<>();
        where.add("tenant_id");
        
        argList.add(tenant);
        
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        String sql = tenantCapacityMapper.update(columns, where);
        try {
            return jdbcTemplate.update(sql, argList.toArray()) == 1;
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
     * @param tenant      tenant.
     * @param gmtModified gmtModified.
     * @return operate result.
     */
    public boolean correctUsage(String tenant, Timestamp gmtModified) {
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, gmtModified);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenant);
        MapperResult mapperResult = tenantCapacityMapper.correctUsage(context);
        try {
            return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) == 1;
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
        TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_CAPACITY);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, lastId);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, pageSize);
        MapperResult mapperResult = tenantCapacityMapper.getCapacityList4CorrectUsage(context);
        
        try {
            return jdbcTemplate.query(mapperResult.getSql(), mapperResult.getParamList().toArray(), (rs, rowNum) -> {
                TenantCapacity tenantCapacity = new TenantCapacity();
                tenantCapacity.setId(rs.getLong("id"));
                tenantCapacity.setTenant(rs.getString("tenant_id"));
                return tenantCapacity;
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
            TenantCapacityMapper tenantCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.TENANT_CAPACITY);
            PreparedStatementCreator preparedStatementCreator = connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        tenantCapacityMapper.delete(Collections.singletonList("tenant_id")));
                ps.setString(1, tenant);
                return ps;
            };
            return jdbcTemplate.update(preparedStatementCreator) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
}
