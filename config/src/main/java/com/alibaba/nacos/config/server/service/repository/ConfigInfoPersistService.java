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
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;

import java.io.IOException;
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
     * @param time              time
     * @param configAdvanceInfo advance info
     * @param notify            whether to push
     */
    void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo, final Timestamp time,
            final Map<String, Object> configAdvanceInfo, final boolean notify);
    
    /**
     * insert or update.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     */
    void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo);
    
    /**
     * Write to the main table, insert or update.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @param notify            whether to push
     */
    void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo, boolean notify);
    
    /**
     * insert or update cas.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @return success or not.
     */
    boolean insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo);
    
    /**
     * Write to the main table, insert or update cas.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @param notify            whether to push
     * @return success or not.
     */
    boolean insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo, boolean notify);
    
    /**
     * Add configuration; database atomic operation, minimum sql action, no business encapsulation.
     *
     * @param id                id
     * @param srcIp             ip
     * @param srcUser           user
     * @param configInfo        info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @return execute sql result
     */
    long addConfigInfoAtomic(final long id, final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Timestamp time, Map<String, Object> configAdvanceInfo);
    
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
     * @param time              time
     * @param notify            whether to push
     * @param policy            {@link SameConfigPolicy}
     * @return map containing the number of affected rows
     * @throws NacosException nacos exception
     */
    Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
            Map<String, Object> configAdvanceInfo, Timestamp time, boolean notify, SameConfigPolicy policy)
            throws NacosException;
    
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
     * @return {@link ConfigInfo} list
     * @author klw
     */
    List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser);
    
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
     * @param time              time
     * @param configAdvanceInfo advance info
     * @param notify            whether to push
     */
    void updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser, final Timestamp time,
            final Map<String, Object> configAdvanceInfo, final boolean notify);
    
    /**
     * Update common configuration information.
     *
     * @param configInfo        config info
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param time              time
     * @param configAdvanceInfo advance info
     * @param notify            whether to push
     * @return success or not.
     */
    boolean updateConfigInfoCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify);
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param configInfo        config info
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param time              time
     * @param configAdvanceInfo advance info
     */
    void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, Map<String, Object> configAdvanceInfo);
    
    /**
     * update md5.
     *
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     * @param md5      md5
     * @param lastTime last modified time
     */
    void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime);
    
    //------------------------------------------select---------------------------------------------//
    
    /**
     * Get the maxId.
     *
     * @return config max id
     */
    long findConfigMaxId();
    
    /**
     * Find all dataId and group. It is guaranteed not to return NULL.
     *
     * @return {@link com.alibaba.nacos.config.server.Config} list
     */
    @Deprecated
    List<ConfigInfo> findAllDataIdAndGroup();
    
    /**
     * Query configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @return {@link ConfigInfoBase}
     */
    ConfigInfoBase findConfigInfoBase(final String dataId, final String group);
    
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
     * Query configuration information based on group.
     *
     * @param pageNo   Page number (must be greater than 0)
     * @param pageSize Page size (must be greater than 0)
     * @param tenant   tenant
     * @param appName  app name
     * @return {@link Page} with {@link ConfigInfo} generation
     */
    Page<ConfigInfo> findConfigInfoByApp(final int pageNo, final int pageSize, final String tenant,
            final String appName);
    
    /**
     * Query configuration information based on group.
     *
     * @param pageNo   Page number (must be greater than 0)
     * @param pageSize Page size (must be greater than 0)
     * @param group    group
     * @return {@link Page} with {@link ConfigInfoBase} generation
     */
    Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo, final int pageSize, final String group);
    
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
     * Query all configuration information by page.
     *
     * @param pageNo   Page number (starting at 1)
     * @param pageSize Page size (must be greater than 0)
     * @param tenant   tenant
     * @return {@link Page} with {@link ConfigInfo} generation
     */
    Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize, final String tenant);
    
    /**
     * Query all configuration information by page.
     *
     * @param pageNo   Page number (starting at 1)
     * @param pageSize Page size (must be greater than 0)
     * @param tenant   tenant
     * @return {@link Page} with {@link ConfigKey} generation
     */
    Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant);
    
    /**
     * Query all config info.
     *
     * @param lastMaxId last max id
     * @param pageSize  page size
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize);
    
    /**
     * Fuzzy query configuration information based on dataId and group.
     *
     * @param pageNo     Page number (must be greater than 0)
     * @param pageSize   Page size (must be greater than 0)
     * @param configKeys Query configuration list
     * @param blacklist  Whether to blacklist
     * @return {@link Page} with {@link ConfigInfo} generation
     */
    Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final ConfigKey[] configKeys,
            final boolean blacklist);
    
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
     * Fuzzy query configuration information based on dataId and group.
     *
     * @param pageNo   Page number (must be greater than 0)
     * @param pageSize Page size (must be greater than 0)
     * @param dataId   data id
     * @param group    group
     * @param content  config content
     * @return {@link Page} with {@link ConfigInfoBase} generation
     * @throws IOException exception
     */
    Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo, final int pageSize, final String dataId,
            final String group, final String content) throws IOException;
    
    /**
     * Query change config.
     *
     * @param startTime start time
     * @param endTime   end time
     * @return {@link ConfigInfoWrapper} list
     */
    List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime, final Timestamp endTime);
    
    /**
     * According to the time period and configuration conditions to query the eligible configuration.
     *
     * @param dataId    dataId Support Fuzzy query
     * @param group     dataId Support Fuzzy query
     * @param tenant    tenant
     * @param appName   app name
     * @param startTime start time
     * @param endTime   end time
     * @param pageNo    pageNo
     * @param pageSize  pageSize
     * @param lastMaxId last max id
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    Page<ConfigInfoWrapper> findChangeConfig(final String dataId, final String group, final String tenant,
            final String appName, final Timestamp startTime, final Timestamp endTime, final int pageNo,
            final int pageSize, final long lastMaxId);
    
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
     * Convert delete config.
     *
     * @param list origin data
     * @return {@link ConfigInfo} list
     */
    List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list);
    
    /**
     * Convert change config.
     *
     * @param list origin data
     * @return {@link ConfigInfoWrapper} list
     */
    List<ConfigInfoWrapper> convertChangeConfig(List<Map<String, Object>> list);
    
    
    /**
     * Get the Md5 value of all configurations, through the paging method.
     *
     * @return {@link ConfigInfoWrapper} list
     */
    List<ConfigInfoWrapper> listAllGroupKeyMd5();
    
    /**
     * list group key md5 by page.
     *
     * @param pageNo   page no
     * @param pageSize page size
     * @return {@link ConfigInfoWrapper} list
     */
    List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize);
    
    /**
     * Query config info.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return {@link ConfigInfoWrapper}
     */
    ConfigInfoWrapper queryConfigInfo(final String dataId, final String group, final String tenant);
    
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
    
    /**
     * Query all configuration information by page.
     *
     * @param pageNo   Page number (starting at 1)
     * @param pageSize Page size (must be greater than 0)
     * @return {@link Page} with {@link ConfigInfoBase} generation
     */
    @Deprecated
    Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo, final int pageSize);
}
