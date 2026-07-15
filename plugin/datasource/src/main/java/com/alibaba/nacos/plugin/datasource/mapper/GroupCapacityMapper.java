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
 * The group capacity info mapper.
 *
 * @author lixiaoshuang
 */
public interface GroupCapacityMapper extends Mapper {
    
    /**
     * Select group_capacity table by group id.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult select(MapperContext context);
    
    /**
     * INSERT INTO SELECT statement.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult insertIntoSelect(MapperContext context);
    
    /**
     * INSERT INTO SELECT statement. Used to insert query results into a table.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult insertIntoSelectByWhere(MapperContext context);
    
    /**
     * Used to increment usage field.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult incrementUsageByWhereQuotaEqualZero(MapperContext context);
    
    /**
     * Used to increment usage field.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult incrementUsageByWhereQuotaNotEqualZero(MapperContext context);
    
    /**
     * Used to increment usage field.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult incrementUsageByWhere(MapperContext context);
    
    /**
     * Used to decrement usage field.
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult decrementUsageByWhere(MapperContext context);
    
    /**
     * Used to update usage field.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult updateUsage(MapperContext context);
    
    /**
     * Used to update usage field.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult updateUsageByWhere(MapperContext context);
    
    /**
     * Used to select group info.
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult selectGroupInfoBySize(MapperContext context);
    
    /**
     * Return table name.
     *
     * @return table name
     */
    default String getTableName() {
        return TableConstant.GROUP_CAPACITY;
    }
}
