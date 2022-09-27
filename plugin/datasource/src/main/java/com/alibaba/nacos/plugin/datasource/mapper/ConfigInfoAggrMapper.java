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

import java.util.List;

/**
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoAggrMapper extends Mapper {
    
    /**
     * Query content from config_info_aggr by dataId, groupId, tenantId and datumId.
     * The default sql:
     * SELECT content FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?  AND datum_id = ?
     *
     * @return The sql of querying content from config_info_aggr by dataId, groupId, tenantId and datumId.
     */
    String select();
    
    /**
     * Add configInfoAggr.
     * The default sql:
     * INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, content, gmt_modified) VALUES(?,?,?,?,?,?,?)
     *
     * @return The sql of adding configInfoAggr.
     */
    String insert();
    
    /**
     * Update configInfoAggr.
     * The default sql:
     * UPDATE config_info_aggr SET content = ? , gmt_modified = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id = ?
     *
     * @return The sql of updating configInfoAggr.
     */
    String update();
    
    /**
     * Delete all pre-aggregation data under a dataId.
     * The default sql:
     * DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=?
     *
     * @return The sql of deleting all pre-aggregation data under a dataId.
     */
    String removeAggrConfigInfo();
    
    /**
     * To delete aggregated data in bulk, you need to specify a list of datum.
     * The default sql:
     * DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id IN (...)
     *
     * @param datumList datumList
     * @return The sql of deleting aggregated data in bulk.
     */
    String batchRemoveAggr(List<String> datumList);
    
    /**
     * Delete a single piece of data before aggregation.
     * The default sql:
     * DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id=?
     *
     * @return The sql of deleting a single piece of data before aggregation.
     */
    String removeSingleAggrConfigInfo();
    
    /**
     * Batch replacement, first delete all the specified DataID+Group data in the aggregation table, and then insert the
     * data. Any exception during the transaction process will force a TransactionSystemException to be thrown.
     * The default sql:
     * INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, content, gmt_modified) VALUES(?,?,?,?,?,?,?)
     *
     * @return The sql of batching replacement.
     */
    String replaceAggr();
    
    /**
     * Get count of aggregation config info.
     * The default sql:
     * SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?
     *
     * @param datumIds datum id list
     * @param isIn     search condition
     * @return The sql of getting count of aggregation config info.
     */
    String aggrConfigInfoCount(List<String> datumIds, boolean isIn);
    
    /**
     * Get count of aggregation config info.
     * The default sql:
     * SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?
     *
     * @return The count of getting count of aggregation config info.
     */
    String aggrConfigInfoCount();
    
    /**
     * Find a single piece of data before aggregation.
     * The default sql:
     * SELECT id,data_id,group_id,tenant_id,datum_id,app_name,content
     *  FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id=?
     *
     * @return The sql of finding a single piece of data before aggregation.
     */
    String findSingleConfigInfoAggr();
    
    /**
     * Find all data before aggregation under a dataId. It is guaranteed not to return NULL.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content
     * FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? ORDER BY datum_id
     *
     * @return The sql of finding all data before aggregation under a dataId.
     */
    String findConfigInfoAggr();
    
    /**
     * Query the count of aggregation config info by dataId, groupId and tenantId.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND
     * group_id=? AND tenant_id=? ORDER BY datum_id LIMIT ?,?
     *
     * @return The sql of querying the count of aggregation config info by dataId, groupId and tenantId.
     */
    String findConfigInfoAggrByPageCountRows();
    
    /**
     * Query aggregation config info.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND
     * group_id=? AND tenant_id=? ORDER BY datum_id LIMIT ?,?
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
    String findAllAggrGroup();
    
    /**
     * Find datumId by datum content.
     * The default sql:
     * SELECT datum_id FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND content = ?
     *
     * @return The sql of finding datumId by datum content.
     */
    String findDatumIdByContent();
}
