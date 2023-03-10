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

/**
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoAggrMapper extends Mapper {
    
    /**
     * To delete aggregated data in bulk, you need to specify a size of datum list.
     * The default sql:
     * DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id IN (...)
     *
     * @param datumSize the size of datum list
     * @return The sql of deleting aggregated data in bulk.
     */
    default String batchRemoveAggr(int datumSize) {
        final StringBuilder placeholderString = new StringBuilder();
        for (int i = 0; i < datumSize; i++) {
            if (i != 0) {
                placeholderString.append(", ");
            }
            placeholderString.append('?');
        }
        return "DELETE FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id IN ("
                + placeholderString + ")";
    }

    /**
     * Get count of aggregation config info.
     * The default sql:
     * SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?
     *
     * @param size datum id list size
     * @param isIn search condition
     * @return The sql of getting count of aggregation config info.
     */
    default String aggrConfigInfoCount(int size, boolean isIn) {
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id");
        if (isIn) {
            sql.append(" IN (");
        } else {
            sql.append(" NOT IN (");
        }
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(')');

        return sql.toString();
    }
    
    /**
     * Find all data before aggregation under a dataId. It is guaranteed not to return NULL.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content
     * FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? ORDER BY datum_id
     *
     * @return The sql of finding all data before aggregation under a dataId.
     */
    default String findConfigInfoAggrIsOrdered() {
        return "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id = ? AND "
                + "group_id = ? AND tenant_id = ? ORDER BY datum_id";
    }
    
    /**
     * Query aggregation config info.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND
     * group_id=? AND tenant_id=? ORDER BY datum_id LIMIT startRow,pageSize
     *
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying aggregation config info.
     */
    String findConfigInfoAggrByPageFetchRows(int startRow, int pageSize);
    
    /**
     * Find all aggregated data sets.
     * The default sql:
     * SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr
     *
     * @return The sql of finding all aggregated data sets.
     */
    default String findAllAggrGroupByDistinct() {
        return "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr";
    }
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.CONFIG_INFO_AGGR;
    }
}
