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
 * @author lixiaoshuang
 */
public interface GroupCapacityMapper extends Mapper {
    
    /**
     * INSERT INTO SELECT statement. Used to insert query results into a table.
     *
     * <p>Example: INSERT INTO group_capacity (group_id, quota,`usage`, `max_size`, max_aggr_count,
     * max_aggr_size,gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info;
     *
     * @return sql.
     */
    String insertIntoSelect();
    
    /**
     * INSERT INTO SELECT statement. Used to insert query results into a table.
     *
     * <p>Where condition: group_id=? AND tenant_id = ''
     *
     * <p>Example: INSERT INTO group_capacity (group_id, quota,`usage`, `max_size`, max_aggr_count,
     * max_aggr_size,gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info where group_id=?
     * AND tenant_id = '';
     *
     * @return sql.
     */
    String insertIntoSelectByWhere();
    
    /**
     * used to increment usage field.
     *
     * <p>Where condition: group_id = ? AND usage < ? AND quota = 0;
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` < ?
     * AND
     * quota = 0;
     *
     * @return
     */
    String incrementUsageByWhereQuotaEqualZero();
    
    /**
     * used to increment usage field.
     *
     * <p>Where condition: group_id = ? AND usage < quota AND quota != 0
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` <
     * quota
     * AND quota != 0;
     *
     * @return
     */
    String incrementUsageByWhereQuotaNotEqualZero();
    
    /**
     * used to increment usage field.
     *
     * <p>Where condition: group_id = ?
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ?;
     *
     * @return
     */
    String incrementUsageByWhere();
    
    /**
     * used to decrement usage field.
     *
     * <p>Where condition: group_id = ? AND `usage` > 0
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE group_id = ? AND `usage` >
     * 0;
     *
     * @return
     */
    String decrementUsageByWhere();
    
    /**
     * used to update usage field.
     *
     * <p>Example: UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE
     * group_id = ?;
     *
     * @return
     */
    String updateUsage();
    
    /**
     * used to update usage field.
     *
     * <p>Where condition: group_id=? AND tenant_id = ''
     *
     * <p>Example: UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id
     * =
     * ''), gmt_modified = ? WHERE group_id= ?;
     *
     * @return
     */
    String updateUsageByWhere();
    
    /**
     * used to select group info.
     *
     * <p>Example: SELECT id, group_id FROM group_capacity WHERE id>? LIMIT ?;
     *
     * @return
     */
    String selectGroupInfoBySize();
    
}
