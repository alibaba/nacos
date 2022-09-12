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
 * The group capacity mapper.
 *
 * @author hyx
 **/

public interface GroupCapacityMapper extends Mapper {
    
    /**
     * Get the group capacity.
     * The default sql:
     * SELECT id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, group_id FROM group_capacity
     * WHERE group_id=?
     *
     * @return The sql of getting the group capacity.
     */
    String getGroupCapacity();
    
    /**
     * Insert GroupCapacity into db.
     * The default sql:
     * INSERT INTO group_capacity (group_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size,
     * gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info;
     *
     * @return The sql of add group capacity.
     */
    String insertGroupCapacity();
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     * The default sql:
     * UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ?
     * WHERE group_id = ? AND `usage` < ? AND quota = 0
     *
     * @return The sql of incrementing UsageWithDefaultQuotaLimit.
     */
    String incrementUsageWithDefaultQuotaLimit();
    
    /**
     * Increment UsageWithQuotaLimit.
     * The default sql:
     * UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ?
     * WHERE group_id = ? AND `usage` < quota AND quota != 0
     *
     * @return The sql of incrementing UsageWithQuotaLimit.
     */
    String incrementUsageWithQuotaLimit();
    
    /**
     * Increment Usage.
     * The default sql:
     * UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ?
     *
     * @return The sql of incrementing Usage.
     */
    String incrementUsage();
    
    /**
     * Decrement Usage.
     * The default sql:
     * UPDATE group_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE group_id = ? AND `usage` > 0
     *
     * @return The sql of decrementing usage.
     */
    String decrementUsage();
    
    /**
     * Update GroupCapacity.
     * The default sql:
     * update group_capacity set ... gmt_modified = ? WHERE group_id = ?
     *
     * @return The sql of updating group capacity.
     */
    String updateGroupCapacity();
    
    /**
     * Correct Usage.
     * The default sql:
     * UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE
     * group_id = ?
     *
     * @return The sql of correcting usage.
     */
    String correctUsage();
    
    /**
     * Get group capacity list, noly has id and groupId value.
     * The default sql:
     * SELECT id, group_id FROM group_capacity WHERE id>? LIMIT ?
     *
     * @return The sql of getting group capacity list.
     */
    String getCapacityList4CorrectUsage();
    
    /**
     * Delete GroupCapacity.
     * The default sql:
     * DELETE FROM group_capacity WHERE group_id = ?;
     *
     * @return The sql of deleting group capacity.
     */
    String deleteGroupCapacity();
}
