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

import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

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
     * @param context The context of datum_id, data_id, group_id, tenant_id
     * @return The sql of deleting aggregated data in bulk.
     */
    MapperResult batchRemoveAggr(MapperContext context);
    
    /**
     * Get count of aggregation config info.
     * The default sql:
     * SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?
     *
     * @param context The context of datum_id, isIn, data_id, group_id, tenant_id
     * @return The sql of getting count of aggregation config info.
     */
    MapperResult aggrConfigInfoCount(MapperContext context);
    
    /**
     * Find all data before aggregation under a dataId. It is guaranteed not to return NULL.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content
     * FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? ORDER BY datum_id
     *
     * @param context The context of data_id, group_id, tenant_id
     * @return The sql of finding all data before aggregation under a dataId.
     */
    MapperResult findConfigInfoAggrIsOrdered(MapperContext context);
    
    /**
     * Query aggregation config info.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND
     * group_id=? AND tenant_id=? ORDER BY datum_id LIMIT startRow,pageSize
     *
     * @param context The context of startRow, pageSize, data_id, group_id, tenant_id
     * @return The sql of querying aggregation config info.
     */
    MapperResult findConfigInfoAggrByPageFetchRows(MapperContext context);
    
    /**
     * Find all aggregated data sets.
     * The default sql:
     * SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr
     *
     * @return The sql of finding all aggregated data sets.
     */
    String findAllAggrGroupByDistinct();
}
