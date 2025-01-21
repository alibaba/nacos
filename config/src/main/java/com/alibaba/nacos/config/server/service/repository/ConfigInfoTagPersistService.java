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
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.PaginationHelper;

import java.util.List;

/**
 * Database service, providing access to config_info_tag in the database.
 * Deprecated since 2.5.0，only support on compatibility,replaced with ConfigInfoGray model, will be  soon removed on further version.
 * @author lixiaoshuang
 */
@Deprecated
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
     * get config info state.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @param tag    tag.
     * @return config info state.
     */
    ConfigInfoStateWrapper findConfigInfo4TagState(final String dataId, final String group, final String tenant,
            String tag);
    
    /**
     * Add tag configuration information and publish data change events.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @return config operation result.
     */
    ConfigOperateResult addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser);
    
    /**
     * insert or update tag config.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @return config operation result.
     */
    ConfigOperateResult insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser);
    
    /**
     * insert or update tag config cas.
     *
     * @param configInfo config info.
     * @param tag        tag.
     * @param srcIp      remote ip.
     * @param srcUser    user.
     * @return config operation result.
     */
    ConfigOperateResult insertOrUpdateTagCas(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser);
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
     * @return config operation result.
     */
    ConfigOperateResult updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser);
    
    /**
     * Update tag configuration information.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @return success or not.
     */
    ConfigOperateResult updateConfigInfo4TagCas(ConfigInfo configInfo, String tag, String srcIp, String srcUser);
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
    
    /**
     * found all config tags.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @return
     */
    List<String> findConfigInfoTags(final String dataId, final String group, final String tenant);
}
