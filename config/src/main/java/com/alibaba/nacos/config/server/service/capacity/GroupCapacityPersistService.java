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

import java.sql.Timestamp;
import java.util.List;

/**
 * Group Capacity Service.
 *
 * @author mai.jh
 */
public interface GroupCapacityPersistService {
    
    /**
     * get GroupCapacity by groupId.
     *
     * @param groupId group Id
     * @return GroupCapacity
     */
    GroupCapacity getGroupCapacity(String groupId);
    
    /**
     * get GroupCapacity.
     *
     * @return Capacity
     */
    Capacity getClusterCapacity();
    
    /**
     * Insert GroupCapacity into db.
     *
     * @param capacity capacity object instance.
     * @return operate result.
     */
    boolean insertGroupCapacity(final GroupCapacity capacity);
    
    /**
     * get ClusterUsage.
     *
     * @return int
     */
    int getClusterUsage();
    
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
     * @return boolean
     */
    boolean updateGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize);
    
    /**
     * update Quota.
     *
     * @param group group string value.
     * @param quota quota int value.
     * @return boolean
     */
    boolean updateQuota(String group, Integer quota);
    
    /**
     * update Quota.
     *
     * @param group   group string value.
     * @param maxSize max size.
     * @return boolean
     */
    boolean updateMaxSize(String group, Integer maxSize);
    
    /**
     * Correct Usage.
     *
     * @param group       group string value.
     * @param gmtModified gmtModified.
     * @return operate result.
     */
    boolean correctUsage(String group, Timestamp gmtModified);
    
    /**
     * Get group capacity list, noly has id and groupId value.
     *
     * @param lastId   lastId long value.
     * @param pageSize pageSize long value.
     * @return GroupCapacity list.
     */
    List<GroupCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize);
    
    /**
     * Delete GroupCapacity.
     *
     * @param group group string value.
     * @return operate result.
     */
    boolean deleteGroupCapacity(final String group);
    
}
