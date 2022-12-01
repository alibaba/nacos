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
 * The tenant capacity info mapper.
 *
 * @author hyx
 **/

public interface TenantCapacityMapper extends Mapper {
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < ? AND quota = 0
     *
     * @return The sql of increment UsageWithDefaultQuotaLimit.
     */
    String incrementUsageWithDefaultQuotaLimit();
    
    /**
     * Increment UsageWithQuotaLimit.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < quota AND quota != 0
     *
     * @return The sql of Increment UsageWithQuotaLimit.
     */
    String incrementUsageWithQuotaLimit();
    
    /**
     * Increment Usage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?
     *
     * @return The sql of increment UsageWithQuotaLimit.
     */
    String incrementUsage();
    
    /**
     * DecrementUsage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0
     *
     * @return The sql of decrementUsage.
     */
    String decrementUsage();
    
    /**
     * Correct Usage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), gmt_modified = ? WHERE tenant_id = ?
     *
     * @return The sql of correcting Usage.
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
     * Insert TenantCapacity.
     * The default sql:
     * INSERT INTO tenant_capacity (tenant_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size,
     * gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;
     * @return The sql of inserting TenantCapacity.
     */
    String insertTenantCapacity();
}
