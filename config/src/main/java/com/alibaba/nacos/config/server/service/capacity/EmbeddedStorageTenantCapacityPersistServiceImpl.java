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
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
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
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.TENANT_CAPACITY_ROW_MAPPER;

/**
 * Embedded Storage Tenant Capacity Service Impl.
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service
public class EmbeddedStorageTenantCapacityPersistServiceImpl implements TenantCapacityPersistService {
    
    private final DatabaseOperate databaseOperate;
    
    public EmbeddedStorageTenantCapacityPersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
    }
    
    @Override
    public TenantCapacity getTenantCapacity(String tenantId) {
        String sql =
                "SELECT id, quota, usage, max_size, max_aggr_count, max_aggr_size, tenant_id FROM tenant_capacity "
                        + "WHERE tenant_id=?";
        List<TenantCapacity> list = databaseOperate.queryMany(sql, new Object[] {tenantId}, TENANT_CAPACITY_ROW_MAPPER);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
    
    @Override
    public boolean insertTenantCapacity(final TenantCapacity tenantCapacity) {
        final String sql =
                "INSERT INTO tenant_capacity (tenant_id, quota, usage, max_size, max_aggr_count, max_aggr_size, "
                        + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?";
        
        final Object[] args = new Object[] {tenantCapacity.getTenant(), tenantCapacity.getQuota(),
                tenantCapacity.getMaxSize(), tenantCapacity.getMaxAggrCount(), tenantCapacity.getMaxAggrSize(),
                tenantCapacity.getGmtCreate(), tenantCapacity.getGmtModified(), tenantCapacity.getTenant()};
        
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean incrementUsageWithDefaultQuotaLimit(TenantCapacity tenantCapacity) {
        String sql =
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage <"
                        + " ? AND quota = 0";
        EmbeddedStorageContextUtils.addSqlContext(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant(),
                tenantCapacity.getQuota());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean incrementUsageWithQuotaLimit(TenantCapacity tenantCapacity) {
        String sql =
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage < "
                        + "quota AND quota != 0";
        EmbeddedStorageContextUtils.addSqlContext(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean incrementUsage(TenantCapacity tenantCapacity) {
        String sql = "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ?";
        EmbeddedStorageContextUtils.addSqlContext(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean decrementUsage(TenantCapacity tenantCapacity) {
        String sql = "UPDATE tenant_capacity SET usage = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND usage > 0";
        EmbeddedStorageContextUtils.addSqlContext(sql, tenantCapacity.getGmtModified(), tenantCapacity.getTenant());
        return databaseOperate.blockUpdate();
    }
    
    @Override
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
        EmbeddedStorageContextUtils.addSqlContext(sql.toString(), argList.toArray());
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public boolean updateQuota(String tenant, Integer quota) {
        return updateTenantCapacity(tenant, quota, null, null, null);
    }
    
    @Override
    public boolean correctUsage(String tenant, Timestamp gmtModified) {
        String sql = "UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                + "gmt_modified = ? WHERE tenant_id = ?";
        EmbeddedStorageContextUtils.addSqlContext(sql, tenant, gmtModified, tenant);
        return databaseOperate.blockUpdate();
    }
    
    @Override
    public List<TenantCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        String sql = "SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?";
        
        if (PropertyUtil.isEmbeddedStorage()) {
            sql = "SELECT id, tenant_id FROM tenant_capacity WHERE id>? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        }
        
        try {
            List<Map<String, Object>> list = databaseOperate.queryMany(sql, new Object[] {lastId, pageSize});
            return list.stream().map(map -> {
                TenantCapacity capacity = new TenantCapacity();
                capacity.setId((Long) map.get("id"));
                capacity.setTenant((String) map.get("tenant_id"));
                return capacity;
            }).collect(Collectors.toList());
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error]", e);
            throw e;
        }
    }
    
    @Override
    public boolean deleteTenantCapacity(final String tenant) {
        EmbeddedStorageContextUtils.addSqlContext("DELETE FROM tenant_capacity WHERE tenant_id = ?;", tenant);
        return databaseOperate.blockUpdate();
    }
}