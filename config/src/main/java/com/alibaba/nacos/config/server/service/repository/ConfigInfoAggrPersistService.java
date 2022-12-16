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

package com.alibaba.nacos.config.server.service.repository;

import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.config.server.model.Page;

import java.util.List;
import java.util.Map;

/**
 * Database service, providing access to config_info_aggr in the database.
 *
 * @author lixiaoshuang
 */
public interface ConfigInfoAggrPersistService {
    
    Object[] EMPTY_ARRAY = new Object[] {};
    
    String PATTERN_STR = "*";
    
    /**
     * create Pagination utils.
     *
     * @param <E> Generic object
     * @return {@link PaginationHelper}
     */
    <E> PaginationHelper<E> createPaginationHelper();
    
    /**
     * Generate fuzzy search Sql.
     *
     * @param s origin string
     * @return fuzzy search Sql
     */
    String generateLikeArgument(String s);
    
    //------------------------------------------insert---------------------------------------------//
    
    /**
     * Add data before aggregation to the database, select -> update or insert .
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param datumId datum id
     * @param appName app name
     * @param content config content
     * @return {@code true} if add success
     */
    boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
            String appName, final String content);
    
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
    
    //------------------------------------------delete---------------------------------------------//
    
    /**
     * Delete a single piece of data before aggregation.
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param datumId datum id
     */
    void removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant, final String datumId);
    
    /**
     * Delete all pre-aggregation data under a dataId.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     */
    void removeAggrConfigInfo(final String dataId, final String group, final String tenant);
    
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
    
    //------------------------------------------update---------------------------------------------//
    
    //------------------------------------------select---------------------------------------------//
    
    /**
     * Get count of aggregation config info.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return count
     */
    int aggrConfigInfoCount(String dataId, String group, String tenant);
    
    /**
     * Get count of aggregation config info.
     *
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     * @param datumIds datum id list
     * @param isIn     search condition
     * @return count
     */
    int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds, boolean isIn);
    
    /**
     * Find a single piece of data before aggregation.
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param datumId datum id
     * @return {@link ConfigInfoAggr}
     */
    ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId);
    
    /**
     * Find all data before aggregation under a dataId. It is guaranteed not to return NULL.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return {@link ConfigInfoAggr} list
     */
    List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant);
    
    /**
     * Query aggregation config info.
     *
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     * @param pageNo   page number
     * @param pageSize page size
     * @return {@link Page} with {@link ConfigInfoAggr} generation
     */
    Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
            final int pageSize);
    
    /**
     * Query eligible aggregated data.
     *
     * @param pageNo     pageNo
     * @param pageSize   pageSize
     * @param configKeys aggregate data conditions
     * @param blacklist  blacklist
     * @return {@link Page} with {@link ConfigInfoAggr} generation
     */
    Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
            boolean blacklist);
    
    /**
     * Find all aggregated data sets.
     *
     * @return {@link ConfigInfoChanged} list
     */
    List<ConfigInfoChanged> findAllAggrGroup();
    
    /**
     * Find datumId by datum content.
     *
     * @param dataId  data id
     * @param groupId group
     * @param content content
     * @return datum keys
     */
    List<String> findDatumIdByContent(String dataId, String groupId, String content);
}
