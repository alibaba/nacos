/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper.base;

import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;

/**
 * The tenant capacity mapper.
 *
 * @author hyx
 **/

public interface TenantCapacityMapper {
    
    /**
     * Get TenantCapacity by tenantId.
     *
     * @param tenantId The tenantCapacity object instance's id.
     * @return tenantCapacity object instance.
     */
    TenantCapacity getTenantCapacity(String tenantId);
    
    /**
     * Insert TenantCapacity.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    boolean insertTenantCapacity(final TenantCapacity tenantCapacity);
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    boolean incrementUsageWithDefaultQuotaLimit(TenantCapacity tenantCapacity);
}
