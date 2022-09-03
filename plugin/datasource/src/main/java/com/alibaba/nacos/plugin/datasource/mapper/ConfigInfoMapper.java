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
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoMapper {
    
    /**
     * Update md5.
     * @return the sql of updating md5.
     */
    String updateMd5();
    
    /**
     * Get the maxId.
     * @return the sql of getting the maxId.
     */
    String findConfigMaxId();
    
    /**
     * Find all dataId and group.
     * @return The sql of finding all dataId and group.
     */
    String findAllDataIdAndGroup();
    
    /**
     * Query common configuration information based on dataId and group.
     * @return Query common configur.
     */
    String findConfigInfoApp();
    
    /**
     * Query configuration information based on dataId and group.
     * @return The sql to select config_info by dataId and group.
     */
    String findConfigInfoBase();
    
    /**
     * Query configuration information by primary key ID.
     * @return The sql to select configInfo by ID.
     */
    String findConfigInfo();
    
    /**
     * Query configuration information based on dataId.
     * @return The sql to
     */
    String findConfigInfoByDataIdFetchRows();
    
    /**
     * Query the count of the configInfo by dataId.
     * @return The num of the count of configInfo.
     */
    String findConfigInfoByDataIdAndAppCount();
    
    /**
     * Query configuration information based on dataId.
     * @return The sql of query configuration information based on dataId.
     */
    String findConfigInfoByDataIdAndAppFetchRows();
    
    /**
     * The count of config_info table sql.
     * @return The sql of the count of config_info table.
     */
    String count();
    
    
    /**
     * Query the count of config_info by tenantId and appName.
     * @return The sql of querying the count of config_info.
     */
    String findConfigInfoByAppCountRows();
    
    /**
     * Query configuration information based on group.
     * @return The sql of querying configration information based on group.
     */
    String findConfigInfoByAppFetchRows();
    
    /**
     * Returns the number of configuration items.
     * @return The sql of querying the number of configuration items.
     */
    String configInfoLikeTenantCount();
    
    /**
     * Get tenant id list  by page.
     * @return The sql of getting tenant id list  by page.
     */
    String getTenantIdList();
    
    /**
     * Get group id list  by page.
     * @return The sql of getting group id list  by page.
     */
    String getGroupIdList();
    
    /**
     * Query all configuration information by page.
     * @return The sql of querying all configuration information.
     */
    String findAllConfigKey();
    
    /**
     * Query all configuration information by page.
     * @return The sql of querying all configuration information by page.
     */
    String findAllConfigInfoBaseFetchRows();
    
    /**
     * Query all configuration information by page for dump task.
     * @return The sql of querying all configuration information by page for dump task.
     */
    String findAllConfigInfoForDumpAllFetchRows();
    
    /**
     * Query all config info.
     * @return The sql of querying all config info.
     */
    String findAllConfigInfoFragment();
    
    /**
     * Query change config.
     * @return The sql of querying change config.
     */
    String findChangeConfig();
    
    /**
     * Add configuration; database atomic operation, minimum sql action, no business encapsulation.
     * @return The sql of adding configuration.
     */
    String addConfigInfoAtomic();
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     * @return The sql of removing configuration.
     */
    String removeConfigInfoAtomic();
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation.
     * @return The sql of updating configuration.
     */
    String updateConfigInfoAtomic();
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     * @return The sql of querying configuration information.
     */
    String findConfigAdvanceInfo();
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     * @return The sql of getting all config info.
     */
    String findConfigAllInfo();
    
    /**
     * list group key md5 by page.
     * @return The sql of listing group key md5 by page.
     */
    String listGroupKeyMd5ByPageFetchRows();
    
    /**
     * Query config info.
     * @return The sql of querying config info.
     */
    String queryConfigInfo();
    
    /**
     * Query dataId list by namespace.
     * @return The sql of querying dataId list by namespace.
     */
    String queryConfigInfoByNamespace();
}
