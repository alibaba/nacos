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

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;

import java.sql.Timestamp;

/**
 * Database service, providing access to config_info_tag in the database.
 *
 * @author lixiaoshuang
 */
public interface ConfigInfoTagPersistService {
    
    /**
     * create Pagination utils.
     *
     * @param <E> Generic object
     * @return {@link PaginationHelper}
     */
    <E> PaginationHelper<E> createPaginationHelper();
    
    //------------------------------------------insert---------------------------------------------//
    
    /**
     * Add tag configuration information and publish data change events.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
    /**
     * insert or update tag config.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp, final String srcUser,
            final Timestamp time, final boolean notify);
    
    /**
     * insert or update tag config cas.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     * @return success or not.
     */
    boolean insertOrUpdateTagCas(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify);
    //------------------------------------------delete---------------------------------------------//
    
    /**
     * Delete configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId  dataId
     * @param group   group
     * @param tenant  tenant
     * @param tag     tag
     * @param srcIp   remote ip
     * @param srcUser user
     */
    void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
            final String srcIp, final String srcUser);
    //------------------------------------------update---------------------------------------------//
    
    /**
     * Update tag configuration information.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    void updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
    /**
     * Update tag configuration information.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     * @return success or not.
     */
    boolean updateConfigInfo4TagCas(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    //------------------------------------------select---------------------------------------------//
    
    /**
     * Query tag configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @param tag    tag
     * @return {@link ConfigInfo4Tag}
     */
    ConfigInfoTagWrapper findConfigInfo4Tag(final String dataId, final String group, final String tenant,
            final String tag);
    
    /**
     * Returns the number of beta configuration items.
     *
     * @return number of configuration items..
     */
    int configInfoTagCount();
    
    /**
     * Query all tag config info for dump task.
     *
     * @param pageNo   page numbser
     * @param pageSize page sizxe
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo, final int pageSize);
    
}
