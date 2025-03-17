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

import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The tenant capacity info mapper.
 *
 * @author KiteSoar
 **/
public interface TenantCapacityMapper extends Mapper {
    
    /**
     * Select tenant_capacity table by tenant id.
     *
     * @param context sql paramMap
     * @return The sql of select.
     */
    MapperResult select(MapperContext context);
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     *
     * @param context sql paramMap
     * @return The sql of increment UsageWithDefaultQuotaLimit.
     */
    MapperResult incrementUsageWithDefaultQuotaLimit(MapperContext context);
    
    /**
     * Increment UsageWithQuotaLimit.
     *
     * @param context sql paramMap
     * @return The sql of Increment UsageWithQuotaLimit.
     */
    MapperResult incrementUsageWithQuotaLimit(MapperContext context);
    
    /**
     * Increment Usage.
     *
     * @param context sql paramMap
     * @return The sql of increment UsageWithQuotaLimit.
     */
    MapperResult incrementUsage(MapperContext context);
    
    /**
     * Decrement Usage.
     *
     * @param context sql paramMap
     * @return The sql of decrementUsage.
     */
    MapperResult decrementUsage(MapperContext context);
    
    /**
     * Correct Usage.
     *
     * @param context sql paramMap`
     * @return The sql of correcting Usage.
     */
    MapperResult correctUsage(MapperContext context);
    
    /**
     * Get TenantCapacity List, only including id and tenantId value.
     *
     * @param context sql paramMap
     * @return The sql of getting TenantCapacity List, only including id and tenantId value.
     */
    MapperResult getCapacityList4CorrectUsage(MapperContext context);
    
    /**
     * Insert TenantCapacity.
     *
     * @param context sql paramMap
     * @return The sql of inserting TenantCapacity.
     */
    MapperResult insertTenantCapacity(MapperContext context);
    
    /**
     * Get Table Name.
     *
     * @return table name.
     */
    default String getTableName() {
        return TableConstant.TENANT_CAPACITY;
    }
}
