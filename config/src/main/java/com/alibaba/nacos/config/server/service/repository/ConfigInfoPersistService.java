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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.PaginationHelper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Database service, providing access to config_info in the database.
 *
 * @author lixiaoshuang
 */
public interface ConfigInfoPersistService {
    
    String PATTERN_STR = "*";
    Object[] EMPTY_ARRAY = new Object[] {};
    
    
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
     * Add common configuration information and publish data change events.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param configAdvanceInfo advance info
     * @return config operation result.
     */
    ConfigOperateResult addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Map<String, Object> configAdvanceInfo);
    
    /**
     * insert or update.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param configAdvanceInfo advance info
     * @return config operation result.
     */
    ConfigOperateResult insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo);
    
    /**
     * Write to the main table, insert or update cas.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param configAdvanceInfo advance info
     * @return success or not.
     */
    ConfigOperateResult insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo);
    
    /**
     * Add configuration; database atomic operation, minimum sql action, no business encapsulation.
     *
     * @param id                id
     * @param srcIp             ip
     * @param srcUser           user
     * @param configInfo        info
     * @param configAdvanceInfo advance info
     * @return execute sql result
     */
    long addConfigInfoAtomic(final long id, final String srcIp, final String srcUser, final ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo);
    
    /**
     * Add configuration; database atomic operation, minimum sql action, no business encapsulation.
     *
     * @param configId id
     * @param tagName  tag
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     */
    void addConfigTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant);
    
    /**
     * Add configuration; database atomic operation.
     *
     * @param configId   config id
     * @param configTags tags
     * @param dataId     dataId
     * @param group      group
     * @param tenant     tenant
     */
    void addConfigTagsRelation(long configId, String configTags, String dataId, String group, String tenant);
    
    /**
     * batch operation,insert or update the format of the returned: succCount: number of successful imports skipCount:
     * number of import skips (only with skip for the same configs) failData: import failed data (only with abort for
     * the same configs) skipData: data skipped at import  (only with skip for the same configs).
     *
     * @param configInfoList    config info list
     * @param srcUser           user
     * @param srcIp             remote ip
     * @param configAdvanceInfo advance info
     * @param policy            {@link SameConfigPolicy}
     * @return map containing the number of affected rows
     * @throws NacosException nacos exception
     */
    Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
            Map<String, Object> configAdvanceInfo, SameConfigPolicy policy) throws NacosException;
    
    //------------------------------------------delete---------------------------------------------//
    
    /**
     * Delete configuration information, physical deletion.
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param srcIp   remote ip
     * @param srcUser user
     */
    void removeConfigInfo(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser);
    
    /**
     * Delete config info by ids.
     *
     * @param ids     id list
     * @param srcIp   remote ip
     * @param srcUser user
     * @return {@link ConfigAllInfo} list
     * @author klw
     */
    List<ConfigAllInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser);
    
    /**
     * Delete tag.
     *
     * @param id id
     */
    void removeTagByIdAtomic(long id);
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId  dataId
     * @param group   group
     * @param tenant  tenant
     * @param srcIp   ip
     * @param srcUser user
     */
    void removeConfigInfoAtomic(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser);
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param ids ids
     */
    void removeConfigInfoByIdsAtomic(final String ids);
    
    //------------------------------------------update---------------------------------------------//
    
    /**
     * Update common configuration information.
     *
     * @param configInfo        config info
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configAdvanceInfo advance info
     * @return config operation result.
     */
    ConfigOperateResult updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Map<String, Object> configAdvanceInfo);
    
    /**
     * Update common configuration information.
     *
     * @param configInfo        config info
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configAdvanceInfo advance info
     * @return config operation result.
     */
    ConfigOperateResult updateConfigInfoCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Map<String, Object> configAdvanceInfo);
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param configInfo        config info
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configAdvanceInfo advance info
     */
    void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            Map<String, Object> configAdvanceInfo);
    
    //------------------------------------------select---------------------------------------------//
    
    /**
     * Get the maxId.
     *
     * @return config max id
     */
    long findConfigMaxId();
    
    /**
     * Query configuration information by primary key ID.
     *
     * @param id id
     * @return {@link ConfigInfo}
     */
    ConfigInfo findConfigInfo(long id);
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return config info
     */
    ConfigInfoWrapper findConfigInfo(final String dataId, final String group, final String tenant);
    
    /**
     * find config info.
     *
     * @param pageNo            page number
     * @param pageSize          page size
     * @param dataId            data id
     * @param group             group
     * @param tenant            tenant
     * @param configAdvanceInfo advance info
     * @return {@link Page} with {@link ConfigInfo} generation
     */
    Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId, final String group,
            final String tenant, final Map<String, Object> configAdvanceInfo);
    
    
    /**
     * Returns the number of configuration items.
     *
     * @return number of configuration items.
     */
    int configInfoCount();
    
    /**
     * Returns the number of configuration items.
     *
     * @param tenant tenant
     * @return number of configuration items.
     */
    int configInfoCount(String tenant);
    
    /**
     * get tenant id list  by page.
     *
     * @param page     page number
     * @param pageSize page size
     * @return tenant id list
     */
    List<String> getTenantIdList(int page, int pageSize);
    
    /**
     * get group id list  by page.
     *
     * @param page     page number
     * @param pageSize page size
     * @return group id list
     */
    List<String> getGroupIdList(int page, int pageSize);
    
    /**
     * Query all config info.
     *
     * @param lastMaxId   last max id
     * @param pageSize    page size
     * @param needContent need content or not.
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize, boolean needContent);
    
    /**
     * Query config info.
     *
     * @param pageNo            page number
     * @param pageSize          page size
     * @param dataId            data id
     * @param group             group
     * @param tenant            tenant
     * @param configAdvanceInfo advance info
     * @return {@link Page} with {@link ConfigInfo} generation
     */
    Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo);
    
    /**
     * Query change config.order by id asc.
     *
     * @param startTime start time
     * @param lastMaxId lastMaxId
     * @param pageSize  pageSize
     * @return {@link ConfigInfoWrapper} list
     */
    List<ConfigInfoStateWrapper> findChangeConfig(final Timestamp startTime, long lastMaxId, final int pageSize);
    
    /**
     * Query tag list.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return tag list
     */
    List<String> selectTagByConfig(String dataId, String group, String tenant);
    
    /**
     * find ConfigInfo by ids.
     *
     * @param ids id list
     * @return {@link com.alibaba.nacos.config.server.model.ConfigInfo} list
     * @author klw
     * @date 2019/7/5 16:37
     */
    List<ConfigInfo> findConfigInfosByIds(final String ids);
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return advance info
     */
    ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant);
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return advance info
     */
    ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant);
    
    /**
     * get config info state.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @return config info state.
     */
    ConfigInfoStateWrapper findConfigInfoState(final String dataId, final String group, final String tenant);
    
    /**
     * query all configuration information according to group, appName, tenant (for export).
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param appName appName
     * @param ids     ids
     * @return Collection of ConfigInfo objects
     */
    List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
            final String appName, final List<Long> ids);
    
    /**
     * Query dataId list by namespace.
     *
     * @param tenantId tenantId
     * @return {@link ConfigInfoBase}
     */
    List<ConfigInfoWrapper> queryConfigInfoByNamespace(final String tenantId);
    
}
