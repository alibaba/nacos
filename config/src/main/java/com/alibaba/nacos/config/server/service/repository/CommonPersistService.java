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

import com.alibaba.nacos.config.server.model.TenantInfo;

import java.util.List;

/**
 * Database service, providing access to other table in the database.
 *
 * @author lixiaoshuang
 */
public interface CommonPersistService {
    
    String PATTERN_STR = "*";
    
    //------------------------------------------insert---------------------------------------------//
    
    /**
     * insert tenant info.
     *
     * @param kp            kp
     * @param tenantId      tenant Id
     * @param tenantName    tenant name
     * @param tenantDesc    tenant description
     * @param createResoure create resouce
     * @param time          time
     */
    void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc, String createResoure,
            final long time);
    
    //------------------------------------------delete---------------------------------------------//
    
    /**
     * Remote tenant info.
     *
     * @param kp       kp
     * @param tenantId tenant id
     */
    void removeTenantInfoAtomic(final String kp, final String tenantId);
    
    //------------------------------------------update---------------------------------------------//
    
    /**
     * Update tenantInfo showname.
     *
     * @param kp         kp
     * @param tenantId   tenant Id
     * @param tenantName tenant name
     * @param tenantDesc tenant description
     */
    void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc);
    
    //------------------------------------------select---------------------------------------------//
    
    /**
     * Query tenant info.
     *
     * @param kp kp
     * @return {@link TenantInfo} list
     */
    List<TenantInfo> findTenantByKp(String kp);
    
    /**
     * Query tenant info.
     *
     * @param kp       kp
     * @param tenantId tenant id
     * @return {@link TenantInfo}
     */
    TenantInfo findTenantByKp(String kp, String tenantId);
    
    /**
     * Generate fuzzy search Sql.
     *
     * @param s origin string
     * @return fuzzy search Sql
     */
    String generateLikeArgument(String s);
    
    /**
     * Determine whether the table exists.
     *
     * @param tableName table name
     * @return {@code true} if table exist
     */
    boolean isExistTable(String tableName);
    
    /**
     * query tenantInfo (namespace) existence based by tenantId.
     *
     * @param tenantId tenant Id
     * @return count by tenantId
     */
    int tenantInfoCountByTenantId(String tenantId);
}
