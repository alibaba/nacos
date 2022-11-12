/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.TenantInfo;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Database service, providing access to ConfigInfo in the database
 * <br> 3.0 starts to increase the data version number, and changes the physical deletion to logical deletion
 * <br> 3.0 adds the database switching function.
 *
 * @author boyan
 * @author leiwen.zh
 * @author klw
 * @since 1.0
 *
 * <br> PersistService has been deprecated and will be removed in future versions. It is recommended to use
 * ConfigInfoPersistService, ConfigInfoTagPersistService, ConfigInfoBetaPersistService, ConfigInfoAggrPersistService,
 * HistoryConfigInfoPersistService, OtherPersistService
 */
@Deprecated
public interface PersistService {
    
    /**
     * constant variables.
     */
    String SPOT = ".";
    
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
     * Add common configuration information and publish data change events.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @param notify            whether to push
     */
    @Deprecated
    void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo, final Timestamp time,
            final Map<String, Object> configAdvanceInfo, final boolean notify);
    
    /**
     * Add beta configuration information and publish data change events.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    @Deprecated
    void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
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
    @Deprecated
    void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
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
    @Deprecated
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
    @Deprecated
    boolean updateConfigInfoCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify);
    
    /**
     * Update beta configuration information.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    @Deprecated
    void updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
    /**
     * Update beta configuration information.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     * @return success or not.
     */
    @Deprecated
    boolean updateConfigInfo4BetaCas(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time, boolean notify);
    
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
    @Deprecated
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
    @Deprecated
    boolean updateConfigInfo4TagCas(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify);
    
    /**
     * insert or update beta config.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     */
    @Deprecated
    void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp, final String srcUser,
            final Timestamp time, final boolean notify);
    
    /**
     * insert or update beta config cas.
     *
     * @param configInfo config info
     * @param betaIps    ip for push
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @param notify     whether to push
     * @return success or not.
     */
    @Deprecated
    boolean insertOrUpdateBetaCas(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify);
    
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
    @Deprecated
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
    @Deprecated
    boolean insertOrUpdateTagCas(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify);
    
    /**
     * update md5.
     *
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     * @param md5      md5
     * @param lastTime last modified time
     */
    @Deprecated
    void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime);
    
    /**
     * insert or update.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     */
    @Deprecated
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
    @Deprecated
    void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo, boolean notify);
    
    /**
     * insert or update cas..
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @return success or not.
     */
    @Deprecated
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
    @Deprecated
    boolean insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo, boolean notify);
    
    // ----------------------- config_aggr_info table insert update delete
    
    /**
     * Delete configuration information, physical deletion.
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param srcIp   remote ip
     * @param srcUser user
     */
    @Deprecated
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
    @Deprecated
    List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser);
    
    /**
     * Delete configuration information, physical deletion.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     */
    @Deprecated
    void removeConfigInfo4Beta(final String dataId, final String group, final String tenant);
    
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
    @Deprecated
    boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
            String appName, final String content);
    
    /**
     * Delete a single piece of data before aggregation.
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param datumId datum id
     */
    @Deprecated
    void removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant, final String datumId);
    
    /**
     * Delete all pre-aggregation data under a dataId.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     */
    @Deprecated
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
    @Deprecated
    boolean batchRemoveAggr(final String dataId, final String group, final String tenant, final List<String> datumList);
    
    /**
     * Delete data before startTime.
     *
     * @param startTime start time
     * @param limitSize limit size
     */
    @Deprecated
    void removeConfigHistory(final Timestamp startTime, final int limitSize);
    
    /**
     * Get the number of configurations before the specified time.
     *
     * @param startTime start time
     * @return count of history config that meet the conditions
     */
    @Deprecated
    int findConfigHistoryCountByTime(final Timestamp startTime);
    
    /**
     * Get the maxId.
     *
     * @return config max id
     */
    @Deprecated
    long findConfigMaxId();
    
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
    @Deprecated
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
    @Deprecated
    boolean replaceAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName);
    
    /**
     * Find all dataId and group. It is guaranteed not to return NULL.
     *
     * @return {@link com.alibaba.nacos.config.server.Config} list
     */
    @Deprecated
    List<ConfigInfo> findAllDataIdAndGroup();
    
    /**
     * Query beta configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return {@link ConfigInfo4Beta}
     */
    @Deprecated
    ConfigInfoBetaWrapper findConfigInfo4Beta(final String dataId, final String group, final String tenant);
    
    /**
     * Query tag configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @param tag    tag
     * @return {@link ConfigInfo4Tag}
     */
    @Deprecated
    ConfigInfoTagWrapper findConfigInfo4Tag(final String dataId, final String group, final String tenant,
            final String tag);
    
    /**
     * Query configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @return {@link ConfigInfoBase}
     */
    @Deprecated
    ConfigInfoBase findConfigInfoBase(final String dataId, final String group);
    
    /**
     * Query configuration information by primary key ID.
     *
     * @param id id
     * @return {@link ConfigInfo}
     */
    @Deprecated
    ConfigInfo findConfigInfo(long id);
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return config info
     */
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
    Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo, final int pageSize, final String group);
    
    /**
     * Returns the number of configuration items.
     *
     * @return number of configuration items.
     */
    @Deprecated
    int configInfoCount();
    
    /**
     * Returns the number of configuration items.
     *
     * @param tenant tenant
     * @return number of configuration items.
     */
    @Deprecated
    int configInfoCount(String tenant);
    
    /**
     * Returns the number of beta configuration items.
     *
     * @return number of configuration items..
     */
    @Deprecated
    int configInfoBetaCount();
    
    /**
     * Returns the number of beta configuration items.
     *
     * @return number of configuration items..
     */
    @Deprecated
    int configInfoTagCount();
    
    /**
     * get tenant id list  by page.
     *
     * @param page     page number
     * @param pageSize page size
     * @return tenant id list
     */
    @Deprecated
    List<String> getTenantIdList(int page, int pageSize);
    
    /**
     * get group id list  by page.
     *
     * @param page     page number
     * @param pageSize page size
     * @return group id list
     */
    @Deprecated
    List<String> getGroupIdList(int page, int pageSize);
    
    /**
     * Get count of aggregation config info.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return count
     */
    @Deprecated
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
    @Deprecated
    int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds, boolean isIn);
    
    /**
     * Query all configuration information by page.
     *
     * @param pageNo   Page number (starting at 1)
     * @param pageSize Page size (must be greater than 0)
     * @param tenant   tenant
     * @return {@link Page} with {@link ConfigInfo} generation
     */
    @Deprecated
    Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize, final String tenant);
    
    /**
     * Query all configuration information by page.
     *
     * @param pageNo   Page number (starting at 1)
     * @param pageSize Page size (must be greater than 0)
     * @param tenant   tenant
     * @return {@link Page} with {@link ConfigKey} generation
     */
    @Deprecated
    Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant);
    
    /**
     * Query all configuration information by page.
     *
     * @param pageNo   Page number (starting at 1)
     * @param pageSize Page size (must be greater than 0)
     * @return {@link Page} with {@link ConfigInfoBase} generation
     */
    @Deprecated
    Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo, final int pageSize);
    
    /**
     * Query all config info.
     *
     * @param lastMaxId last max id
     * @param pageSize  page size
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    @Deprecated
    Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize);
    
    /**
     * Query all beta config info for dump task.
     *
     * @param pageNo   page number
     * @param pageSize page size
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    @Deprecated
    Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo, final int pageSize);
    
    /**
     * Query all tag config info for dump task.
     *
     * @param pageNo   page numbser
     * @param pageSize page sizxe
     * @return {@link Page} with {@link ConfigInfoWrapper} generation
     */
    @Deprecated
    Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo, final int pageSize);
    
    /**
     * Fuzzy query configuration information based on dataId and group.
     *
     * @param pageNo     Page number (must be greater than 0)
     * @param pageSize   Page size (must be greater than 0)
     * @param configKeys Query configuration list
     * @param blacklist  Whether to blacklist
     * @return {@link Page} with {@link ConfigInfo} generation
     */
    @Deprecated
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
    @Deprecated
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
    @Deprecated
    Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo, final int pageSize, final String dataId,
            final String group, final String content) throws IOException;
    
    /**
     * Find a single piece of data before aggregation.
     *
     * @param dataId  data id
     * @param group   group
     * @param tenant  tenant
     * @param datumId datum id
     * @return {@link ConfigInfoAggr}
     */
    @Deprecated
    ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId);
    
    /**
     * Find all data before aggregation under a dataId. It is guaranteed not to return NULL.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return {@link ConfigInfoAggr} list
     */
    @Deprecated
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
    @Deprecated
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
    @Deprecated
    Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
            boolean blacklist);
    
    /**
     * Find all aggregated data sets.
     *
     * @return {@link ConfigInfoChanged} list
     */
    @Deprecated
    List<ConfigInfoChanged> findAllAggrGroup();
    
    /**
     * Find datumId by datum content.
     *
     * @param dataId  data id
     * @param groupId group
     * @param content content
     * @return datum keys
     */
    @Deprecated
    List<String> findDatumIdByContent(String dataId, String groupId, String content);
    
    /**
     * Query change config.
     *
     * @param startTime start time
     * @param endTime   end time
     * @return {@link ConfigInfoWrapper} list
     */
    @Deprecated
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
    @Deprecated
    Page<ConfigInfoWrapper> findChangeConfig(final String dataId, final String group, final String tenant,
            final String appName, final Timestamp startTime, final Timestamp endTime, final int pageNo,
            final int pageSize, final long lastMaxId);
    
    /**
     * Query deleted config.
     *
     * @param startTime start time
     * @param endTime   end time
     * @return {@link ConfigInfo} list
     */
    @Deprecated
    List<ConfigInfo> findDeletedConfig(final Timestamp startTime, final Timestamp endTime);
    
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
    void addConfigTagsRelation(long configId, String configTags, String dataId, String group, String tenant);
    
    /**
     * Delete tag.
     *
     * @param id id
     */
    @Deprecated
    void removeTagByIdAtomic(long id);
    
    /**
     * Query tag list.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return tag list
     */
    @Deprecated
    List<String> selectTagByConfig(String dataId, String group, String tenant);
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId  dataId
     * @param group   group
     * @param tenant  tenant
     * @param srcIp   ip
     * @param srcUser user
     */
    @Deprecated
    void removeConfigInfoAtomic(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser);
    
    /**
     * Remove configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param ids ids
     */
    @Deprecated
    void removeConfigInfoByIdsAtomic(final String ids);
    
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
    @Deprecated
    void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
            final String srcIp, final String srcUser);
    
    /**
     * Update configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param configInfo        config info
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param time              time
     * @param configAdvanceInfo advance info
     */
    @Deprecated
    void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, Map<String, Object> configAdvanceInfo);
    
    
    /**
     * find ConfigInfo by ids.
     *
     * @param ids id list
     * @return {@link com.alibaba.nacos.config.server.model.ConfigInfo} list
     * @author klw
     * @date 2019/7/5 16:37
     */
    @Deprecated
    List<ConfigInfo> findConfigInfosByIds(final String ids);
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return advance info
     */
    @Deprecated
    ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant);
    
    /**
     * Query configuration information; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return advance info
     */
    @Deprecated
    ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant);
    
    /**
     * Update change records; database atomic operations, minimal sql actions, no business encapsulation.
     *
     * @param id         id
     * @param configInfo config info
     * @param srcIp      ip
     * @param srcUser    user
     * @param time       time
     * @param ops        ops type
     */
    @Deprecated
    void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser, final Timestamp time,
            String ops);
    
    /**
     * List configuration history change record.
     *
     * @param dataId   data Id
     * @param group    group
     * @param tenant   tenant
     * @param pageNo   no
     * @param pageSize size
     * @return {@link Page} with {@link ConfigHistoryInfo} generation
     */
    @Deprecated
    Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo, int pageSize);
    
    /**
     * Get history config detail.
     *
     * @param nid nid
     * @return {@link ConfigHistoryInfo}
     */
    @Deprecated
    ConfigHistoryInfo detailConfigHistory(Long nid);
    
    /**
     * Get previous config detail.
     *
     * @param id id
     * @return {@link ConfigHistoryInfo}
     */
    @Deprecated
    ConfigHistoryInfo detailPreviousConfigHistory(Long id);
    
    
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
    @Deprecated
    void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc, String createResoure,
            final long time);
    
    /**
     * Update tenantInfo showname.
     *
     * @param kp         kp
     * @param tenantId   tenant Id
     * @param tenantName tenant name
     * @param tenantDesc tenant description
     */
    @Deprecated
    void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc);
    
    /**
     * Query tenant info.
     *
     * @param kp kp
     * @return {@link TenantInfo} list
     */
    @Deprecated
    List<TenantInfo> findTenantByKp(String kp);
    
    /**
     * Query tenant info.
     *
     * @param kp       kp
     * @param tenantId tenant id
     * @return {@link TenantInfo}
     */
    @Deprecated
    TenantInfo findTenantByKp(String kp, String tenantId);
    
    /**
     * Remote tenant info.
     *
     * @param kp       kp
     * @param tenantId tenant id
     */
    @Deprecated
    void removeTenantInfoAtomic(final String kp, final String tenantId);
    
    /**
     * Convert delete config.
     *
     * @param list origin data
     * @return {@link ConfigInfo} list
     */
    @Deprecated
    List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list);
    
    /**
     * Convert change config.
     *
     * @param list origin data
     * @return {@link ConfigInfoWrapper} list
     */
    @Deprecated
    List<ConfigInfoWrapper> convertChangeConfig(List<Map<String, Object>> list);
    
    /**
     * Get the Md5 value of all configurations, through the paging method.
     *
     * @return {@link ConfigInfoWrapper} list
     */
    @Deprecated
    List<ConfigInfoWrapper> listAllGroupKeyMd5();
    
    /**
     * list group key md5 by page.
     *
     * @param pageNo   page no
     * @param pageSize page size
     * @return {@link ConfigInfoWrapper} list
     */
    @Deprecated
    List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize);
    
    /**
     * Generate fuzzy search Sql.
     *
     * @param s origin string
     * @return fuzzy search Sql
     */
    @Deprecated
    String generateLikeArgument(String s);
    
    /**
     * Query config info.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return {@link ConfigInfoWrapper}
     */
    @Deprecated
    ConfigInfoWrapper queryConfigInfo(final String dataId, final String group, final String tenant);
    
    /**
     * Determine whether the table exists.
     *
     * @param tableName table name
     * @return {@code true} if table exist
     */
    @Deprecated
    boolean isExistTable(String tableName);
    
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
    @Deprecated
    List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
            final String appName, final List<Long> ids);
    
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
    @Deprecated
    Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
            Map<String, Object> configAdvanceInfo, Timestamp time, boolean notify, SameConfigPolicy policy)
            throws NacosException;
    
    /**
     * query tenantInfo (namespace) existence based by tenantId.
     *
     * @param tenantId tenant Id
     * @return count by tenantId
     */
    @Deprecated
    int tenantInfoCountByTenantId(String tenantId);
    
    /**
     * Query dataId list by namespace.
     *
     * @param tenantId tenantId
     * @return {@link ConfigInfoBase}
     */
    @Deprecated
    List<ConfigInfoWrapper> queryConfigInfoByNamespace(final String tenantId);
}