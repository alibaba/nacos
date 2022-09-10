/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper;

/**
 * The tenant capacity mapper.
 *
 * @author hyx
 **/

public interface TenantCapacityMapper {
    
    /**
     * Query the tenant capacity.
     * The default sql:
     * SELECT id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, tenant_id
     * FROM tenant_capacity WHERE tenant_id=?
     *
     * @return The sql of querying the tenant capacity.
     */
    String getTenantCapacity();
    
    /**
     * Insert TenantCapacity.
     * The default sql:
     * INSERT INTO tenant_capacity (tenant_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size,
     * gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;
     *
     * @return The sql of inserting tenant capacity.
     */
    String insertTenantCapacity();
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ?
     * WHERE tenant_id = ? AND `usage` < ? AND quota = 0
     *
     * @return The sql of incrementing UsageWithDefaultQuotaLimit.
     */
    String incrementUsageWithDefaultQuotaLimit();
    
    /**
     * Increment UsageWithQuotaLimit.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ?
     * WHERE tenant_id = ? AND `usage` < quota AND quota != 0
     *
     * @return The sql of incrementing UsageWithQuotaLimit.
     */
    String incrementUsageWithQuotaLimit();
    
    /**
     * Increment Usage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?
     *
     * @return The sql of incrementing usage.
     */
    String incrementUsage();
    
    /**
     * DecrementUsage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0
     *
     * @return The sql of DecrementUsage.
     */
    String decrementUsage();
    
    /**
     * Update TenantCapacity.
     * The default sql:
     * UPDATE tenant_capacity SET ... gmt_modified = ? WHERE tenant_id = ?
     *
     * @return The sql of updating TenantCapacity.
     */
    String updateTenantCapacity();
    
    /**
     * Correct Usage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info
     * WHERE tenant_id = ?), gmt_modified = ? WHERE tenant_id = ?
     *
     * @return The sql of correcting usage.
     */
    String correctUsage();
    
    /**
     * Get TenantCapacity List, only including id and tenantId value.
     * The default sql:
     * SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?
     *
     * @return The sql of getting TenantCapacity List, only including id and tenantId value.
     */
    String getCapacityList4CorrectUsage();
    
    /**
     * Delete TenantCapacity.
     * The default sql:
     * DELETE FROM tenant_capacity WHERE tenant_id = ?;
     *
     * @return The sql of deleting tenant capacity.
     */
    String deleteTenantCapacity();
}
