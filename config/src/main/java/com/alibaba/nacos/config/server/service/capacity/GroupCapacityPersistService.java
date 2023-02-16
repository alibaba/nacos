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
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.mapper.GroupCapacityMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
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
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(Constants.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    private static final class GroupCapacityRowMapper implements RowMapper<GroupCapacity> {
        
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
                Arrays.asList("group_id"));
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
        String sql;
        if (CLUSTER.equals(capacity.getGroup())) {
            sql = groupCapacityMapper.insertIntoSelect();
        } else {
            // Note: add "tenant_id = ''" condition.
            sql = groupCapacityMapper.insertIntoSelectByWhere();
        }
        String[] primaryKeyGeneratedKeys = groupCapacityMapper.getPrimaryKeyGeneratedKeys();
        return insertGroupCapacity(sql, capacity, primaryKeyGeneratedKeys);
    }
    
    private boolean insertGroupCapacity(final String sql, final GroupCapacity capacity, String[] primaryKeyGeneratedKeys) {
        try {
            GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator preparedStatementCreator = connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, primaryKeyGeneratedKeys);
                String group = capacity.getGroup();
                ps.setString(1, group);
                ps.setInt(2, capacity.getQuota());
                ps.setInt(3, capacity.getMaxSize());
                ps.setInt(4, capacity.getMaxAggrCount());
                ps.setInt(5, capacity.getMaxAggrSize());
                ps.setTimestamp(6, capacity.getGmtCreate());
                ps.setTimestamp(7, capacity.getGmtModified());
                if (!CLUSTER.equals(group)) {
                    ps.setString(8, group);
                }
                return ps;
            };
            jdbcTemplate.update(preparedStatementCreator, generatedKeyHolder);
            return generatedKeyHolder.getKey() != null;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
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
        String sql = groupCapacityMapper.incrementUsageByWhereQuotaEqualZero();
        try {
            int affectRow = jdbcTemplate
                    .update(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup(), groupCapacity.getQuota());
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
        String sql = groupCapacityMapper.incrementUsageByWhereQuotaNotEqualZero();
        try {
            return jdbcTemplate.update(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup()) == 1;
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
        String sql = groupCapacityMapper.incrementUsageByWhere();
        try {
            int affectRow = jdbcTemplate.update(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup());
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
        String sql = groupCapacityMapper.decrementUsageByWhere();
        try {
            return jdbcTemplate.update(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup()) == 1;
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
        String sql;
        if (CLUSTER.equals(group)) {
            sql = groupCapacityMapper.updateUsage();
            try {
                return jdbcTemplate.update(sql, gmtModified, group) == 1;
            } catch (CannotGetJdbcConnectionException e) {
                FATAL_LOG.error("[db-error]", e);
                throw e;
            }
        } else {
            // Note: add "tenant_id = ''" condition.
            sql = groupCapacityMapper.updateUsageByWhere();
            try {
                return jdbcTemplate.update(sql, group, gmtModified, group) == 1;
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
        String sql = groupCapacityMapper.selectGroupInfoBySize();
        try {
            return jdbcTemplate.query(sql, new Object[] {lastId, pageSize}, new RowMapper<GroupCapacity>() {
                @Override
                public GroupCapacity mapRow(ResultSet rs, int rowNum) throws SQLException {
                    GroupCapacity groupCapacity = new GroupCapacity();
                    groupCapacity.setId(rs.getLong("id"));
                    groupCapacity.setGroup(rs.getString("group_id"));
                    return groupCapacity;
                }
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
                        groupCapacityMapper.delete(Arrays.asList("group_id")));
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
