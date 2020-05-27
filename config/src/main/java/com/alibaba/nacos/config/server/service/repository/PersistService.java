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
import com.alibaba.nacos.config.server.model.SubInfo;
import com.alibaba.nacos.config.server.model.TenantInfo;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 数据库服务，提供ConfigInfo在数据库的存取<br> 3.0开始增加数据版本号, 并将物理删除改为逻辑删除<br> 3.0增加数据库切换功能
 *
 * @author boyan
 * @author leiwen.zh
 * @since 1.0
 */
@SuppressWarnings("all")
public interface PersistService {

	/**
	 * @author klw
	 * @Description: constant variables
	 */
	String SPOT = ".";
	Object[] EMPTY_ARRAY = new Object[]{};
	String SQL_FIND_ALL_CONFIG_INFO = "select id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,c_schema from config_info";
	String SQL_TENANT_INFO_COUNT_BY_TENANT_ID = "select count(1) from tenant_info where tenant_id = ?";
	String SQL_FIND_CONFIG_INFO_BY_IDS = "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE ";
	String SQL_DELETE_CONFIG_INFO_BY_IDS = "DELETE FROM config_info WHERE ";
	int QUERY_LIMIT_SIZE = 50;
	String PATTERN_STR = "*";

	<E> PaginationHelper<E> createPaginationHelper();

