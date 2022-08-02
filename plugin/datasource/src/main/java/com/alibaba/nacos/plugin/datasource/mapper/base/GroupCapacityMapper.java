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

import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;

/**
 * The group capacity mapper.
 *
 * @author hyx
 **/

public interface GroupCapacityMapper extends BaseMapper<GroupCapacity> {
    
    /**
     * Get GroupCapacity by groupId.
     *
     * @param groupId capacity object instance's groupId
     * @return capacity object instance.
     */
    GroupCapacity getGroupCapacity(String groupId);
    
    /**
     * Insert GroupCapacity into db.
     *
     * @param capacity capacity object instance.
     * @return operate result.
     */
    boolean insertGroupCapacity(final GroupCapacity capacity);
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    boolean incrementUsageWithDefaultQuotaLimit(GroupCapacity groupCapacity);
    
    /**
     * Increment UsageWithQuotaLimit.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity);
    
    /**
     * Increment Usage.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    boolean incrementUsage(GroupCapacity groupCapacity);
    
    /**
     * Decrement Usage.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    boolean decrementUsage(GroupCapacity groupCapacity);
    
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
    boolean updateGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize);
    
    /**
     * Delete GroupCapacity.
     *
     * @param group group string value.
     * @return operate result.
     */
    boolean deleteGroupCapacity(final String group);
}
