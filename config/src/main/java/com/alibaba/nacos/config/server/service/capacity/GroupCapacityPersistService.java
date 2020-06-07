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

import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
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
import java.sql.*;
import java.util.List;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * Group Capacity Service
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Service
public class GroupCapacityPersistService {
    static final String CLUSTER = "";

    private static final GroupCapacityRowMapper
        GROUP_CAPACITY_ROW_MAPPER = new GroupCapacityRowMapper();
    private JdbcTemplate jdbcTemplate;

    private DataSourceService dataSourceService;

    @PostConstruct
    public void init() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jdbcTemplate = dataSourceService.getJdbcTemplate();
    }

    private static final class GroupCapacityRowMapper implements
        RowMapper<GroupCapacity> {
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
        String sql
            = "SELECT id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, group_id FROM group_capacity "
            + "WHERE group_id=?";
        List<GroupCapacity> list = jdbcTemplate.query(sql, new Object[] {groupId},
            GROUP_CAPACITY_ROW_MAPPER);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public Capacity getClusterCapacity() {
        return getGroupCapacity(CLUSTER);
    }

    public boolean insertGroupCapacity(final GroupCapacity capacity) {
        String sql;
        if (CLUSTER.equals(capacity.getGroup())) {
            sql
                = "insert into group_capacity (group_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                + "gmt_create, gmt_modified) select ?, ?, count(*), ?, ?, ?, ?, ? from config_info;";
        } else {
            // 注意这里要加"tenant_id = ''"条件
            sql =
                "insert into group_capacity (group_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                    + "gmt_create, gmt_modified) select ?, ?, count(*), ?, ?, ?, ?, ? from config_info where "
                    + "group_id=? and tenant_id = '';";
        }
        return insertGroupCapacity(sql, capacity);
    }

    public int getClusterUsage() {
        Capacity clusterCapacity = getClusterCapacity();
        if (clusterCapacity != null) {
            return clusterCapacity.getUsage();
        }
        String sql = "SELECT count(*) FROM config_info";
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }

    private boolean insertGroupCapacity(final String sql, final GroupCapacity capacity) {
        try {
            GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator preparedStatementCreator = new PreparedStatementCreator() {
                @Override
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
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
                }
            };
            jdbcTemplate.update(preparedStatementCreator, generatedKeyHolder);
            return generatedKeyHolder.getKey() != null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean incrementUsageWithDefaultQuotaLimit(GroupCapacity groupCapacity) {
        String sql =
            "UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` <"
                + " ? AND quota = 0";
        try {
            int affectRow = jdbcTemplate.update(sql,
                groupCapacity.getGmtModified(), groupCapacity.getGroup(), groupCapacity.getQuota());
            return affectRow == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity) {
        String sql
            = "UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` < "
            + "quota AND quota != 0";
        try {
            return jdbcTemplate.update(sql,
                groupCapacity.getGmtModified(), groupCapacity.getGroup()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;

        }
    }

    public boolean incrementUsage(GroupCapacity groupCapacity) {
        String sql = "UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ?";
        try {
            int affectRow = jdbcTemplate.update(sql,
                groupCapacity.getGmtModified(), groupCapacity.getGroup());
            return affectRow == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean decrementUsage(GroupCapacity groupCapacity) {
        String sql =
            "UPDATE group_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE group_id = ? AND `usage` > 0";
        try {
            return jdbcTemplate.update(sql,
                groupCapacity.getGmtModified(), groupCapacity.getGroup()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean updateGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
                                       Integer maxAggrSize) {
        List<Object> argList = Lists.newArrayList();
        StringBuilder sql = new StringBuilder("update group_capacity set");
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

        sql.append(" where group_id = ?");
        argList.add(group);
        try {
            return jdbcTemplate.update(sql.toString(), argList.toArray()) == 1;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean updateQuota(String group, Integer quota) {
        return updateGroupCapacity(group, quota, null, null, null);
    }

    public boolean updateMaxSize(String group, Integer maxSize) {
        return updateGroupCapacity(group, null, maxSize, null, null);
    }

    public boolean correctUsage(String group, Timestamp gmtModified) {
        String sql;
        if (CLUSTER.equals(group)) {
            sql = "UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE "
                + "group_id = ?";
            try {
                return jdbcTemplate.update(sql, gmtModified, group) == 1;
            } catch (CannotGetJdbcConnectionException e) {
                fatalLog.error("[db-error]", e);
                throw e;
            }
        } else {
            // 注意这里要加"tenant_id = ''"条件
            sql = "UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE group_id=? AND "
                + "tenant_id = ''), gmt_modified = ? WHERE group_id = ?";
            try {
                return jdbcTemplate.update(sql, group, gmtModified, group) == 1;
            } catch (CannotGetJdbcConnectionException e) {
                fatalLog.error("[db-error]", e);
                throw e;
            }
        }
    }

    /**
     * 获取GroupCapacity列表，只有id、groupId有值
     *
     * @param lastId   id > lastId
     * @param pageSize 页数
     * @return GroupCapacity列表
     */
    public List<GroupCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        String sql = "SELECT id, group_id FROM group_capacity WHERE id>? LIMIT ?";

        if (PropertyUtil.isEmbeddedStorage()) {
            sql = "SELECT id, group_id FROM group_capacity WHERE id>? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        }
        try {
            return jdbcTemplate.query(sql, new Object[] {lastId, pageSize},
                new RowMapper<GroupCapacity>() {
                    @Override
                    public GroupCapacity mapRow(ResultSet rs, int rowNum) throws SQLException {
                        GroupCapacity groupCapacity = new GroupCapacity();
                        groupCapacity.setId(rs.getLong("id"));
                        groupCapacity.setGroup(rs.getString("group_id"));
                        return groupCapacity;
                    }
                });
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error]", e);
            throw e;
        }
    }

    public boolean deleteGroupCapacity(final String group) {
        try {
            PreparedStatementCreator preparedStatementCreator = new PreparedStatementCreator() {
                @Override
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM group_capacity WHERE group_id = ?;");
                    ps.setString(1, group);
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
