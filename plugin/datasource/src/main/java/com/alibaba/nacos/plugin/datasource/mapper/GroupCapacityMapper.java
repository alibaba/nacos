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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

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
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult insertIntoSelect(MapperContext context) {
        List<Object> paramList = new ArrayList<>();
        paramList.add(context.getUpdateParameter(FieldConstant.GROUP_ID));
        paramList.add(context.getUpdateParameter(FieldConstant.QUOTA));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_SIZE));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_AGGR_COUNT));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_AGGR_SIZE));
        paramList.add(context.getUpdateParameter(FieldConstant.GMT_CREATE));
        paramList.add(context.getUpdateParameter(FieldConstant.GMT_MODIFIED));
        
        String sql =
                "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count, max_aggr_size,gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info";
        return new MapperResult(sql, paramList);
    }
    
    /**
     * INSERT INTO SELECT statement. Used to insert query results into a table.
     *
     * <p>Where condition: group_id=? AND tenant_id = '{defaultNamespaceId}'
     *
     * <p>Example: INSERT INTO group_capacity (group_id, quota,`usage`, `max_size`, max_aggr_count,
     * max_aggr_size,gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info where group_id=?
     * AND tenant_id = '{defaultNamespaceId}';
     *
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult insertIntoSelectByWhere(MapperContext context) {
        final String sql =
                "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count, max_aggr_size, gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE group_id=? AND tenant_id = '"
                        + NamespaceUtil.getNamespaceDefaultId() + "'";
        List<Object> paramList = new ArrayList<>();
        paramList.add(context.getUpdateParameter(FieldConstant.GROUP_ID));
        paramList.add(context.getUpdateParameter(FieldConstant.QUOTA));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_SIZE));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_AGGR_COUNT));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_AGGR_SIZE));
        paramList.add(context.getUpdateParameter(FieldConstant.GMT_CREATE));
        paramList.add(context.getUpdateParameter(FieldConstant.GMT_MODIFIED));
        
        paramList.add(context.getWhereParameter(FieldConstant.GROUP_ID));
        
        return new MapperResult(sql, paramList);
    }
    
    /**
     * used to increment usage field.
     *
     * <p>Where condition: group_id = ? AND usage < ? AND quota = 0;
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` < ?
     * AND
     * quota = 0;
     *
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult incrementUsageByWhereQuotaEqualZero(MapperContext context) {
        return new MapperResult(
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < ? AND quota = 0",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.GROUP_ID),
                        context.getWhereParameter(FieldConstant.USAGE)));
    }
    
    /**
     * used to increment usage field.
     *
     * <p>Where condition: group_id = ? AND usage < quota AND quota != 0
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` <
     * quota
     * AND quota != 0;
     *
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult incrementUsageByWhereQuotaNotEqualZero(MapperContext context) {
        return new MapperResult(
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < quota AND quota != 0",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.GROUP_ID)));
    }
    
    /**
     * used to increment usage field.
     *
     * <p>Where condition: group_id = ?
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ?;
     *
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult incrementUsageByWhere(MapperContext context) {
        return new MapperResult("UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ?",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.GROUP_ID)));
    }
    
    /**
     * used to decrement usage field.
     *
     * <p>Where condition: group_id = ? AND `usage` > 0
     *
     * <p>Example: UPDATE group_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE group_id = ? AND `usage` >
     * 0;
     *
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult decrementUsageByWhere(MapperContext context) {
        return new MapperResult(
                "UPDATE group_capacity SET usage = usage - 1, gmt_modified = ? WHERE group_id = ? AND usage > 0",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.GROUP_ID)));
    }
    
    /**
     * used to update usage field.
     *
     * <p>Example: UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE
     * group_id = ?;
     *
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult updateUsage(MapperContext context) {
        return new MapperResult(
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE group_id = ?",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.GROUP_ID)));
    }
    
    /**
     * used to update usage field.
     *
     * <p>Where condition: group_id=? AND tenant_id = '{defaultNamespaceId}'
     *
     * <p>Example: UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id
     * =
     * ''), gmt_modified = ? WHERE group_id= ?;
     *
     * @param context sql paramMap
     * @return sql.
     */
    default MapperResult updateUsageByWhere(MapperContext context) {
        return new MapperResult(
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id = '"
                        + NamespaceUtil.getNamespaceDefaultId() + "')," + " gmt_modified = ? WHERE group_id= ?",
                CollectionUtils.list(context.getWhereParameter(FieldConstant.GROUP_ID),
                        context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.GROUP_ID)));
    }
    
    /**
     * used to select group info.
     *
     * <p>Example: SELECT id, group_id FROM group_capacity WHERE id>? LIMIT ?;
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult selectGroupInfoBySize(MapperContext context);
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.GROUP_CAPACITY;
    }
}
