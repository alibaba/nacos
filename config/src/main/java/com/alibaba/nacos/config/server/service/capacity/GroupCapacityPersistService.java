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
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.mapper.GroupCapacityMapper;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;

/**
 * Group Capacity Service.
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Service
public class GroupCapacityPersistService {
    
    static final String CLUSTER = "";
    
    private static final GroupCapacityRowMapper GROUP_CAPACITY_ROW_MAPPER = new GroupCapacityRowMapper();
    
    private JdbcTemplate jdbcTemplate;
    
    private DataSourceService dataSourceService;
    
    private MapperManager mapperManager;
    
    /**
     * init.
     */
    @PostConstruct
    public void init() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jdbcTemplate = dataSourceService.getJdbcTemplate();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    static final class GroupCapacityRowMapper implements RowMapper<GroupCapacity> {
        
        @Override
        public GroupCapacity mapRow(ResultSet rs, int rowNum) throws SQLException {
            GroupCapacity groupCapacity = new GroupCapacity();
            groupCapacity.setId(rs.getLong("id"));
            groupCapacity.setQuota(rs.getInt("quota"));
            groupCapacity.setUsage(rs.getInt("usage"));
            groupCapacity.setMaxSize(rs.getInt("max_size"));
            groupCapacity.setMaxAggrCount(rs.getInt("max_aggr_count"));
            groupCapacity.setMaxAggrSize(rs.getInt("max_aggr_size"));
            groupCapacity.setGroup(rs.getString("group_id"));
            return groupCapacity;
        }
    }
    
    public GroupCapacity getGroupCapacity(String groupId) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        String sql = groupCapacityMapper.select(
                Arrays.asList("id", "quota", "`usage`", "`max_size`", "max_aggr_count", "max_aggr_size", "group_id"),
                Collections.singletonList("group_id"));
        List<GroupCapacity> list = jdbcTemplate.query(sql, new Object[] {groupId}, GROUP_CAPACITY_ROW_MAPPER);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
    
    public Capacity getClusterCapacity() {
        return getGroupCapacity(CLUSTER);
    }
    
    /**
     * Insert GroupCapacity into db.
     *
     * @param capacity capacity object instance.
     * @return operate result.
     */
    public boolean insertGroupCapacity(final GroupCapacity capacity) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        MapperResult mapperResult;
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GROUP_ID, capacity.getGroup());
        context.putUpdateParameter(FieldConstant.QUOTA, capacity.getQuota());
        context.putUpdateParameter(FieldConstant.MAX_SIZE, capacity.getMaxSize());
        context.putUpdateParameter(FieldConstant.MAX_AGGR_SIZE, capacity.getMaxAggrSize());
        context.putUpdateParameter(FieldConstant.MAX_AGGR_COUNT, capacity.getMaxAggrCount());
        context.putUpdateParameter(FieldConstant.GMT_CREATE, capacity.getGmtCreate());
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, capacity.getGmtModified());
        
        context.putWhereParameter(FieldConstant.GROUP_ID, capacity.getGroup());
        if (CLUSTER.equals(capacity.getGroup())) {
            mapperResult = groupCapacityMapper.insertIntoSelect(context);
        } else {
            // Note: add "tenant_id = ''" condition.
            mapperResult = groupCapacityMapper.insertIntoSelectByWhere(context);
        }
        return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) > 0;
    }
    
    public int getClusterUsage() {
        Capacity clusterCapacity = getClusterCapacity();
        if (clusterCapacity != null) {
            return clusterCapacity.getUsage();
        }
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.count(null);
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithDefaultQuotaLimit(GroupCapacity groupCapacity) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, groupCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.GROUP_ID, groupCapacity.getGroup());
        context.putWhereParameter(FieldConstant.USAGE, groupCapacity.getQuota());
        MapperResult mapperResult = groupCapacityMapper.incrementUsageByWhereQuotaEqualZero(context);
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
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, groupCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.GROUP_ID, groupCapacity.getGroup());
        MapperResult mapperResult = groupCapacityMapper.incrementUsageByWhereQuotaNotEqualZero(context);
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
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsage(GroupCapacity groupCapacity) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, groupCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.GROUP_ID, groupCapacity.getGroup());
        MapperResult mapperResult = groupCapacityMapper.incrementUsageByWhere(context);
        try {
            int affectRow = jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray());
            return affectRow == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Decrement Usage.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean decrementUsage(GroupCapacity groupCapacity) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, groupCapacity.getGmtModified());
        context.putWhereParameter(FieldConstant.GROUP_ID, groupCapacity.getGroup());
        MapperResult mapperResult = groupCapacityMapper.decrementUsageByWhere(context);
        try {
            return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Update GroupCapacity.
     *
     * @param group        group string value.
     * @param quota        quota int value.
     * @param maxSize      maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize  maxAggrSize int value.
     * @return operate result.
     */
    public boolean updateGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize) {
        List<Object> argList = CollectionUtils.list();
        List<String> columnList = CollectionUtils.list();
        if (quota != null) {
            columnList.add("quota");
            argList.add(quota);
        }
        if (maxSize != null) {
            columnList.add("max_size");
            argList.add(maxSize);
        }
        if (maxAggrCount != null) {
            columnList.add("max_aggr_count");
            argList.add(maxAggrCount);
        }
        if (maxAggrSize != null) {
            columnList.add("max_aggr_size");
            argList.add(maxAggrSize);
        }
        columnList.add("gmt_modified");
        argList.add(TimeUtils.getCurrentTime());
        
        List<String> whereList = CollectionUtils.list();
        whereList.add("group_id");
        argList.add(group);
        
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        String sql = groupCapacityMapper.update(columnList, whereList);
        try {
            return jdbcTemplate.update(sql, argList.toArray()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    public boolean updateQuota(String group, Integer quota) {
        return updateGroupCapacity(group, quota, null, null, null);
    }
    
    public boolean updateMaxSize(String group, Integer maxSize) {
        return updateGroupCapacity(group, null, maxSize, null, null);
    }
    
    /**
     * Correct Usage.
     *
     * @param group       group string value.
     * @param gmtModified gmtModified.
     * @return operate result.
     */
    public boolean correctUsage(String group, Timestamp gmtModified) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        MapperResult mapperResult;
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, gmtModified);
        context.putWhereParameter(FieldConstant.GROUP_ID, group);
        if (CLUSTER.equals(group)) {
            mapperResult = groupCapacityMapper.updateUsage(context);
            try {
                return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) == 1;
            } catch (CannotGetJdbcConnectionException e) {
                FATAL_LOG.error("[db-error]", e);
                throw e;
            }
        } else {
            // Note: add "tenant_id = ''" condition.
            mapperResult = groupCapacityMapper.updateUsageByWhere(context);
            try {
                return jdbcTemplate.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) == 1;
            } catch (CannotGetJdbcConnectionException e) {
                FATAL_LOG.error("[db-error]", e);
                throw e;
            }
        }
    }
    
    /**
     * Get group capacity list, noly has id and groupId value.
     *
     * @param lastId   lastId long value.
     * @param pageSize pageSize long value.
     * @return GroupCapacity list.
     */
    public List<GroupCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.GROUP_CAPACITY);
        
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, lastId);
        context.setPageSize(pageSize);
        
        MapperResult mapperResult = groupCapacityMapper.selectGroupInfoBySize(context);
        try {
            return jdbcTemplate.query(mapperResult.getSql(), mapperResult.getParamList().toArray(), (rs, rowNum) -> {
                GroupCapacity groupCapacity = new GroupCapacity();
                groupCapacity.setId(rs.getLong("id"));
                groupCapacity.setGroup(rs.getString("group_id"));
                return groupCapacity;
            });
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    /**
     * Delete GroupCapacity.
     *
     * @param group group string value.
     * @return operate result.
     */
    public boolean deleteGroupCapacity(final String group) {
        try {
            GroupCapacityMapper groupCapacityMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.GROUP_CAPACITY);
            PreparedStatementCreator preparedStatementCreator = connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        groupCapacityMapper.delete(Collections.singletonList("group_id")));
                ps.setString(1, group);
                return ps;
            };
            return jdbcTemplate.update(preparedStatementCreator) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
        
    }
}
