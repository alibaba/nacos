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

import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.service.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.google.common.collect.Lists;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.GROUP_CAPACITY_ROW_MAPPER;

/**
 * Embedded Storage Group Capacity Service.
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service
public class EmbeddedStorageGroupCapacityPersistServiceImpl implements GroupCapacityPersistService {
    
    static final String CLUSTER = "";
    
    private final DatabaseOperate databaseOperate;
    
    public EmbeddedStorageGroupCapacityPersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
    }
    
    @Override
    public GroupCapacity getGroupCapacity(String groupId) {
        String sql = "SELECT id, quota, usage, max_size, max_aggr_count, max_aggr_size, group_id FROM group_capacity "
                + "WHERE group_id=?";
        List<GroupCapacity> list = databaseOperate.queryMany(sql, new Object[] {groupId}, GROUP_CAPACITY_ROW_MAPPER);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
    
    @Override
    public Capacity getClusterCapacity() {
        return getGroupCapacity(CLUSTER);
    }
    
    @Override
    public boolean insertGroupCapacity(final GroupCapacity capacity) {
        if (CLUSTER.equals(capacity.getGroup())) {
            String sql =
                    "insert into group_capacity (group_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                            + "gmt_create, gmt_modified) select ?, ?, count(*), ?, ?, ?, ?, ? from config_info;";
            EmbeddedStorageContextUtils
                    .addSqlContext(sql, capacity.getGroup(), capacity.getQuota(), capacity.getMaxSize(),
                            capacity.getMaxAggrCount(), capacity.getMaxAggrSize(), capacity.getGmtCreate(),
                            capacity.getGmtModified());
        } else {
            // Note: add "tenant_id = ''" condition.
            String sql =
                    "insert into group_capacity (id, group_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                            + "gmt_create, gmt_modified) select ?, ?, count(*), ?, ?, ?, ?, ? from config_info where "
                            + "group_id=? and tenant_id = '';";
            EmbeddedStorageContextUtils
                    .addSqlContext(sql, capacity.getGroup(), capacity.getQuota(), capacity.getMaxSize(),
                            capacity.getMaxAggrCount(), capacity.getMaxAggrSize(), capacity.getGmtCreate(),
                            capacity.getGmtModified(), capacity.getGroup());
        }
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public int getClusterUsage() {
        Capacity clusterCapacity = getClusterCapacity();
        if (clusterCapacity != null) {
            return clusterCapacity.getUsage();
        }
        String sql = "SELECT count(*) FROM config_info";
        
        Integer result = databaseOperate.queryOne(sql, new Object[] {}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public boolean incrementUsageWithDefaultQuotaLimit(GroupCapacity groupCapacity) {
        String sql =
                "UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` <"
                        + " ? AND quota = 0";
        EmbeddedStorageContextUtils
                .addSqlContext(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup(), groupCapacity.getQuota());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity) {
        String sql = "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < "
                + "quota AND quota != 0";
        EmbeddedStorageContextUtils.addSqlContext(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean incrementUsage(GroupCapacity groupCapacity) {
        String sql = "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ?";
        EmbeddedStorageContextUtils.addSqlContext(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean decrementUsage(GroupCapacity groupCapacity) {
        String sql = "UPDATE group_capacity SET usage = usage - 1, gmt_modified = ? WHERE group_id = ? AND usage > 0";
        EmbeddedStorageContextUtils.addSqlContext(sql, groupCapacity.getGmtModified(), groupCapacity.getGroup());
        return databaseOperate.blockUpdate();
    }
    
    @Override
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
        EmbeddedStorageContextUtils.addSqlContext(sql.toString(), argList.toArray());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean updateQuota(String group, Integer quota) {
        return updateGroupCapacity(group, quota, null, null, null);
    }
    
    @Override
    public boolean updateMaxSize(String group, Integer maxSize) {
        return updateGroupCapacity(group, null, maxSize, null, null);
    }
    
    @Override
    public boolean correctUsage(String group, Timestamp gmtModified) {
        String sql;
        if (CLUSTER.equals(group)) {
            sql = "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE "
                    + "group_id = ?";
            EmbeddedStorageContextUtils.addSqlContext(sql, gmtModified, group);
            
        } else {
            // Note: add "tenant_id = ''" condition.
            sql = "UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE group_id=? AND "
                    + "tenant_id = ''), gmt_modified = ? WHERE group_id = ?";
            EmbeddedStorageContextUtils.addSqlContext(sql, group, gmtModified, group);
        }
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public List<GroupCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        String sql = "SELECT id, group_id FROM group_capacity WHERE id>? LIMIT ?";
        
        if (PropertyUtil.isEmbeddedStorage()) {
            sql = "SELECT id, group_id FROM group_capacity WHERE id>? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        }
        try {
            List<Map<String, Object>> list = databaseOperate.queryMany(sql, new Object[] {lastId, pageSize});
            return list.stream().map(map -> {
                GroupCapacity capacity = new GroupCapacity();
                capacity.setId((Long) map.get("id"));
                capacity.setGroup((String) map.get("group_id"));
                return capacity;
            }).collect(Collectors.toList());
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    @Override
    public boolean deleteGroupCapacity(final String group) {
        EmbeddedStorageContextUtils.addSqlContext("DELETE FROM group_capacity WHERE group_id = ?;", group);
        return databaseOperate.blockUpdate();
    }
}