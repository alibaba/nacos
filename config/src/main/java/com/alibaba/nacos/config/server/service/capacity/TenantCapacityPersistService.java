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

import java.sql.Timestamp;
import java.util.List;

/**
 * tenant capacity persist service.
 *
 * @author mai.jh
 */
public interface TenantCapacityPersistService {
    
    /**
     * get tenantCapacity by tenantId.
     *
     * @param tenantId tenantId
     * @return TenantCapacity
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
    
    /**
     * Increment UsageWithQuotaLimit.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    boolean incrementUsageWithQuotaLimit(TenantCapacity tenantCapacity);
    
    /**
     * Increment Usage.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    boolean incrementUsage(TenantCapacity tenantCapacity);
    
    /**
     * DecrementUsage.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    boolean decrementUsage(TenantCapacity tenantCapacity);
    
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
    boolean updateTenantCapacity(String tenant, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize);
    
    /**
     * update Quota.
     *
     * @param tenant tenant string value.
     * @param quota  quota int value.
     * @return boolean
     */
    boolean updateQuota(String tenant, Integer quota);
    
    /**
     * Correct Usage.
     *
     * @param tenant      tenant.
     * @param gmtModified gmtModified.
     * @return operate result.
     */
    boolean correctUsage(String tenant, Timestamp gmtModified);
    
    /**
     * Get TenantCapacity List, only including id and tenantId value.
     *
     * @param lastId   lastId long value.
     * @param pageSize pageSize int value.
     * @return TenantCapacity List.
     */
    List<TenantCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize);
    
    /**
     * Delete TenantCapacity.
     *
     * @param tenant tenant string value.
     * @return operate result.
     */
    boolean deleteTenantCapacity(final String tenant);
    
}