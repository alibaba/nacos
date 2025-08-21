/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.persistence.repository.PaginationHelper;

import java.util.List;

/**
 * The interface Config migrate persist service.
 *
 * @author Sunrisea
 */
public interface ConfigMigratePersistService {
    
    /**
     * Create pagination helper pagination helper.
     *
     * @param <E> the type parameter
     * @return the pagination helper
     */
    <E> PaginationHelper<E> createPaginationHelper();
    
    /**
     * Config info conflict count integer.
     *
     * @param srcUser the src user
     * @return the integer
     */
    Integer configInfoConflictCount(String srcUser);
    
    /**
     * Config info gray conflict count integer.
     *
     * @param srcUser the src user
     * @return the integer
     */
    Integer configInfoGrayConflictCount(String srcUser);
    
    /**
     * Gets migrate config id list.
     *
     * @param startId  the start id
     * @param pageSize the page size
     * @return the migrate config id list
     */
    List<Long> getMigrateConfigInsertIdList(long startId, int pageSize);
    
    /**
     * Gets migrate config gray id list.
     *
     * @param startId  the start id
     * @param pageSize the page size
     * @return the migrate config gray id list
     */
    List<Long> getMigrateConfigGrayInsertIdList(long startId, int pageSize);
    
    /**
     * Gets migrate config update list.
     *
     * @param startId      the start id
     * @param pageSize     the page size
     * @param srcTenant    the src tenant
     * @param targetTenant the target tenant
     * @param srcUser      the src user
     * @return the migrate config update list
     */
    List<ConfigInfo> getMigrateConfigUpdateList(long startId, int pageSize, String srcTenant, String targetTenant,
            String srcUser);
    
    /**
     * Gets migrate config gray update list.
     *
     * @param startId      the start id
     * @param pageSize     the page size
     * @param srcTenant    the src tenant
     * @param targetTenant the target tenant
     * @param srcUser      the src user
     * @return the migrate config gray update list
     */
    List<ConfigInfoGrayWrapper> getMigrateConfigGrayUpdateList(long startId, int pageSize, String srcTenant,
            String targetTenant, String srcUser);
    
    /**
     * Migrate config by ids.
     *
     * @param ids     the ids
     * @param srcUser the src user
     */
    void migrateConfigInsertByIds(List<Long> ids, String srcUser);
    
    /**
     * Migrate config gray by ids.
     *
     * @param ids     the ids
     * @param srcUser the src user
     */
    void migrateConfigGrayInsertByIds(List<Long> ids, String srcUser);
    
    /**
     * Sync config gray.
     *
     * @param dataId       the data id
     * @param group        the group
     * @param tenant       the tenant
     * @param grayName     the gray name
     * @param targetTenant the target tenant
     * @param srcUser      the src user
     */
    void syncConfigGray(String dataId, String group, String tenant, String grayName, String targetTenant,
            String srcUser);
    
    /**
     * Sync config.
     *
     * @param dataId       the data id
     * @param group        the group
     * @param tenant       the tenant
     * @param targetTenant the target tenant
     * @param srcUser      the src user
     */
    void syncConfig(String dataId, String group, String tenant, String targetTenant, String srcUser);
}
