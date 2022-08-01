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

import java.util.List;
import java.util.Map;

/**
 * The aggregation of config info data.
 *
 * @author hyx
 **/

public interface ConfigInfoAggrMapper {
    /**
     * Add data before aggregation to the database, select -> update or insert .
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param datumId datum id
     * @param appName app name
     * @param content config content
     * @return the number of data inserted.
     */
    Integer addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
            String appName, final String content);
    
    /**
     * Delete a single piece of data before aggregation.
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param datumId datum id
     * @return the number of removed.
     */
    Integer removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant, final String datumId);
    
    /**
     * Delete all pre-aggregation data under a dataId.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return the number of removed.
     */
    Integer removeAggrConfigInfo(final String dataId, final String group, final String tenant);
    
    /**
     * To delete aggregated data in bulk, you need to specify a list of datum.
     *
     * @param dataId    dataId
     * @param group     group
     * @param tenant    tenant
     * @param datumList datumList
     * @return {@code true} if remove success
     */
    boolean batchRemoveAggr(final String dataId, final String group, final String tenant, final List<String> datumList);
    
    /**
     * Add or update data in batches. Any exception during the transaction will force a TransactionSystemException to be
     * thrown.
     *
     * @param dataId   dataId
     * @param group    group
     * @param tenant   tenant
     * @param appName  app name
     * @param datumMap datumMap
     * @return {@code true} if publish success
     */
    boolean batchPublishAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName);
    
    /**
     * Batch replacement, first delete all the specified DataID+Group data in the aggregation table, and then insert the
     * data. Any exception during the transaction process will force a TransactionSystemException to be thrown.
     *
     * @param dataId   dataId
     * @param group    group
     * @param tenant   tenant
     * @param appName  app name
     * @param datumMap datumMap
     * @return {@code true} if replace success
     */
    boolean replaceAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName);
}