	/**
	 * 添加普通配置信息，发布数据变更事件
	 */
	void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
			final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify);

	/**
	 * 添加普通配置信息，发布数据变更事件
	 */
	void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps,
			String srcIp, String srcUser, Timestamp time, boolean notify);

	/**
	 * 添加普通配置信息，发布数据变更事件
	 */
	void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
			boolean notify);

	/**
	 * 更新配置信息
	 */
	void updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
			final Timestamp time, final Map<String, Object> configAdvanceInfo,
			final boolean notify);

	/**
	 * 更新配置信息
	 */
	void updateConfigInfo4Beta(ConfigInfo configInfo,
			String betaIps, String srcIp, String srcUser, Timestamp time,
			boolean notify);

	/**
	 * 更新配置信息
	 */
	void updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
			boolean notify);

	void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
			final String srcUser, final Timestamp time, final boolean notify);

	void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
			final String srcUser, final Timestamp time, final boolean notify);

	/**
	 * 更新md5
	 */
	void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime);

	void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
			Map<String, Object> configAdvanceInfo);

	/**
	 * 写入主表，插入或更新
	 */
	void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
			Map<String, Object> configAdvanceInfo, boolean notify);

	// ----------------------- config_aggr_info 表 insert update delete

	/**
	 * 写入主表，插入或更新
	 */
	void insertOrUpdateSub(SubInfo subInfo);

	/**
	 * 删除配置信息, 物理删除
	 */
	void removeConfigInfo(final String dataId, final String group, final String tenant, final String srcIp,
			final String srcUser);

	/**
	 * @return List<ConfigInfo> deleted configInfos
	 * @author klw
	 * @Description: delete config info by ids
	 * @Date 2019/7/5 16:45
	 * @Param [ids, srcIp, srcUser]
	 */
	List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser);

	/**
	 * 删除beta配置信息, 物理删除
	 */
	void removeConfigInfo4Beta(final String dataId, final String group, final String tenant);

	/**
	 * 增加聚合前数据到数据库, select -> update or insert
	 */
	boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
			String appName, final String content);

	/**
	 * 删除单条聚合前数据
	 */
	void removeSingleAggrConfigInfo(final String dataId,
			final String group, final String tenant, final String datumId);

	/**
	 * 删除一个dataId下面所有的聚合前数据
	 */
	void removeAggrConfigInfo(final String dataId, final String group, final String tenant);

	/**
	 * 批量删除聚合数据，需要指定datum的列表
	 *
	 * @param dataId dataId
	 * @param group group
	 * @param datumList datumList
	 */
	boolean batchRemoveAggr(final String dataId, final String group, final String tenant,
			final List<String> datumList);

	/**
	 * 删除startTime前的数据
	 */
	void removeConfigHistory(final Timestamp startTime, final int limitSize);

	/**
	 * 获取指定时间前配置条数
	 */
	int findConfigHistoryCountByTime(final Timestamp startTime);

	/**
	 * 获取最大maxId
	 */
	long findConfigMaxId();

	/**
	 * 批量添加或者更新数据.事务过程中出现任何异常都会强制抛出TransactionSystemException
	 *
	 * @param dataId dataId
	 * @param group group
	 * @param datumMap datumMap
	 * @return
	 */
	boolean batchPublishAggr(final String dataId, final String group, final String tenant,
			final Map<String, String> datumMap, final String appName);

	/**
	 * 批量替换，先全部删除聚合表中指定DataID+Group的数据，再插入数据. 事务过程中出现任何异常都会强制抛出TransactionSystemException
	 *
	 * @param dataId dataId
	 * @param group group
	 * @param datumMap datumMap
	 * @return
	 */
	boolean replaceAggr(final String dataId, final String group, final String tenant,
			final Map<String, String> datumMap, final String appName);

	/**
	 * 查找所有的dataId和group。保证不返回NULL。
	 */
	@Deprecated
	List<ConfigInfo> findAllDataIdAndGroup();

	/**
	 * 根据dataId和group查询配置信息
	 */
	ConfigInfo4Beta findConfigInfo4Beta(final String dataId, final String group, final String tenant);

	/**
	 * 根据dataId和group查询配置信息
	 */
	ConfigInfo4Tag findConfigInfo4Tag(final String dataId, final String group, final String tenant,
			final String tag);

	/**
	 * 根据dataId和group查询配置信息
	 */
	ConfigInfo findConfigInfoApp(final String dataId, final String group, final String tenant,
			final String appName);

	/**
	 * 根据dataId和group查询配置信息
	 */
	ConfigInfo findConfigInfoAdvanceInfo(final String dataId, final String group, final String tenant,
			final Map<String, Object> configAdvanceInfo);

	/**
	 * 根据dataId和group查询配置信息
	 */
	ConfigInfoBase findConfigInfoBase(final String dataId, final String group);

	/**
	 * 根据数据库主键ID查询配置信息
	 *
	 * @param id id
	 * @return {@link ConfigInfo}
	 */
	ConfigInfo findConfigInfo(long id);

	/**
	 * 根据dataId查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param dataId
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findConfigInfoByDataId(final int pageNo, final int pageSize, final String dataId,
			final String tenant);

	/**
	 * 根据dataId查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param dataId
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findConfigInfoByDataIdAndApp(final int pageNo, final int pageSize, final String dataId,
			final String tenant, final String appName);

	Page<ConfigInfo> findConfigInfoByDataIdAndAdvance(final int pageNo, final int pageSize, final String dataId,
			final String tenant,
			final Map<String, Object> configAdvanceInfo);

	Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
			final String group,
			final String tenant, final Map<String, Object> configAdvanceInfo);

	/**
	 * 根据dataId查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param dataId
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfoBase> findConfigInfoBaseByDataId(final int pageNo,
			final int pageSize, final String dataId);

	/**
	 * 根据group查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param group
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findConfigInfoByGroup(final int pageNo, final int pageSize, final String group,
			final String tenant);

	/**
	 * 根据group查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param group
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findConfigInfoByGroupAndApp(final int pageNo,
			final int pageSize, final String group, final String tenant,
			final String appName);

	Page<ConfigInfo> findConfigInfoByGroupAndAdvance(final int pageNo,
			final int pageSize, final String group, final String tenant,
			final Map<String, Object> configAdvanceInfo);

	/**
	 * 根据group查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findConfigInfoByApp(final int pageNo,
			final int pageSize, final String tenant, final String appName);

	Page<ConfigInfo> findConfigInfoByAdvance(final int pageNo,
			final int pageSize, final String tenant,
			final Map<String, Object> configAdvanceInfo);

	/**
	 * 根据group查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param group
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo,
			final int pageSize, final String group);

	/**
	 * 返回配置项个数
	 */
	int configInfoCount();

	/**
	 * 返回配置项个数
	 */
	int configInfoCount(String tenant);

	/**
	 * 返回beta配置项个数
	 */
	int configInfoBetaCount();

	/**
	 * 返回beta配置项个数
	 */
	int configInfoTagCount();

	List<String> getTenantIdList(int page, int pageSize);

	List<String> getGroupIdList(int page, int pageSize);

	int aggrConfigInfoCount(String dataId, String group, String tenant);

	int aggrConfigInfoCountIn(String dataId, String group, String tenant, List<String> datumIds);

	int aggrConfigInfoCountNotIn(String dataId, String group, String tenant, List<String> datumIds);

	int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds,
			boolean isIn);

	/**
	 * 分页查询所有的配置信息
	 *
	 * @param pageNo   页码(从1开始)
	 * @param pageSize 每页大小(必须大于0)
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize, final String tenant);

	/**
	 * 分页查询所有的配置信息
	 *
	 * @param pageNo   页码(从1开始)
	 * @param pageSize 每页大小(必须大于0)
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant);

	/**
	 * 分页查询所有的配置信息
	 *
	 * @param pageNo   页码(从1开始)
	 * @param pageSize 每页大小(必须大于0)
	 * @return ConfigInfo对象的集合
	 */
	@Deprecated
	Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo,
			final int pageSize);

	Page<ConfigInfoWrapper> findAllConfigInfoForDumpAll(
			final int pageNo, final int pageSize);

	Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize);

	Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(
			final int pageNo, final int pageSize);

	Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(
			final int pageNo, final int pageSize);

	/**
	 * 通过select in方式实现db记录的批量查询； subQueryLimit指定in中条件的个数，上限20
	 */
	List<ConfigInfo> findConfigInfoByBatch(final List<String> dataIds,
			final String group, final String tenant, int subQueryLimit);

	/**
	 * 根据dataId和group模糊查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param dataId   支持模糊查询
	 * @param group    支持模糊查询
	 * @param tenant   支持模糊查询
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final String dataId,
			final String group, final String tenant, final String appName,
			final String content);

	Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
			final String group, final String tenant,
			final Map<String, Object> configAdvanceInfo);

	/**
	 * 根据dataId和group模糊查询配置信息
	 *
	 * @param pageNo     页码(必须大于0)
	 * @param pageSize   每页大小(必须大于0)
	 * @param configKeys 查询配置列表
	 * @param blacklist  是否黑名单
	 * @return ConfigInfo对象的集合
	 */
	Page<ConfigInfo> findConfigInfoLike(final int pageNo,
			final int pageSize, final ConfigKey[] configKeys,
			final boolean blacklist);

	/**
	 * 根据dataId和group模糊查询配置信息
	 *
	 * @param pageNo   页码(必须大于0)
	 * @param pageSize 每页大小(必须大于0)
	 * @param dataId
	 * @param group
	 * @return ConfigInfo对象的集合
	 * @throws IOException
	 */
	Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo,
			final int pageSize, final String dataId, final String group,
			final String content) throws IOException;

	/**
	 * 查找聚合前的单条数据
	 *
	 * @param dataId
	 * @param group
	 * @param datumId
	 * @return {@link ConfigInfoAggr}
	 */
	ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId);

	/**
	 * 查找一个dataId下面的所有聚合前的数据. 保证不返回NULL.
	 */
	List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant);

	Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
			final int pageSize);

	/**
	 * 查询符合条件的聚合数据
	 *
	 * @param pageNo     pageNo
	 * @param pageSize   pageSize
	 * @param configKeys 聚合数据条件
	 * @param blacklist  黑名单
	 * @return {@link Page<ConfigInfoAggr>}
	 */
	Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
			boolean blacklist);

	/**
	 * 找到所有聚合数据组。
	 */
	List<ConfigInfoChanged> findAllAggrGroup();

	/**
	 * 由datum内容查找datumId
	 *
	 * @param dataId  data id
	 * @param groupId group
	 * @param content content
	 * @return datum keys
	 */
	List<String> findDatumIdByContent(String dataId, String groupId,
			String content);

	List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime,
			final Timestamp endTime);

	/**
	 * 根据时间段和配置条件查询符合条件的配置
	 *
	 * @param dataId    dataId 支持模糊
	 * @param group     dataId 支持模糊
	 * @param appName   产品名
	 * @param startTime 起始时间
	 * @param endTime   截止时间
	 * @param pageNo    pageNo
	 * @param pageSize  pageSize
	 * @return {@link Page<ConfigInfoWrapper>}
	 */
	Page<ConfigInfoWrapper> findChangeConfig(final String dataId, final String group, final String tenant,
			final String appName, final Timestamp startTime,
			final Timestamp endTime, final int pageNo,
			final int pageSize, final long lastMaxId);

	List<ConfigInfo> findDeletedConfig(final Timestamp startTime,
			final Timestamp endTime);

	/**
	 * 增加配置；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param srcIp             ip
	 * @param srcUser           user
	 * @param configInfo        info
	 * @param time              time
	 * @param configAdvanceInfo advance info
	 * @return excute sql result
	 */
	long addConfigInfoAtomic(final long id, final String srcIp, final String srcUser, final ConfigInfo configInfo,
			final Timestamp time,
			Map<String, Object> configAdvanceInfo);

	/**
	 * 增加配置；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param configId id
	 * @param tagName  tag
	 * @param dataId   data id
	 * @param group    group
	 * @param tenant   tenant
	 */
	void addConfigTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant);

	/**
	 * 增加配置；数据库原子操作
	 *
	 * @param configId   config id
	 * @param configTags tags
	 * @param dataId     dataId
	 * @param group      group
	 * @param tenant     tenant
	 */
	void addConfigTagsRelation(long configId, String configTags, String dataId, String group,
			String tenant);

	void removeTagByIdAtomic(long id);

	List<String> getConfigTagsByTenant(String tenant);

	List<String> selectTagByConfig(String dataId, String group, String tenant);

	/**
	 * 删除配置；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param dataId  dataId
	 * @param group   group
	 * @param tenant  tenant
	 * @param srcIp   ip
	 * @param srcUser user
	 */
	void removeConfigInfoAtomic(final String dataId, final String group, final String tenant,
			final String srcIp,
			final String srcUser);

	/**
	 * @return void
	 * @author klw
	 * @Description: Delete configuration; database atomic operation, minimum SQL action, no business encapsulation
	 * @Date 2019/7/5 16:39
	 * @Param [id]
	 */
	void removeConfigInfoByIdsAtomic(final String ids);

	/**
	 * 删除配置；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param dataId  dataId
	 * @param group   group
	 * @param tenant  tenant
	 * @param tag     tag
	 * @param srcIp   ip
	 * @param srcUser user
	 */
	void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
			final String srcIp,
			final String srcUser);

	/**
	 * 更新配置;数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param configInfo        config info
	 * @param srcIp             ip
	 * @param srcUser           user
	 * @param time              time
	 * @param configAdvanceInfo advance info
	 */
	void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
			final Timestamp time, Map<String, Object> configAdvanceInfo);

	/**
	 * 查询配置信息；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param dataId dataId
	 * @param group  group
	 * @param tenant tenant
	 * @return config info
	 */
	ConfigInfo findConfigInfo(final String dataId, final String group, final String tenant);

	/**
	 * @return {@link java.util.List<com.alibaba.nacos.config.server.model.ConfigInfo>}
	 * @author klw
	 * @Description: find ConfigInfo by ids
	 * @Date 2019/7/5 16:37
	 * @Param [ids]
	 */
	List<ConfigInfo> findConfigInfosByIds(final String ids);

	/**
	 * 查询配置信息；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param dataId dataId
	 * @param group  group
	 * @param tenant tenant
	 * @return advance info
	 */
	ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant);

	/**
	 * 查询配置信息；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param dataId dataId
	 * @param group  group
	 * @param tenant tenant
	 * @return advance info
	 */
	ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant);

	/**
	 * 更新变更记录；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param configHistoryId         id
	 * @param configInfo config info
	 * @param srcIp      ip
	 * @param srcUser    user
	 * @param time       time
	 * @param ops        ops type
	 */
	void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser,
			final Timestamp time, String ops);

	/**
	 * list配置的历史变更记录
	 *
	 * @param dataId   data Id
	 * @param group    group
	 * @param tenant   tenant
	 * @param pageNo   no
	 * @param pageSize size
	 * @return history info
	 */
	Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
			int pageSize);

	/**
	 * 增加配置；数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param dataId  dataId
	 * @param group   group
	 * @param appName appName
	 * @param date    date
	 */
	void addConfigSubAtomic(final String dataId, final String group, final String appName,
			final Timestamp date);

	/**
	 * 更新配置;数据库原子操作，最小sql动作，无业务封装
	 *
	 * @param dataId  data Id
	 * @param group   group
	 * @param appName app name
	 * @param time    time
	 */
	void updateConfigSubAtomic(final String dataId, final String group, final String appName,
			final Timestamp time);

	ConfigHistoryInfo detailConfigHistory(Long nid);

	/**
	 * insert tenant info
	 *
	 * @param kp         kp
	 * @param tenantId   tenant Id
	 * @param tenantName tenant name
	 * @param tenantDesc tenant description
	 * @param time       time
	 */
	void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
			String createResoure, final long time);

	/**
	 * Update tenantInfo showname
	 *
	 * @param kp         kp
	 * @param tenantId   tenant Id
	 * @param tenantName tenant name
	 * @param tenantDesc tenant description
	 */
	void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc);

	List<TenantInfo> findTenantByKp(String kp);

	TenantInfo findTenantByKp(String kp, String tenantId);

	void removeTenantInfoAtomic(final String kp, final String tenantId);

	List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list);

	List<ConfigInfoWrapper> convertChangeConfig(List<Map<String, Object>> list);

	/**
	 * 获取所有的配置的Md5值，通过分页方式获取。
	 *
	 * @return {@link List<ConfigInfoWrapper>}
	 */
	List<ConfigInfoWrapper> listAllGroupKeyMd5();

	List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize);

	String generateLikeArgument(String s);

	ConfigInfoWrapper queryConfigInfo(final String dataId, final String group, final String tenant);

	boolean isExistTable(String tableName);

	Boolean completeMd5();

	/**
	 * query all configuration information according to group, appName, tenant (for export)
	 *
	 * @param group
	 * @return Collection of ConfigInfo objects
	 */
	List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
			final String appName, final List<Long> ids);

	/**
	 * batch operation,insert or update
	 * the format of the returned:
	 * succCount: number of successful imports
	 * skipCount: number of import skips (only with skip for the same configs)
	 * failData: import failed data (only with abort for the same configs)
	 * skipData: data skipped at import  (only with skip for the same configs)
	 */
	Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
			Map<String, Object> configAdvanceInfo, Timestamp time, boolean notify, SameConfigPolicy policy) throws
			NacosException;

	/**
	 * query tenantInfo (namespace) existence based by tenantId
	 *
	 * @param tenantId
	 * @return count by tenantId
	 */
	int tenantInfoCountByTenantId(String tenantId);
}