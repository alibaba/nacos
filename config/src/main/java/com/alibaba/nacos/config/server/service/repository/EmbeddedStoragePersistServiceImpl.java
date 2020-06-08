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
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
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
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_ADVANCE_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_ALL_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO4BETA_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO4TAG_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_AGGR_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_BASE_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_CHANGED_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_KEY_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.HISTORY_DETAIL_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.HISTORY_LIST_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.TENANT_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;

/**
 * For Apache Derby
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.MethodReturnWrapperTypeRule")
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedStoragePersistServiceImpl implements PersistService {

	private static final String RESOURCE_CONFIG_INFO_ID = "config-info-id";
	private static final String RESOURCE_CONFIG_HISTORY_ID = "config-history-id";
	private static final String RESOURCE_CONFIG_TAG_RELATION_ID = "config-tag-relation-id";
	private static final String RESOURCE_APP_CONFIGDATA_RELATION_SUBS = "app-configdata-relation-subs";
	private static final String RESOURCE_CONFIG_BETA_ID = "config-beta-id";
	private static final String RESOURCE_NAMESPACE_ID = "namespace-id";
	private static final String RESOURCE_USER_ID = "user-id";
	private static final String RESOURCE_ROLE_ID = "role-id";
	private static final String RESOURCE_PERMISSIONS_ID = "permissions_id";

	private DataSourceService dataSourceService;

	private final DatabaseOperate databaseOperate;

	private final IdGeneratorManager idGeneratorManager;

	/**
	 * The constructor sets the dependency injection order
	 *
	 * @param databaseOperate    {@link EmbeddedStoragePersistServiceImpl}
	 * @param idGeneratorManager {@link IdGeneratorManager}
	 */
	public EmbeddedStoragePersistServiceImpl(DatabaseOperate databaseOperate,
			IdGeneratorManager idGeneratorManager) {
		this.databaseOperate = databaseOperate;
		this.idGeneratorManager = idGeneratorManager;
	}

	@PostConstruct
	public void init() {
		dataSourceService = DynamicDataSource.getInstance().getDataSource();
		idGeneratorManager.register(RESOURCE_CONFIG_INFO_ID, RESOURCE_CONFIG_HISTORY_ID,
				RESOURCE_CONFIG_TAG_RELATION_ID, RESOURCE_APP_CONFIGDATA_RELATION_SUBS,
				RESOURCE_CONFIG_BETA_ID, RESOURCE_NAMESPACE_ID, RESOURCE_USER_ID,
				RESOURCE_ROLE_ID, RESOURCE_PERMISSIONS_ID);
	}

	public boolean checkMasterWritable() {
		return dataSourceService.checkMasterWritable();
	}

	public void setBasicDataSourceService(DataSourceService dataSourceService) {
		this.dataSourceService = dataSourceService;
	}

	public synchronized void reload() throws IOException {
		this.dataSourceService.reload();
	}

	// ----------------------- config_info 表 insert update delete

	/**
	 * 单元测试用
	 */
	public JdbcTemplate getJdbcTemplate() {
		return this.dataSourceService.getJdbcTemplate();
	}

	public TransactionTemplate getTransactionTemplate() {
		return this.dataSourceService.getTransactionTemplate();
	}

	public String getCurrentDBUrl() {
		return this.dataSourceService.getCurrentDBUrl();
	}

	public DatabaseOperate getDatabaseOperate() {
		return databaseOperate;
	}

	@Override
	public <E> PaginationHelper<E> createPaginationHelper() {
		return new EmbeddedPaginationHelperImpl<E>(databaseOperate);
	}

	public void addConfigInfo(final String srcIp,
			final String srcUser, final ConfigInfo configInfo, final Timestamp time,
			final Map<String, Object> configAdvanceInfo, final boolean notify) {

		try {
			final String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
					StringUtils.EMPTY :
					configInfo.getTenant();
			configInfo.setTenant(tenantTmp);

			long configId = idGeneratorManager.nextId(RESOURCE_CONFIG_INFO_ID);
			long hisId = idGeneratorManager.nextId(RESOURCE_CONFIG_HISTORY_ID);

			addConfigInfoAtomic(configId, srcIp, srcUser, configInfo, time,
					configAdvanceInfo);
			String configTags = configAdvanceInfo == null ?
					null :
					(String) configAdvanceInfo.get("config_tags");

			addConfigTagsRelation(configId, configTags, configInfo.getDataId(),
					configInfo.getGroup(), configInfo.getTenant());
			insertConfigHistoryAtomic(hisId, configInfo, srcIp, srcUser, time, "I");
			EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, time);
			databaseOperate.blockUpdate();
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void addConfigInfo4Beta(ConfigInfo configInfo,
			String betaIps, String srcIp, String srcUser, Timestamp time,
			boolean notify) {
		String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ?
				StringUtils.EMPTY :
				configInfo.getAppName();
		String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
				StringUtils.EMPTY :
				configInfo.getTenant();

		configInfo.setTenant(tenantTmp);
		try {
			String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);

			final String sql =
					"INSERT INTO config_info_beta(data_id,group_id,tenant_id,app_name,content,md5,beta_ips,src_ip,"
							+ "src_user,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
			final Object[] args = new Object[] { configInfo.getDataId(),
					configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(),
					md5, betaIps, srcIp, srcUser, time, time };

			EmbeddedStorageContextUtils
					.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
			EmbeddedStorageContextUtils.addSqlContext(sql, args);

			databaseOperate.blockUpdate();
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void addConfigInfo4Tag(ConfigInfo configInfo, String tag,
			String srcIp, String srcUser, Timestamp time, boolean notify) {
		String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ?
				StringUtils.EMPTY :
				configInfo.getAppName();
		String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
				StringUtils.EMPTY :
				configInfo.getTenant();
		String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();

		configInfo.setTenant(tenantTmp);

		try {
			String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);

			final String sql =
					"INSERT INTO config_info_tag(data_id,group_id,tenant_id,tag_id,app_name,content,md5,src_ip,src_user,"
							+ "gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
			final Object[] args = new Object[] { configInfo.getDataId(),
					configInfo.getGroup(), tenantTmp, tagTmp, appNameTmp,
					configInfo.getContent(), md5, srcIp, srcUser, time, time };

			EmbeddedStorageContextUtils
					.onModifyConfigTagInfo(configInfo, tagTmp, srcIp, time);
			EmbeddedStorageContextUtils.addSqlContext(sql, args);

			databaseOperate.blockUpdate();
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void updateConfigInfo(final ConfigInfo configInfo,
			final String srcIp, final String srcUser, final Timestamp time,
			final Map<String, Object> configAdvanceInfo, final boolean notify) {
		try {
			ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(),
					configInfo.getGroup(), configInfo.getTenant());

			final String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
					StringUtils.EMPTY :
					configInfo.getTenant();

			oldConfigInfo.setTenant(tenantTmp);

			String appNameTmp = oldConfigInfo.getAppName();
			// If the appName passed by the user is not empty, the appName of the user is persisted;
			// otherwise, the appName of db is used. Empty string is required to clear appName
			if (configInfo.getAppName() == null) {
				configInfo.setAppName(appNameTmp);
			}

			updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);

			String configTags = configAdvanceInfo == null ?
					null :
					(String) configAdvanceInfo.get("config_tags");
			if (configTags != null) {
				// Delete all tags and recreate them
				removeTagByIdAtomic(oldConfigInfo.getId());
				addConfigTagsRelation(oldConfigInfo.getId(), configTags,
						configInfo.getDataId(), configInfo.getGroup(),
						configInfo.getTenant());
			}

			insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp,
					srcUser, time, "U");

			EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, time);
			databaseOperate.blockUpdate();
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	@Override
	public void updateConfigInfo4Beta(ConfigInfo configInfo,
			String betaIps, String srcIp, String srcUser, Timestamp time,
			boolean notify) {
		String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ?
				StringUtils.EMPTY :
				configInfo.getAppName();
		String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
				StringUtils.EMPTY :
				configInfo.getTenant();

		configInfo.setTenant(tenantTmp);
		try {
			String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);

			final String sql =
					"UPDATE config_info_beta SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
							+ "data_id=? AND group_id=? AND tenant_id=?";
			final Object[] args = new Object[] { configInfo.getContent(), md5, srcIp,
					srcUser, time, appNameTmp, configInfo.getDataId(),
					configInfo.getGroup(), tenantTmp };

			EmbeddedStorageContextUtils
					.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
			EmbeddedStorageContextUtils.addSqlContext(sql, args);

			databaseOperate.blockUpdate();
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void updateConfigInfo4Tag(ConfigInfo configInfo,
			String tag, String srcIp, String srcUser, Timestamp time, boolean notify) {
		String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ?
				StringUtils.EMPTY :
				configInfo.getAppName();
		String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
				StringUtils.EMPTY :
				configInfo.getTenant();
		String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();

		configInfo.setTenant(tenantTmp);

		try {
			String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);

			final String sql =
					"UPDATE config_info_tag SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
							+ "data_id=? AND group_id=? AND tenant_id=? AND tag_id=?";
			final Object[] args = new Object[] { configInfo.getContent(), md5, srcIp,
					srcUser, time, appNameTmp, configInfo.getDataId(),
					configInfo.getGroup(), tenantTmp, tagTmp };

			EmbeddedStorageContextUtils
					.onModifyConfigTagInfo(configInfo, tagTmp, srcIp, time);
			EmbeddedStorageContextUtils.addSqlContext(sql, args);

			databaseOperate.blockUpdate();
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps,
			final String srcIp, final String srcUser, final Timestamp time,
			final boolean notify) {
		if (findConfigInfo4Beta(configInfo.getDataId(), configInfo.getGroup(),
				configInfo.getTenant()) == null) {
			addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
		}
		else {
			updateConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
		}
		if (ApplicationUtils.getStandaloneMode()) {
			EventDispatcher.fireEvent(
					new ConfigDataChangeEvent(true, configInfo.getDataId(),
							configInfo.getGroup(), configInfo.getTenant(),
							time.getTime()));
		}
	}

	public void insertOrUpdateTag(final ConfigInfo configInfo, final String tag,
			final String srcIp, final String srcUser, final Timestamp time,
			final boolean notify) {
		if (findConfigInfo4Tag(configInfo.getDataId(), configInfo.getGroup(),
				configInfo.getTenant(), tag) == null) {
			addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
		}
		else {
			updateConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
		}
		if (ApplicationUtils.getStandaloneMode()) {
			EventDispatcher.fireEvent(
					new ConfigDataChangeEvent(false, configInfo.getDataId(),
							configInfo.getGroup(), configInfo.getTenant(), tag,
							time.getTime()));
		}
	}

	public void updateMd5(String dataId, String group, String tenant, String md5,
			Timestamp lastTime) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		try {

			final String sql = "UPDATE config_info SET md5 = ? WHERE data_id=? AND group_id=? AND tenant_id=? AND gmt_modified=?";
			final Object[] args = new Object[] { md5, dataId, group, tenantTmp,
					lastTime };

			EmbeddedStorageContextUtils.addSqlContext(sql, args);

			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException("Failed to config the MD5 modification");
			}
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo,
			Timestamp time, Map<String, Object> configAdvanceInfo) {
		insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, true);
	}

	public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo,
			Timestamp time, Map<String, Object> configAdvanceInfo, boolean notify) {
		if (Objects.isNull(findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
				configInfo.getTenant()))) {
			addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
		}
		else {
			updateConfigInfo(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
		}
		if (ApplicationUtils.getStandaloneMode()) {
			EventDispatcher.fireEvent(
					new ConfigDataChangeEvent(false, configInfo.getDataId(),
							configInfo.getGroup(), configInfo.getTenant(),
							time.getTime()));
		}
	}

	public void insertOrUpdateSub(SubInfo subInfo) {
		if (isAlreadyExist(subInfo)) {
			updateConfigSubAtomic(subInfo.getDataId(), subInfo.getGroup(),
					subInfo.getAppName(), subInfo.getDate());
		}
		else {
			addConfigSubAtomic(subInfo.getDataId(), subInfo.getGroup(),
					subInfo.getAppName(), subInfo.getDate());
		}
	}

	private boolean isAlreadyExist(SubInfo subInfo) {
		final String sql = "SELECT * from app_configdata_relation_subs WHERE dara_id=? and group_id=? and app_name=?";
		Map obj = databaseOperate.queryOne(sql,
				new Object[] { subInfo.getDataId(), subInfo.getGroup(),
						subInfo.getAppName() }, Map.class);
		return obj != null;
	}

	public void removeConfigInfo(final String dataId, final String group,
			final String tenant, final String srcIp, final String srcUser) {
		final Timestamp time = new Timestamp(System.currentTimeMillis());
		ConfigInfo configInfo = findConfigInfo(dataId, group, tenant);
		if (Objects.nonNull(configInfo)) {
			try {
				String tenantTmp = StringUtils.isBlank(tenant) ?
						StringUtils.EMPTY :
						tenant;

				removeConfigInfoAtomic(dataId, group, tenantTmp, srcIp, srcUser);
				removeTagByIdAtomic(configInfo.getId());
				insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser,
						time, "D");

				EmbeddedStorageContextUtils
						.onDeleteConfigInfo(tenantTmp, group, dataId, srcIp, time);

				boolean result = databaseOperate
						.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
				if (!result) {
					throw new NacosConfigException("config deletion failed");
				}
			}
			finally {
				EmbeddedStorageContextUtils.cleanAllContext();
			}
		}
	}

	public List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids,
			final String srcIp, final String srcUser) {
		if (CollectionUtils.isEmpty(ids)) {
			return null;
		}
		ids.removeAll(Collections.singleton(null));
		final Timestamp time = new Timestamp(System.currentTimeMillis());
		try {
			String idsStr = Joiner.on(",").join(ids);
			List<ConfigInfo> configInfoList = findConfigInfosByIds(idsStr);
			if (CollectionUtils.isNotEmpty(configInfoList)) {
				removeConfigInfoByIdsAtomic(idsStr);
				for (ConfigInfo configInfo : configInfoList) {
					removeTagByIdAtomic(configInfo.getId());
					insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp,
							srcUser, time, "D");
				}
			}

			EmbeddedStorageContextUtils.onBatchDeleteConfigInfo(configInfoList);
			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException("Failed to config batch deletion");
			}

			return configInfoList;
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void removeConfigInfo4Beta(final String dataId, final String group,
			final String tenant) {
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		ConfigInfo configInfo = findConfigInfo4Beta(dataId, group, tenant);
		if (configInfo != null) {
			try {
				final String sql = "DELETE FROM config_info_beta WHERE data_id=? AND group_id=? AND tenant_id=?";
				final Object[] args = new Object[] { dataId, group, tenantTmp };

				EmbeddedStorageContextUtils
						.onDeleteConfigBetaInfo(tenantTmp, group, dataId,
								System.currentTimeMillis());
				EmbeddedStorageContextUtils.addSqlContext(sql, args);

				boolean result = databaseOperate
						.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
				if (!result) {
					throw new NacosConfigException("[Tag] Configuration deletion failed");
				}
			}
			finally {
				EmbeddedStorageContextUtils.cleanAllContext();
			}

		}
	}

	public boolean addAggrConfigInfo(final String dataId, final String group,
			String tenant, final String datumId, String appName, final String content) {
		String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String contentTmp = StringUtils.isBlank(content) ? StringUtils.EMPTY : content;
		final Timestamp now = new Timestamp(System.currentTimeMillis());

		final String select =
				"SELECT content FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?  AND "
						+ "datum_id = ?";
		final String insert =
				"INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, content, gmt_modified) "
						+ "VALUES(?,?,?,?,?,?,?) ";
		final String update =
				"UPDATE config_info_aggr SET content = ? , gmt_modified = ? WHERE data_id = ? AND group_id = ? AND "
						+ "tenant_id = ? AND datum_id = ?";

		String dbContent = databaseOperate
				.queryOne(select, new Object[] { dataId, group, tenantTmp, datumId },
						String.class);

		if (Objects.isNull(dbContent)) {
			final Object[] args = new Object[] { dataId, group, tenantTmp, datumId,
					appNameTmp, contentTmp, now };
			EmbeddedStorageContextUtils.addSqlContext(insert, args);
		}
		else if (!dbContent.equals(content)) {
			final Object[] args = new Object[] { contentTmp, now, dataId, group,
					tenantTmp, datumId };
			EmbeddedStorageContextUtils.addSqlContext(update, args);
		}

		try {
			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException("[Merge] Configuration release failed");
			}
			return true;
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void removeSingleAggrConfigInfo(final String dataId, final String group,
			final String tenant, final String datumId) {
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

		final String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id=?";
		final Object[] args = new Object[] { dataId, group, tenantTmp, datumId };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);

		try {
			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException(
						"[aggregation with single] Configuration deletion failed");
			}
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void removeAggrConfigInfo(final String dataId, final String group,
			final String tenant) {
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

		final String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=?";
		final Object[] args = new Object[] { dataId, group, tenantTmp };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);

		try {
			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException(
						"[aggregation with all] Configuration deletion failed");
			}
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public boolean batchRemoveAggr(final String dataId, final String group,
			final String tenant, final List<String> datumList) {
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final StringBuilder datumString = new StringBuilder();
		for (String datum : datumList) {
			datumString.append("'").append(datum).append("',");
		}
		datumString.deleteCharAt(datumString.length() - 1);
		final String sql =
				"delete from config_info_aggr where data_id=? and group_id=? and tenant_id=? and datum_id in ("
						+ datumString.toString() + ")";
		final Object[] args = new Object[] { dataId, group, tenantTmp };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);

		try {
			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException(
						"[aggregation] Failed to configure batch deletion");
			}
			return true;
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
		String sql = "delete from his_config_info where gmt_modified < ? limit ?";
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		helper.updateLimit(sql, new Object[] { startTime, limitSize });
	}

	public int findConfigHistoryCountByTime(final Timestamp startTime) {
		String sql = "SELECT COUNT(*) FROM his_config_info WHERE gmt_modified < ?";
		Integer result = databaseOperate
				.queryOne(sql, new Object[] { startTime }, Integer.class);
		if (result == null) {
			throw new IllegalArgumentException("configInfoBetaCount error");
		}
		return result;
	}

	public long findConfigMaxId() {
		String sql = "SELECT max(id) FROM config_info";
		return Optional.ofNullable(databaseOperate.queryOne(sql, Long.class)).orElse(0L);
	}

	public boolean batchPublishAggr(final String dataId, final String group,
			final String tenant, final Map<String, String> datumMap,
			final String appName) {
		try {
			Boolean isPublishOk = false;
			for (Entry<String, String> entry : datumMap.entrySet()) {
				addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName,
						entry.getValue());
			}

			isPublishOk = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());

			if (isPublishOk == null) {
				return false;
			}
			return isPublishOk;
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public boolean replaceAggr(final String dataId, final String group,
			final String tenant, final Map<String, String> datumMap,
			final String appName) {
		Boolean isReplaceOk = false;
		String appNameTmp = appName == null ? "" : appName;

		removeAggrConfigInfo(dataId, group, tenant);

		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String sql =
				"INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, "
						+ "content, gmt_modified) VALUES(?,?,?,?,?,?,?) ";
		for (Entry<String, String> datumEntry : datumMap.entrySet()) {
			final Object[] args = new Object[] { dataId, group, tenantTmp,
					datumEntry.getKey(), appNameTmp, datumEntry.getValue(),
					new Timestamp(System.currentTimeMillis()) };
			EmbeddedStorageContextUtils.addSqlContext(sql, args);
		}
		try {
			isReplaceOk = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());

			if (isReplaceOk == null) {
				return false;
			}
			return isReplaceOk;
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}

	}

	public List<ConfigInfo> findAllDataIdAndGroup() {
		String sql = "SELECT DISTINCT data_id, group_id FROM config_info";
		return databaseOperate.queryMany(sql, EMPTY_ARRAY, CONFIG_INFO_ROW_MAPPER);
	}

	public ConfigInfo4Beta findConfigInfo4Beta(final String dataId, final String group,
			final String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String sql =
				"SELECT ID,data_id,group_id,tenant_id,app_name,content,beta_ips FROM config_info_beta WHERE data_id=?"
						+ " AND group_id=? AND tenant_id=?";

		return databaseOperate.queryOne(sql, new Object[] { dataId, group, tenantTmp },
				CONFIG_INFO4BETA_ROW_MAPPER);

	}

	public ConfigInfo4Tag findConfigInfo4Tag(final String dataId, final String group,
			final String tenant, final String tag) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();

		final String sql =
				"SELECT ID,data_id,group_id,tenant_id,tag_id,app_name,content FROM config_info_tag WHERE data_id=? "
						+ "AND group_id=? AND tenant_id=? AND tag_id=?";

		return databaseOperate
				.queryOne(sql, new Object[] { dataId, group, tenantTmp, tagTmp },
						CONFIG_INFO4TAG_ROW_MAPPER);
	}

	public ConfigInfo findConfigInfoApp(final String dataId, final String group,
			final String tenant, final String appName) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String sql =
				"SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND "
						+ "group_id=? AND tenant_id=? AND app_name=?";

		return databaseOperate
				.queryOne(sql, new Object[] { dataId, group, tenantTmp, appName },
						CONFIG_INFO_ROW_MAPPER);

	}

	public ConfigInfo findConfigInfoAdvanceInfo(final String dataId, final String group,
			final String tenant, final Map<String, Object> configAdvanceInfo) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String appName = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("appName");
		final String configTags = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("config_tags");
		List<String> paramList = new ArrayList<String>();
		paramList.add(dataId);
		paramList.add(group);
		paramList.add(tenantTmp);

		StringBuilder sql = new StringBuilder(
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and group_id=? "
						+ "and tenant_id=? ");
		if (StringUtils.isNotBlank(configTags)) {
			sql = new StringBuilder(
					"select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
							+ "config_tags_relation b on a.id=b.id where a.data_id=? and a.group_id=? and a.tenant_id=? ");
			sql.append(" and b.tag_name in (");
			String[] tagArr = configTags.split(",");
			for (int i = 0; i < tagArr.length; i++) {
				if (i != 0) {
					sql.append(", ");
				}
				sql.append("?");
				paramList.add(tagArr[i]);
			}
			sql.append(") ");

			if (StringUtils.isNotBlank(appName)) {
				sql.append(" and a.app_name=? ");
				paramList.add(appName);
			}
		}
		else {
			if (StringUtils.isNotBlank(appName)) {
				sql.append(" and app_name=? ");
				paramList.add(appName);
			}
		}

		return databaseOperate
				.queryOne(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);
	}

	public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
		final String sql =
				"SELECT ID,data_id,group_id,content FROM config_info WHERE data_id=? AND group_id=? AND "
						+ "tenant_id=?";

		return databaseOperate
				.queryOne(sql, new Object[] { dataId, group, StringUtils.EMPTY },
						CONFIG_INFO_BASE_ROW_MAPPER);
	}

	public ConfigInfo findConfigInfo(long id) {
		final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE ID=?";

		return databaseOperate.queryOne(sql, new Object[] { id }, CONFIG_INFO_ROW_MAPPER);
	}

	public Page<ConfigInfo> findConfigInfoByDataId(final int pageNo, final int pageSize,
			final String dataId, final String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(
				"select count(*) from config_info where data_id=? and tenant_id=?",
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and "
						+ "tenant_id=?", new Object[] { dataId, tenantTmp }, pageNo,
				pageSize, CONFIG_INFO_ROW_MAPPER);
	}

	public Page<ConfigInfo> findConfigInfoByDataIdAndApp(final int pageNo,
			final int pageSize, final String dataId, final String tenant,
			final String appName) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(
				"select count(*) from config_info where data_id=? and tenant_id=? and app_name=?",
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and "
						+ "tenant_id=? and app_name=?",
				new Object[] { dataId, tenantTmp, appName }, pageNo, pageSize,
				CONFIG_INFO_ROW_MAPPER);
	}

	public Page<ConfigInfo> findConfigInfoByDataIdAndAdvance(final int pageNo,
			final int pageSize, final String dataId, final String tenant,
			final Map<String, Object> configAdvanceInfo) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String appName = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("appName");
		final String configTags = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("config_tags");
		StringBuilder sqlCount = new StringBuilder(
				"select count(*) from config_info where data_id=? and tenant_id=? ");
		StringBuilder sql = new StringBuilder(
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and tenant_id=? ");
		List<String> paramList = new ArrayList<String>();
		paramList.add(dataId);
		paramList.add(tenantTmp);
		if (StringUtils.isNotBlank(configTags)) {
			sqlCount = new StringBuilder(
					"select count(*) from config_info  a left join config_tags_relation b on a.id=b.id where a.data_id=? "
							+ "and a.tenant_id=? ");

			sql = new StringBuilder(
					"select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
							+ "config_tags_relation b on a.id=b.id where a.data_id=? and a.tenant_id=? ");

			sqlCount.append(" and b.tag_name in (");
			sql.append(" and b.tag_name in (");
			String[] tagArr = configTags.split(",");
			for (int i = 0; i < tagArr.length; i++) {
				if (i != 0) {
					sqlCount.append(", ");
					sql.append(", ");
				}
				sqlCount.append("?");
				sql.append("?");
				paramList.add(tagArr[i]);
			}
			sqlCount.append(") ");
			sql.append(") ");

			if (StringUtils.isNotBlank(appName)) {
				sqlCount.append(" and a.app_name=? ");
				sql.append(" and a.app_name=? ");
				paramList.add(appName);
			}
		}
		else {
			if (StringUtils.isNotBlank(appName)) {
				sqlCount.append(" and app_name=? ");
				sql.append(" and app_name=? ");
				paramList.add(appName);
			}
		}

		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(sqlCount.toString(), sql.toString(), paramList.toArray(),
				pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
	}

	public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize,
			final String dataId, final String group, final String tenant,
			final Map<String, Object> configAdvanceInfo) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String appName = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("appName");
		final String configTags = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("config_tags");
		String sqlCount = "select count(*) from config_info";
		String sql = "select ID,data_id,group_id,tenant_id,app_name,content,type from config_info";
		StringBuilder where = new StringBuilder(" where ");
		List<String> paramList = new ArrayList<String>();
		paramList.add(tenantTmp);
		if (StringUtils.isNotBlank(configTags)) {
			sqlCount = "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id";
			sql = "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
					+ "config_tags_relation b on a.id=b.id";

			where.append(" a.tenant_id=? ");

			if (StringUtils.isNotBlank(dataId)) {
				where.append(" and a.data_id=? ");
				paramList.add(dataId);
			}
			if (StringUtils.isNotBlank(group)) {
				where.append(" and a.group_id=? ");
				paramList.add(group);
			}
			if (StringUtils.isNotBlank(appName)) {
				where.append(" and a.app_name=? ");
				paramList.add(appName);
			}

			where.append(" and b.tag_name in (");
			String[] tagArr = configTags.split(",");
			for (int i = 0; i < tagArr.length; i++) {
				if (i != 0) {
					where.append(", ");
				}
				where.append("?");
				paramList.add(tagArr[i]);
			}
			where.append(") ");
		}
		else {
			where.append(" tenant_id=? ");
			if (StringUtils.isNotBlank(dataId)) {
				where.append(" and data_id=? ");
				paramList.add(dataId);
			}
			if (StringUtils.isNotBlank(group)) {
				where.append(" and group_id=? ");
				paramList.add(group);
			}
			if (StringUtils.isNotBlank(appName)) {
				where.append(" and app_name=? ");
				paramList.add(appName);
			}
		}
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper
				.fetchPage(sqlCount + where, sql + where, paramList.toArray(), pageNo,
						pageSize, CONFIG_INFO_ROW_MAPPER);
	}

	public Page<ConfigInfoBase> findConfigInfoBaseByDataId(final int pageNo,
			final int pageSize, final String dataId) {
		PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
		return helper.fetchPage(
				"select count(*) from config_info where data_id=? and tenant_id=?",
				"select ID,data_id,group_id,content from config_info where data_id=? and tenant_id=?",
				new Object[] { dataId, StringUtils.EMPTY }, pageNo, pageSize,
				CONFIG_INFO_BASE_ROW_MAPPER);

	}

	public Page<ConfigInfo> findConfigInfoByGroup(final int pageNo, final int pageSize,
			final String group, final String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(
				"select count(*) from config_info where group_id=? and tenant_id=?",
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and "
						+ "tenant_id=?", new Object[] { group, tenantTmp }, pageNo,
				pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfo> findConfigInfoByGroupAndApp(final int pageNo,
			final int pageSize, final String group, final String tenant,
			final String appName) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(
				"select count(*) from config_info where group_id=? and tenant_id=? and app_name =?",
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and "
						+ "tenant_id=? and app_name =?",
				new Object[] { group, tenantTmp, appName }, pageNo, pageSize,
				CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfo> findConfigInfoByGroupAndAdvance(final int pageNo,
			final int pageSize, final String group, final String tenant,
			final Map<String, Object> configAdvanceInfo) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

		final String appName = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("appName");
		final String configTags = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("config_tags");
		StringBuilder sqlCount = new StringBuilder(
				"select count(*) from config_info where group_id=? and tenant_id=? ");
		StringBuilder sql = new StringBuilder(
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and tenant_id=? ");
		List<String> paramList = new ArrayList<String>();
		paramList.add(group);
		paramList.add(tenantTmp);
		if (StringUtils.isNotBlank(configTags)) {
			sqlCount = new StringBuilder(
					"select count(*) from config_info  a left join config_tags_relation b on a.id=b.id where a.group_id=?"
							+ " and a.tenant_id=? ");
			sql = new StringBuilder(
					"select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
							+ "config_tags_relation b on a.id=b.id where a.group_id=? and a.tenant_id=? ");

			sqlCount.append(" and b.tag_name in (");
			sql.append(" and b.tag_name in (");
			String[] tagArr = configTags.split(",");
			for (int i = 0; i < tagArr.length; i++) {
				if (i != 0) {
					sqlCount.append(", ");
					sql.append(", ");
				}
				sqlCount.append("?");
				sql.append("?");
				paramList.add(tagArr[i]);
			}
			sqlCount.append(") ");
			sql.append(") ");

			if (StringUtils.isNotBlank(appName)) {
				sqlCount.append(" and a.app_name=? ");
				sql.append(" and a.app_name=? ");
				paramList.add(appName);
			}
		}
		else {
			if (StringUtils.isNotBlank(appName)) {
				sqlCount.append(" and app_name=? ");
				sql.append(" and app_name=? ");
				paramList.add(appName);
			}
		}
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(sqlCount.toString(), sql.toString(), paramList.toArray(),
				pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfo> findConfigInfoByApp(final int pageNo, final int pageSize,
			final String tenant, final String appName) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(
				"select count(*) from config_info where tenant_id like ? and app_name=?",
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where tenant_id like ? and "
						+ "app_name=?",
				new Object[] { generateLikeArgument(tenantTmp), appName }, pageNo,
				pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfo> findConfigInfoByAdvance(final int pageNo, final int pageSize,
			final String tenant, final Map<String, Object> configAdvanceInfo) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String appName = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("appName");
		final String configTags = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("config_tags");
		StringBuilder sqlCount = new StringBuilder(
				"select count(*) from config_info where tenant_id like ? ");
		StringBuilder sql = new StringBuilder(
				"select ID,data_id,group_id,tenant_id,app_name,content from config_info where tenant_id like ? ");
		List<String> paramList = new ArrayList<String>();
		paramList.add(tenantTmp);
		if (StringUtils.isNotBlank(configTags)) {
			sqlCount = new StringBuilder(
					"select count(*) from config_info a left join config_tags_relation b on a.id=b.id where a.tenant_id=?"
							+ " ");

			sql = new StringBuilder(
					"select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
							+ "config_tags_relation b on a.id=b.id where a.tenant_id=? ");

			sqlCount.append(" and b.tag_name in (");
			sql.append(" and b.tag_name in (");
			String[] tagArr = configTags.split(",");
			for (int i = 0; i < tagArr.length; i++) {
				if (i != 0) {
					sqlCount.append(", ");
					sql.append(", ");
				}
				sqlCount.append("?");
				sql.append("?");
				paramList.add(tagArr[i]);
			}
			sqlCount.append(") ");
			sql.append(") ");

			if (StringUtils.isNotBlank(appName)) {
				sqlCount.append(" and a.app_name=? ");
				sql.append(" and a.app_name=? ");
				paramList.add(appName);
			}
		}
		else {
			if (StringUtils.isNotBlank(appName)) {
				sqlCount.append(" and app_name=? ");
				sql.append(" and app_name=? ");
				paramList.add(appName);
			}
		}
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPage(sqlCount.toString(), sql.toString(), paramList.toArray(),
				pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo,
			final int pageSize, final String group) {
		PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
		return helper.fetchPage(
				"select count(*) from config_info where group_id=? and tenant_id=?",
				"select ID,data_id,group_id,content from config_info where group_id=? and tenant_id=?",
				new Object[] { group, StringUtils.EMPTY }, pageNo, pageSize,
				CONFIG_INFO_BASE_ROW_MAPPER);
	}

	public int configInfoCount() {
		String sql = " SELECT COUNT(ID) FROM config_info ";
		Integer result = databaseOperate.queryOne(sql, Integer.class);
		if (result == null) {
			throw new IllegalArgumentException("configInfoCount error");
		}
		return result;
	}

	public int configInfoCount(String tenant) {
		String sql = " SELECT COUNT(ID) FROM config_info where tenant_id like '" + tenant
				+ "'";
		Integer result = databaseOperate.queryOne(sql, Integer.class);
		if (result == null) {
			throw new IllegalArgumentException("configInfoCount error");
		}
		return result;
	}

	public int configInfoBetaCount() {
		String sql = " SELECT COUNT(ID) FROM config_info_beta ";
		Integer result = databaseOperate.queryOne(sql, Integer.class);
		if (result == null) {
			throw new IllegalArgumentException("configInfoBetaCount error");
		}
		return result;
	}

	public int configInfoTagCount() {
		String sql = " SELECT COUNT(ID) FROM config_info_tag ";
		Integer result = databaseOperate.queryOne(sql, Integer.class);
		if (result == null) {
			throw new IllegalArgumentException("configInfoBetaCount error");
		}
		return result;
	}

	public List<String> getTenantIdList(int page, int pageSize) {
		String sql = "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT ?, ?";
		int from = (page - 1) * pageSize;
		return databaseOperate
				.queryMany(sql, new Object[] { from, pageSize }, String.class);
	}

	public List<String> getGroupIdList(int page, int pageSize) {
		String sql = "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT ?, ?";
		int from = (page - 1) * pageSize;
		return databaseOperate
				.queryMany(sql, new Object[] { from, pageSize }, String.class);
	}

	public int aggrConfigInfoCount(String dataId, String group, String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String sql = " SELECT COUNT(ID) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
		Integer result = databaseOperate
				.queryOne(sql, new Object[] { dataId, group, tenantTmp }, Integer.class);
		if (result == null) {
			throw new IllegalArgumentException("aggrConfigInfoCount error");
		}
		return result;
	}

	public int aggrConfigInfoCountIn(String dataId, String group, String tenant,
			List<String> datumIds) {
		return aggrConfigInfoCount(dataId, group, tenant, datumIds, true);
	}

	public int aggrConfigInfoCountNotIn(String dataId, String group, String tenant,
			List<String> datumIds) {
		return aggrConfigInfoCount(dataId, group, tenant, datumIds, false);
	}

	public int aggrConfigInfoCount(String dataId, String group, String tenant,
			List<String> datumIds, boolean isIn) {
		if (datumIds == null || datumIds.isEmpty()) {
			return 0;
		}
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		StringBuilder sql = new StringBuilder(
				" SELECT COUNT(*) FROM config_info_aggr WHERE data_id = ? and group_id = ? and tenant_id = ? and "
						+ "datum_id");
		if (isIn) {
			sql.append(" in (");
		}
		else {
			sql.append(" not in (");
		}
		for (int i = 0, size = datumIds.size(); i < size; i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append("?");
		}
		sql.append(")");

		List<Object> objectList = Lists.<Object>newArrayList(dataId, group, tenantTmp);
		objectList.addAll(datumIds);

		Integer result = databaseOperate
				.queryOne(sql.toString(), objectList.toArray(), Integer.class);
		if (result == null) {
			throw new IllegalArgumentException("aggrConfigInfoCount error");
		}
		return result;
	}

	public Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize,
			final String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String sqlCountRows = "SELECT COUNT(*) FROM config_info";
		String sqlFetchRows =
				" SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
						+ " FROM (                               "
						+ "   SELECT id FROM config_info         "
						+ "   WHERE tenant_id like ?                  "
						+ "   ORDER BY id LIMIT ?,?             "
						+ " ) g, config_info t                   "
						+ " WHERE g.id = t.id                    ";

		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
				new Object[] { generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize,
						pageSize }, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize,
			final String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String select = " SELECT data_id,group_id,app_name "
				+ " FROM (                               "
				+ "   SELECT id FROM config_info         "
				+ "   WHERE tenant_id LIKE ?                  "
				+ "   ORDER BY id LIMIT ?, ?             "
				+ " ) g, config_info t                   "
				+ " WHERE g.id = t.id                    ";

		final int totalCount = configInfoCount(tenant);
		int pageCount = totalCount / pageSize;
		if (totalCount > pageSize * pageCount) {
			pageCount++;
		}

		if (pageNo > pageCount) {
			return null;
		}

		final Page<ConfigKey> page = new Page<ConfigKey>();
		page.setPageNumber(pageNo);
		page.setPagesAvailable(pageCount);
		page.setTotalCount(totalCount);

		List<ConfigKey> result = databaseOperate.queryMany(select,
				new Object[] { generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize,
						pageSize },
				// new Object[0],
				CONFIG_KEY_ROW_MAPPER);

		for (ConfigKey item : result) {
			page.getPageItems().add(item);
		}
		return page;
	}

	public Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo,
			final int pageSize) {
		String sqlCountRows = "SELECT COUNT(*) FROM config_info";
		String sqlFetchRows = " SELECT t.id,data_id,group_id,content,md5 "
				+ " FROM (                               "
				+ "   SELECT id FROM config_info         "
				+ "   ORDER BY id LIMIT ?,?             "
				+ " ) g, config_info t                   "
				+ " WHERE g.id = t.id                    ";

		PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
		return helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
				new Object[] { (pageNo - 1) * pageSize, pageSize }, pageNo, pageSize,
				CONFIG_INFO_BASE_ROW_MAPPER);

	}

	public Page<ConfigInfoWrapper> findAllConfigInfoForDumpAll(final int pageNo,
			final int pageSize) {
		String sqlCountRows = "select count(*) from config_info";
		String sqlFetchRows =
				" SELECT t.id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified "
						+ " FROM (                               "
						+ "   SELECT id FROM config_info         "
						+ "   ORDER BY id LIMIT ?,?             "
						+ " ) g, config_info t                   "
						+ " WHERE g.id = t.id                    ";

		PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();

		return helper
				.fetchPageLimit(sqlCountRows, sqlFetchRows, EMPTY_ARRAY, pageNo, pageSize,
						CONFIG_INFO_WRAPPER_ROW_MAPPER);

	}

	public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId,
			final int pageSize) {
		String select =
				"SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type from config_info where id > ? "
						+ "order by id asc limit ?,?";
		PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
		return helper.fetchPageLimit(select, new Object[] { lastMaxId, 0, pageSize }, 1,
				pageSize, CONFIG_INFO_WRAPPER_ROW_MAPPER);

	}

	public Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo,
			final int pageSize) {
		String sqlCountRows = "SELECT COUNT(*) FROM config_info_beta";
		String sqlFetchRows =
				" SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips "
						+ " FROM (                               "
						+ "   SELECT id FROM config_info_beta         "
						+ "   ORDER BY id LIMIT ?,?             "
						+ " ) g, config_info_beta t                   "
						+ " WHERE g.id = t.id                    ";
		PaginationHelper<ConfigInfoBetaWrapper> helper = createPaginationHelper();
		return helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
				new Object[] { (pageNo - 1) * pageSize, pageSize }, pageNo, pageSize,
				CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);

	}

	public Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo,
			final int pageSize) {
		String sqlCountRows = "SELECT COUNT(*) FROM config_info_tag";
		String sqlFetchRows =
				" SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified "
						+ " FROM (                               "
						+ "   SELECT id FROM config_info_tag         "
						+ "   ORDER BY id LIMIT ?,?             "
						+ " ) g, config_info_tag t                   "
						+ " WHERE g.id = t.id                    ";
		PaginationHelper<ConfigInfoTagWrapper> helper = createPaginationHelper();
		return helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
				new Object[] { (pageNo - 1) * pageSize, pageSize }, pageNo, pageSize,
				CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);

	}

	public List<ConfigInfo> findConfigInfoByBatch(final List<String> dataIds,
			final String group, final String tenant, int subQueryLimit) {
		// assert dataids group not null
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		// if dataids empty return empty list
		if (CollectionUtils.isEmpty(dataIds)) {
			return Collections.emptyList();
		}

		// Volume query upper limit
		// The number of in is within 100. The shorter the SQL statement, the better
		if (subQueryLimit > QUERY_LIMIT_SIZE) {
			subQueryLimit = 50;
		}
		List<ConfigInfo> result = new ArrayList<ConfigInfo>(dataIds.size());

		String sqlStart =
				"select data_id, group_id, tenant_id, app_name, content from config_info where group_id = ? and "
						+ "tenant_id = ? and data_id in (";
		String sqlEnd = ")";
		StringBuilder subQuerySql = new StringBuilder();

		for (int i = 0; i < dataIds.size(); i += subQueryLimit) {
			// dataids
			List<String> params = new ArrayList<String>(
					dataIds.subList(i, Math.min(i + subQueryLimit, dataIds.size())));

			for (int j = 0; j < params.size(); j++) {
				subQuerySql.append("?");
				if (j != params.size() - 1) {
					subQuerySql.append(",");
				}
			}

			// group
			params.add(0, group);
			params.add(1, tenantTmp);

			final String sql = sqlStart + subQuerySql.toString() + sqlEnd;

			List<ConfigInfo> r = databaseOperate
					.queryMany(sql, params.toArray(), CONFIG_INFO_ROW_MAPPER);

			// assert not null
			if (r != null && r.size() > 0) {
				result.addAll(r);
			}
		}
		return result;
	}

	public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize,
			final String dataId, final String group, final String tenant,
			final String appName, final String content) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
			if (StringUtils.isBlank(appName)) {
				return this.findAllConfigInfo(pageNo, pageSize, tenantTmp);
			}
			else {
				return this.findConfigInfoByApp(pageNo, pageSize, tenantTmp, appName);
			}
		}

		String sqlCountRows = "select count(*) from config_info where ";
		String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info where ";
		String where = " 1=1 ";
		List<String> params = new ArrayList<String>();

		if (!StringUtils.isBlank(dataId)) {
			where += " and data_id like ? ";
			params.add(generateLikeArgument(dataId));
		}
		if (!StringUtils.isBlank(group)) {
			where += " and group_id like ? ";
			params.add(generateLikeArgument(group));
		}

		where += " and tenant_id like ? ";
		params.add(generateLikeArgument(tenantTmp));

		if (!StringUtils.isBlank(appName)) {
			where += " and app_name = ? ";
			params.add(appName);
		}
		if (!StringUtils.isBlank(content)) {
			where += " and content like ? ";
			params.add(generateLikeArgument(content));
		}
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper
				.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
						pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize,
			final String dataId, final String group, final String tenant,
			final Map<String, Object> configAdvanceInfo) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String appName = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("appName");
		final String content = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("content");
		final String configTags = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("config_tags");
		String sqlCountRows = "select count(*) from config_info";
		String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info";
		StringBuilder where = new StringBuilder(" where ");
		List<String> params = new ArrayList<String>();
		params.add(generateLikeArgument(tenantTmp));
		if (StringUtils.isNotBlank(configTags)) {
			sqlCountRows = "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id ";
			sqlFetchRows =
					"select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
							+ "config_tags_relation b on a.id=b.id ";

			where.append(" a.tenant_id like ? ");
			if (!StringUtils.isBlank(dataId)) {
				where.append(" and a.data_id like ? ");
				params.add(generateLikeArgument(dataId));
			}
			if (!StringUtils.isBlank(group)) {
				where.append(" and a.group_id like ? ");
				params.add(generateLikeArgument(group));
			}
			if (!StringUtils.isBlank(appName)) {
				where.append(" and a.app_name = ? ");
				params.add(appName);
			}
			if (!StringUtils.isBlank(content)) {
				where.append(" and a.content like ? ");
				params.add(generateLikeArgument(content));
			}

			where.append(" and b.tag_name in (");
			String[] tagArr = configTags.split(",");
			for (int i = 0; i < tagArr.length; i++) {
				if (i != 0) {
					where.append(", ");
				}
				where.append("?");
				params.add(tagArr[i]);
			}
			where.append(") ");
		}
		else {
			where.append(" tenant_id like ? ");
			if (!StringUtils.isBlank(dataId)) {
				where.append(" and data_id like ? ");
				params.add(generateLikeArgument(dataId));
			}
			if (!StringUtils.isBlank(group)) {
				where.append(" and group_id like ? ");
				params.add(generateLikeArgument(group));
			}
			if (!StringUtils.isBlank(appName)) {
				where.append(" and app_name = ? ");
				params.add(appName);
			}
			if (!StringUtils.isBlank(content)) {
				where.append(" and content like ? ");
				params.add(generateLikeArgument(content));
			}
		}
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper
				.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
						pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize,
			final ConfigKey[] configKeys, final boolean blacklist) {
		String sqlCountRows = "select count(*) from config_info where ";
		String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info where ";
		String where = " 1=1 ";
		// White list, please synchronize the condition is empty, there is no qualified configuration
		if (configKeys.length == 0 && !blacklist) {
			Page<ConfigInfo> page = new Page<ConfigInfo>();
			page.setTotalCount(0);
			return page;
		}
		List<String> params = new ArrayList<String>();
		boolean isFirst = true;
		for (ConfigKey configInfo : configKeys) {
			String dataId = configInfo.getDataId();
			String group = configInfo.getGroup();
			String appName = configInfo.getAppName();

			if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group) && StringUtils
					.isBlank(appName)) {
				break;
			}

			if (blacklist) {
				if (isFirst) {
					isFirst = false;
					where += " and ";
				}
				else {
					where += " and ";
				}

				where += "(";
				boolean isFirstSub = true;
				if (!StringUtils.isBlank(dataId)) {
					where += " data_id not like ? ";
					params.add(generateLikeArgument(dataId));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(group)) {
					if (!isFirstSub) {
						where += " or ";
					}
					where += " group_id not like ? ";
					params.add(generateLikeArgument(group));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(appName)) {
					if (!isFirstSub) {
						where += " or ";
					}
					where += " app_name != ? ";
					params.add(appName);
					isFirstSub = false;
				}
				where += ") ";
			}
			else {
				if (isFirst) {
					isFirst = false;
					where += " and ";
				}
				else {
					where += " or ";
				}
				where += "(";
				boolean isFirstSub = true;
				if (!StringUtils.isBlank(dataId)) {
					where += " data_id like ? ";
					params.add(generateLikeArgument(dataId));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(group)) {
					if (!isFirstSub) {
						where += " and ";
					}
					where += " group_id like ? ";
					params.add(generateLikeArgument(group));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(appName)) {
					if (!isFirstSub) {
						where += " and ";
					}
					where += " app_name = ? ";
					params.add(appName);
					isFirstSub = false;
				}
				where += ") ";
			}
		}
		PaginationHelper<ConfigInfo> helper = createPaginationHelper();
		return helper
				.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
						pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

	}

	public Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo,
			final int pageSize, final String dataId, final String group,
			final String content) throws IOException {
		if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
			throw new IOException("invalid param");
		}

		String sqlCountRows = "select count(*) from config_info where ";
		String sqlFetchRows = "select ID,data_id,group_id,tenant_id,content from config_info where ";
		String where = " 1=1 and tenant_id='' ";
		List<String> params = new ArrayList<String>();

		if (!StringUtils.isBlank(dataId)) {
			where += " and data_id like ? ";
			params.add(generateLikeArgument(dataId));
		}
		if (!StringUtils.isBlank(group)) {
			where += " and group_id like ? ";
			params.add(generateLikeArgument(group));
		}
		if (!StringUtils.isBlank(content)) {
			where += " and content like ? ";
			params.add(generateLikeArgument(content));
		}
		PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
		return helper
				.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
						pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);

	}

	public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group,
			String tenant, String datumId) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String sql =
				"SELECT id,data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? "
						+ "AND group_id=? AND tenant_id=? AND datum_id=?";

		return databaseOperate
				.queryOne(sql, new Object[] { dataId, group, tenantTmp, datumId },
						CONFIG_INFO_AGGR_ROW_MAPPER);

	}

	public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group,
			String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String sql =
				"SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND "
						+ "group_id=? AND tenant_id=? ORDER BY datum_id";

		return databaseOperate.queryMany(sql, new Object[] { dataId, group, tenantTmp },
				CONFIG_INFO_AGGR_ROW_MAPPER);

	}

	public Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group,
			String tenant, final int pageNo, final int pageSize) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String sqlCountRows = "SELECT COUNT(*) FROM config_info_aggr WHERE data_id = ? and group_id = ? and tenant_id = ?";
		String sqlFetchRows =
				"select data_id,group_id,tenant_id,datum_id,app_name,content from config_info_aggr where data_id=? and "
						+ "group_id=? and tenant_id=? order by datum_id limit ?,?";
		PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
		return helper
				.fetchPageLimit(sqlCountRows, new Object[] { dataId, group, tenantTmp },
						sqlFetchRows,
						new Object[] { dataId, group, tenantTmp, (pageNo - 1) * pageSize,
								pageSize }, pageNo, pageSize,
						CONFIG_INFO_AGGR_ROW_MAPPER);

	}

	public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo,
			final int pageSize, ConfigKey[] configKeys, boolean blacklist) {

		String sqlCountRows = "select count(*) from config_info_aggr where ";
		String sqlFetchRows = "select data_id,group_id,tenant_id,datum_id,app_name,content from config_info_aggr where ";
		String where = " 1=1 ";
		// White list, please synchronize the condition is empty, there is no qualified configuration
		if (configKeys.length == 0 && blacklist == false) {
			Page<ConfigInfoAggr> page = new Page<ConfigInfoAggr>();
			page.setTotalCount(0);
			return page;
		}
		List<String> params = new ArrayList<String>();
		boolean isFirst = true;

		for (ConfigKey configInfoAggr : configKeys) {
			String dataId = configInfoAggr.getDataId();
			String group = configInfoAggr.getGroup();
			String appName = configInfoAggr.getAppName();
			if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group) && StringUtils
					.isBlank(appName)) {
				break;
			}
			if (blacklist) {
				if (isFirst) {
					isFirst = false;
					where += " and ";
				}
				else {
					where += " and ";
				}

				where += "(";
				boolean isFirstSub = true;
				if (!StringUtils.isBlank(dataId)) {
					where += " data_id not like ? ";
					params.add(generateLikeArgument(dataId));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(group)) {
					if (!isFirstSub) {
						where += " or ";
					}
					where += " group_id not like ? ";
					params.add(generateLikeArgument(group));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(appName)) {
					if (!isFirstSub) {
						where += " or ";
					}
					where += " app_name != ? ";
					params.add(appName);
					isFirstSub = false;
				}
				where += ") ";
			}
			else {
				if (isFirst) {
					isFirst = false;
					where += " and ";
				}
				else {
					where += " or ";
				}
				where += "(";
				boolean isFirstSub = true;
				if (!StringUtils.isBlank(dataId)) {
					where += " data_id like ? ";
					params.add(generateLikeArgument(dataId));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(group)) {
					if (!isFirstSub) {
						where += " and ";
					}
					where += " group_id like ? ";
					params.add(generateLikeArgument(group));
					isFirstSub = false;
				}
				if (!StringUtils.isBlank(appName)) {
					if (!isFirstSub) {
						where += " and ";
					}
					where += " app_name = ? ";
					params.add(appName);
					isFirstSub = false;
				}
				where += ") ";
			}
		}
		PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
		return helper
				.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
						pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);

	}

	public List<ConfigInfoChanged> findAllAggrGroup() {
		String sql = "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr";

		return databaseOperate
				.queryMany(sql, EMPTY_ARRAY, CONFIG_INFO_CHANGED_ROW_MAPPER);

	}

	public List<String> findDatumIdByContent(String dataId, String groupId,
			String content) {
		String sql = "SELECT datum_id FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND content = ? ";

		return databaseOperate
				.queryMany(sql, new Object[] { dataId, groupId, content }, String.class);

	}

	public List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime,
			final Timestamp endTime) {
		List<Map<String, Object>> list = databaseOperate.queryMany(
				"SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified FROM config_info WHERE "
						+ "gmt_modified >=? AND gmt_modified <= ?",
				new Object[] { startTime, endTime });
		return convertChangeConfig(list);

	}

	public Page<ConfigInfoWrapper> findChangeConfig(final String dataId,
			final String group, final String tenant, final String appName,
			final Timestamp startTime, final Timestamp endTime, final int pageNo,
			final int pageSize, final long lastMaxId) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String sqlCountRows = "select count(*) from config_info where ";
		String sqlFetchRows = "select id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified from config_info where ";
		String where = " 1=1 ";
		List<Object> params = new ArrayList<Object>();

		if (!StringUtils.isBlank(dataId)) {
			where += " and data_id like ? ";
			params.add(generateLikeArgument(dataId));
		}
		if (!StringUtils.isBlank(group)) {
			where += " and group_id like ? ";
			params.add(generateLikeArgument(group));
		}

		if (!StringUtils.isBlank(tenantTmp)) {
			where += " and tenant_id = ? ";
			params.add(tenantTmp);
		}

		if (!StringUtils.isBlank(appName)) {
			where += " and app_name = ? ";
			params.add(appName);
		}
		if (startTime != null) {
			where += " and gmt_modified >=? ";
			params.add(startTime);
		}
		if (endTime != null) {
			where += " and gmt_modified <=? ";
			params.add(endTime);
		}
		PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
		return helper
				.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
						pageNo, pageSize, lastMaxId, CONFIG_INFO_WRAPPER_ROW_MAPPER);

	}

	public List<ConfigInfo> findDeletedConfig(final Timestamp startTime,
			final Timestamp endTime) {
		List<Map<String, Object>> list = databaseOperate.queryMany(
				"SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND "
						+ "gmt_modified >=? AND gmt_modified <= ?",
				new Object[] { startTime, endTime });
		return convertDeletedConfig(list);

	}

	public long addConfigInfoAtomic(final long id, final String srcIp,
			final String srcUser, final ConfigInfo configInfo, final Timestamp time,
			Map<String, Object> configAdvanceInfo) {
		final String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ?
				StringUtils.EMPTY :
				configInfo.getAppName();
		final String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
				StringUtils.EMPTY :
				configInfo.getTenant();
		final String desc =
				configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
		final String use =
				configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
		final String effect = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("effect");
		final String type =
				configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
		final String schema = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("schema");
		final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
		final String sql =
				"INSERT INTO config_info(id, data_id, group_id, tenant_id, app_name, content, md5, src_ip, src_user, gmt_create,"
						+ "gmt_modified, c_desc, c_use, effect, type, c_schema) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
		final Object[] args = new Object[] { id, configInfo.getDataId(),
				configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(),
				md5Tmp, srcIp, srcUser, time, time, desc, use, effect, type, schema, };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);
		return id;
	}

	public void addConfigTagRelationAtomic(long configId, String tagName, String dataId,
			String group, String tenant) {
		final String sql =
				"INSERT INTO config_tags_relation(id,tag_name,tag_type,data_id,group_id,tenant_id) "
						+ "VALUES(?,?,?,?,?,?)";
		final Object[] args = new Object[] { configId, tagName, null, dataId, group,
				tenant };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);
	}

	public void addConfigTagsRelation(long configId, String configTags, String dataId,
			String group, String tenant) {
		if (StringUtils.isNotBlank(configTags)) {
			String[] tagArr = configTags.split(",");
			for (int i = 0; i < tagArr.length; i++) {
				addConfigTagRelationAtomic(configId, tagArr[i], dataId, group, tenant);
			}
		}
	}

	public void removeTagByIdAtomic(long id) {
		final String sql = "DELETE FROM config_tags_relation WHERE id=?";
		final Object[] args = new Object[] { id };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);
	}

	public List<String> getConfigTagsByTenant(String tenant) {
		String sql = "SELECT tag_name FROM config_tags_relation WHERE tenant_id = ? ";
		return databaseOperate.queryMany(sql, new Object[] { tenant }, String.class);
	}

	public List<String> selectTagByConfig(String dataId, String group, String tenant) {
		String sql = "SELECT tag_name FROM config_tags_relation WHERE data_id=? AND group_id=? AND tenant_id = ? ";
		return databaseOperate
				.queryMany(sql, new Object[] { dataId, group, tenant }, String.class);
	}

	public void removeConfigInfoAtomic(final String dataId, final String group,
			final String tenant, final String srcIp, final String srcUser) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

		final String sql = "DELETE FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?";
		final Object[] args = new Object[] { dataId, group, tenantTmp };

		EmbeddedStorageContextUtils.addSqlContext(sql, args);
	}

	public void removeConfigInfoByIdsAtomic(final String ids) {
		if (StringUtils.isBlank(ids)) {
			return;
		}
		StringBuilder sql = new StringBuilder(SQL_DELETE_CONFIG_INFO_BY_IDS);
		sql.append("id in (");
		List<Long> paramList = new ArrayList<>();
		String[] tagArr = ids.split(",");
		for (int i = 0; i < tagArr.length; i++) {
			if (i != 0) {
				sql.append(", ");
			}
			sql.append("?");
			paramList.add(Long.parseLong(tagArr[i]));
		}
		sql.append(") ");
		EmbeddedStorageContextUtils.addSqlContext(sql.toString(), paramList.toArray());
	}

	public void removeConfigInfoTag(final String dataId, final String group,
			final String tenant, final String tag, final String srcIp,
			final String srcUser) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag;

		final String sql = "DELETE FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?";
		final Object[] args = new Object[] { dataId, group, tenantTmp, tagTmp };

		EmbeddedStorageContextUtils
				.onDeleteConfigTagInfo(tenantTmp, group, dataId, tagTmp, srcIp);
		EmbeddedStorageContextUtils.addSqlContext(sql, args);
		try {
			databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp,
			final String srcUser, final Timestamp time,
			Map<String, Object> configAdvanceInfo) {
		final String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ?
				StringUtils.EMPTY :
				configInfo.getAppName();
		final String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
				StringUtils.EMPTY :
				configInfo.getTenant();
		final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
		final String desc =
				configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
		final String use =
				configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
		final String effect = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("effect");
		final String type =
				configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
		final String schema = configAdvanceInfo == null ?
				null :
				(String) configAdvanceInfo.get("schema");

		final String sql =
				"UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=?,"
						+ "c_desc=?,c_use=?,effect=?,type=?,c_schema=? WHERE data_id=? AND group_id=? AND tenant_id=?";

		final Object[] args = new Object[] { configInfo.getContent(), md5Tmp, srcIp,
				srcUser, time, appNameTmp, desc, use, effect, type, schema,
				configInfo.getDataId(), configInfo.getGroup(), tenantTmp };

		EmbeddedStorageContextUtils.addSqlContext(sql, args);
	}

	public ConfigInfo findConfigInfo(final String dataId, final String group,
			final String tenant) {
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

		final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,type FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?";
		final Object[] args = new Object[] { dataId, group, tenantTmp };
		return databaseOperate.queryOne(sql, args, CONFIG_INFO_ROW_MAPPER);

	}

	public List<ConfigInfo> findConfigInfosByIds(final String ids) {
		if (StringUtils.isBlank(ids)) {
			return null;
		}
		StringBuilder sql = new StringBuilder(SQL_FIND_CONFIG_INFO_BY_IDS);
		sql.append("id in (");
		List<Long> paramList = new ArrayList<>();
		String[] tagArr = ids.split(",");
		for (int i = 0; i < tagArr.length; i++) {
			if (i != 0) {
				sql.append(", ");
			}
			sql.append("?");
			paramList.add(Long.parseLong(tagArr[i]));
		}
		sql.append(") ");
		return databaseOperate
				.queryMany(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);

	}

	public ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId,
			final String group, final String tenant) {
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);

		ConfigAdvanceInfo configAdvance = databaseOperate.queryOne(
				"SELECT gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
				new Object[] { dataId, group, tenantTmp },
				CONFIG_ADVANCE_INFO_ROW_MAPPER);

		if (CollectionUtils.isNotEmpty(configTagList)) {
			StringBuilder configTagsTmp = new StringBuilder();
			for (String configTag : configTagList) {
				if (configTagsTmp.length() == 0) {
					configTagsTmp.append(configTag);
				}
				else {
					configTagsTmp.append(",").append(configTag);
				}
			}
			configAdvance.setConfigTags(configTagsTmp.toString());
		}
		return configAdvance;
	}

	public ConfigAllInfo findConfigAllInfo(final String dataId, final String group,
			final String tenant) {
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

		final String sql =
				"SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,gmt_create,"
						+ "gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info "
						+ "WHERE data_id=? AND group_id=? AND tenant_id=?";

		List<String> configTagList = selectTagByConfig(dataId, group, tenant);

		ConfigAllInfo configAdvance = databaseOperate
				.queryOne(sql, new Object[] { dataId, group, tenantTmp },
						CONFIG_ALL_INFO_ROW_MAPPER);

		if (configTagList != null && !configTagList.isEmpty()) {
			StringBuilder configTagsTmp = new StringBuilder();
			for (String configTag : configTagList) {
				if (configTagsTmp.length() == 0) {
					configTagsTmp.append(configTag);
				}
				else {
					configTagsTmp.append(",").append(configTag);
				}
			}
			configAdvance.setConfigTags(configTagsTmp.toString());
		}
		return configAdvance;
	}

	public void insertConfigHistoryAtomic(long configHistoryId, ConfigInfo configInfo,
			String srcIp, String srcUser, final Timestamp time, String ops) {
		String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ?
				StringUtils.EMPTY :
				configInfo.getAppName();
		String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ?
				StringUtils.EMPTY :
				configInfo.getTenant();
		final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);

		final String sql =
				"INSERT INTO his_config_info (id,data_id,group_id,tenant_id,app_name,content,md5,"
						+ "src_ip,src_user,gmt_modified,op_type) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
		final Object[] args = new Object[] { configHistoryId, configInfo.getDataId(),
				configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(),
				md5Tmp, srcIp, srcUser, time, ops };

		EmbeddedStorageContextUtils.addSqlContext(sql, args);
	}

	public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group,
			String tenant, int pageNo, int pageSize) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		String sqlCountRows = "select count(*) from his_config_info where data_id = ? and group_id = ? and tenant_id = ?";
		String sqlFetchRows = "select nid,data_id,group_id,tenant_id,app_name,src_ip,op_type,gmt_create,gmt_modified from his_config_info where data_id = ? and group_id = ? and tenant_id = ? order by nid desc";

		PaginationHelper<ConfigHistoryInfo> helper = createPaginationHelper();
		return helper.fetchPage(sqlCountRows, sqlFetchRows,
				new Object[] { dataId, group, tenantTmp }, pageNo, pageSize,
				HISTORY_LIST_ROW_MAPPER);
	}

	public void addConfigSubAtomic(final String dataId, final String group,
			final String appName, final Timestamp date) {
		final String appNameTmp = appName == null ? "" : appName;
		final long id = idGeneratorManager.nextId(RESOURCE_APP_CONFIGDATA_RELATION_SUBS);

		final String sql = "INSERT INTO app_configdata_relation_subs(id, data_id,group_id,app_name,gmt_modified) VALUES(?,?,?,?,?)";
		final Object[] args = new Object[] { id, dataId, group, appNameTmp, date };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);

		try {
			databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void updateConfigSubAtomic(final String dataId, final String group,
			final String appName, final Timestamp time) {
		final String appNameTmp = appName == null ? "" : appName;

		final String sql = "UPDATE app_configdata_relation_subs SET gmt_modified=? WHERE data_id=? AND group_id=? AND app_name=?";
		final Object[] args = new Object[] { time, dataId, group, appNameTmp };
		EmbeddedStorageContextUtils.addSqlContext(sql, args);

		try {
			databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public ConfigHistoryInfo detailConfigHistory(Long nid) {
		String sqlFetchRows =
				"SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,"
						+ "op_type,gmt_create,gmt_modified FROM his_config_info WHERE nid = ?";
		return databaseOperate
				.queryOne(sqlFetchRows, new Object[] { nid }, HISTORY_DETAIL_ROW_MAPPER);
	}

	public void insertTenantInfoAtomic(String kp, String tenantId, String tenantName,
			String tenantDesc, String createResoure, final long time) {

		final String sql = "INSERT INTO tenant_info(kp,tenant_id,tenant_name,tenant_desc,create_source,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?)";
		final Object[] args = new Object[] { kp, tenantId, tenantName, tenantDesc,
				createResoure, time, time };

		EmbeddedStorageContextUtils.addSqlContext(sql, args);

		try {
			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException("Namespace creation failed");
			}
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public void updateTenantNameAtomic(String kp, String tenantId, String tenantName,
			String tenantDesc) {

		final String sql = "UPDATE tenant_info SET tenant_name = ?, tenant_desc = ?, gmt_modified= ? WHERE kp=? AND tenant_id=?";
		final Object[] args = new Object[] { tenantName, tenantDesc,
				System.currentTimeMillis(), kp, tenantId };

		EmbeddedStorageContextUtils.addSqlContext(sql, args);

		try {
			boolean result = databaseOperate
					.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
			if (!result) {
				throw new NacosConfigException("Namespace update failed");
			}
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public List<TenantInfo> findTenantByKp(String kp) {
		String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=?";
		return databaseOperate
				.queryMany(sql, new Object[] { kp }, TENANT_INFO_ROW_MAPPER);

	}

	public TenantInfo findTenantByKp(String kp, String tenantId) {
		String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=? AND tenant_id=?";
		return databaseOperate
				.queryOne(sql, new Object[] { kp, tenantId }, TENANT_INFO_ROW_MAPPER);

	}

	public void removeTenantInfoAtomic(final String kp, final String tenantId) {
		EmbeddedStorageContextUtils
				.addSqlContext("DELETE FROM tenant_info WHERE kp=? AND tenant_id=?", kp,
						tenantId);
		try {
			databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
		}
		finally {
			EmbeddedStorageContextUtils.cleanAllContext();
		}
	}

	public List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list) {
		List<ConfigInfo> configs = new ArrayList<ConfigInfo>();
		for (Map<String, Object> map : list) {
			String dataId = (String) map.get("data_id");
			String group = (String) map.get("group_id");
			String tenant = (String) map.get("tenant_id");
			ConfigInfo config = new ConfigInfo();
			config.setDataId(dataId);
			config.setGroup(group);
			config.setTenant(tenant);
			configs.add(config);
		}
		return configs;
	}

	public List<ConfigInfoWrapper> convertChangeConfig(List<Map<String, Object>> list) {
		List<ConfigInfoWrapper> configs = new ArrayList<ConfigInfoWrapper>();
		for (Map<String, Object> map : list) {
			String dataId = (String) map.get("data_id");
			String group = (String) map.get("group_id");
			String tenant = (String) map.get("tenant_id");
			String content = (String) map.get("content");
			long mTime = ((Timestamp) map.get("gmt_modified")).getTime();
			ConfigInfoWrapper config = new ConfigInfoWrapper();
			config.setDataId(dataId);
			config.setGroup(group);
			config.setTenant(tenant);
			config.setContent(content);
			config.setLastModified(mTime);
			configs.add(config);
		}
		return configs;
	}

	public List<ConfigInfoWrapper> listAllGroupKeyMd5() {
		final int pageSize = 10000;
		int totalCount = configInfoCount();
		int pageCount = (int) Math.ceil(totalCount * 1.0 / pageSize);
		List<ConfigInfoWrapper> allConfigInfo = new ArrayList<ConfigInfoWrapper>();
		for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
			List<ConfigInfoWrapper> configInfoList = listGroupKeyMd5ByPage(pageNo,
					pageSize);
			allConfigInfo.addAll(configInfoList);
		}
		return allConfigInfo;
	}

	public List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize) {
		String sqlCountRows = " SELECT COUNT(*) FROM config_info ";
		String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified FROM ( SELECT id FROM config_info ORDER BY id LIMIT ?,?  ) g, config_info t WHERE g.id = t.id";
		PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
		Page<ConfigInfoWrapper> page = helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
				new Object[] { (pageNo - 1) * pageSize, pageSize }, pageNo, pageSize,
				CONFIG_INFO_WRAPPER_ROW_MAPPER);

		return page.getPageItems();
	}

	public String generateLikeArgument(String s) {
		String fuzzySearchSign = "\\*";
		String sqlLikePercentSign = "%";
		if (s.contains(PATTERN_STR)) {
			return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
		}
		else {
			return s;
		}
	}

	public ConfigInfoWrapper queryConfigInfo(final String dataId, final String group,
			final String tenant) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final String sql =
				"SELECT ID,data_id,group_id,tenant_id,app_name,content,type,gmt_modified,md5 FROM "
						+ "config_info WHERE data_id=? AND group_id=? AND tenant_id=?";

		return databaseOperate.queryOne(sql, new Object[] { dataId, group, tenantTmp },
				CONFIG_INFO_WRAPPER_ROW_MAPPER);
	}

	public boolean isExistTable(String tableName) {
		String sql = "SELECT COUNT(*) FROM " + tableName;
		try {
			databaseOperate.queryOne(sql, Integer.class);
			return true;
		}
		catch (Throwable e) {
			return false;
		}
	}

	public Boolean completeMd5() {
		defaultLog.info("[start completeMd5]");
		int perPageSize = 1000;
		int rowCount = configInfoCount();
		int pageCount = (int) Math.ceil(rowCount * 1.0 / perPageSize);
		int actualRowCount = 0;
		for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
			Page<ConfigInfoWrapper> page = findAllConfigInfoForDumpAll(pageNo,
					perPageSize);
			if (page != null) {
				for (ConfigInfoWrapper cf : page.getPageItems()) {
					String md5InDb = cf.getMd5();
					final String content = cf.getContent();
					final String tenant = cf.getTenant();
					final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
					if (StringUtils.isBlank(md5InDb)) {
						try {
							updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5,
									new Timestamp(cf.getLastModified()));
						}
						catch (Throwable e) {
							LogUtil.defaultLog
									.error("[completeMd5-error] datId:{} group:{} lastModified:{}",
											cf.getDataId(), cf.getGroup(),
											new Timestamp(cf.getLastModified()));
						}
					}
					else {
						if (!md5InDb.equals(md5)) {
							try {
								updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5,
										new Timestamp(cf.getLastModified()));
							}
							catch (Throwable e) {
								LogUtil.defaultLog
										.error("[completeMd5-error] datId:{} group:{} lastModified:{}",
												cf.getDataId(), cf.getGroup(),
												new Timestamp(cf.getLastModified()));
							}
						}
					}
				}

				actualRowCount += page.getPageItems().size();
				defaultLog.info("[completeMd5] {} / {}", actualRowCount, rowCount);
			}
		}
		return true;
	}

	public List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId,
			final String group, final String tenant, final String appName,
			final List<Long> ids) {
		String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		StringBuilder where = new StringBuilder(" where ");
		List<Object> paramList = new ArrayList<>();
		if (!CollectionUtils.isEmpty(ids)) {
			where.append(" id in (");
			for (int i = 0; i < ids.size(); i++) {
				if (i != 0) {
					where.append(", ");
				}
				where.append("?");
				paramList.add(ids.get(i));
			}
			where.append(") ");
		}
		else {
			where.append(" tenant_id=? ");
			paramList.add(tenantTmp);
			if (!StringUtils.isBlank(dataId)) {
				where.append(" and data_id like ? ");
				paramList.add(generateLikeArgument(dataId));
			}
			if (StringUtils.isNotBlank(group)) {
				where.append(" and group_id=? ");
				paramList.add(group);
			}
			if (StringUtils.isNotBlank(appName)) {
				where.append(" and app_name=? ");
				paramList.add(appName);
			}
		}
		return databaseOperate
				.queryMany(SQL_FIND_ALL_CONFIG_INFO + where, paramList.toArray(),
						CONFIG_ALL_INFO_ROW_MAPPER);
	}

	public Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList,
			String srcUser, String srcIp, Map<String, Object> configAdvanceInfo,
			Timestamp time, boolean notify, SameConfigPolicy policy)
			throws NacosException {
		int succCount = 0;
		int skipCount = 0;
		List<Map<String, String>> failData = null;
		List<Map<String, String>> skipData = null;

		for (int i = 0; i < configInfoList.size(); i++) {
			ConfigAllInfo configInfo = configInfoList.get(i);
			try {
				ParamUtils.checkParam(configInfo.getDataId(), configInfo.getGroup(),
						"datumId", configInfo.getContent());
			}
			catch (Throwable e) {
				defaultLog.error("data verification failed", e);
				throw e;
			}
			ConfigInfo configInfo2Save = new ConfigInfo(configInfo.getDataId(),
					configInfo.getGroup(), configInfo.getTenant(),
					configInfo.getAppName(), configInfo.getContent());

			String type = configInfo.getType();
			if (StringUtils.isBlank(type)) {
				// simple judgment of file type based on suffix
				if (configInfo.getDataId().contains(SPOT)) {
					String extName = configInfo.getDataId()
							.substring(configInfo.getDataId().lastIndexOf(SPOT) + 1)
							.toUpperCase();
					try {
						type = FileTypeEnum.valueOf(extName.toUpperCase()).getFileType();
					}
					catch (Throwable ex) {
						type = FileTypeEnum.TEXT.getFileType();
					}
				}
			}
			if (configAdvanceInfo == null) {
				configAdvanceInfo = new HashMap<>(16);
			}
			configAdvanceInfo.put("type", type);
			try {
				addConfigInfo(srcIp, srcUser, configInfo2Save, time, configAdvanceInfo,
						notify);
				succCount++;
			}
			catch (Throwable e) {
				if (!StringUtils.contains("DuplicateKeyException", e.toString())) {
					throw e;
				}
				// uniqueness constraint conflict
				if (SameConfigPolicy.ABORT.equals(policy)) {
					failData = new ArrayList<>();
					skipData = new ArrayList<>();
					Map<String, String> faileditem = new HashMap<>(2);
					faileditem.put("dataId", configInfo2Save.getDataId());
					faileditem.put("group", configInfo2Save.getGroup());
					failData.add(faileditem);
					for (int j = (i + 1); j < configInfoList.size(); j++) {
						ConfigInfo skipConfigInfo = configInfoList.get(j);
						Map<String, String> skipitem = new HashMap<>(2);
						skipitem.put("dataId", skipConfigInfo.getDataId());
						skipitem.put("group", skipConfigInfo.getGroup());
						skipData.add(skipitem);
					}
					break;
				}
				else if (SameConfigPolicy.SKIP.equals(policy)) {
					skipCount++;
					if (skipData == null) {
						skipData = new ArrayList<>();
					}
					Map<String, String> skipitem = new HashMap<>(2);
					skipitem.put("dataId", configInfo2Save.getDataId());
					skipitem.put("group", configInfo2Save.getGroup());
					skipData.add(skipitem);
				}
				else if (SameConfigPolicy.OVERWRITE.equals(policy)) {
					succCount++;
					updateConfigInfo(configInfo2Save, srcIp, srcUser, time,
							configAdvanceInfo, notify);
				}
			}
		}
		Map<String, Object> result = new HashMap<>(4);
		result.put("succCount", succCount);
		result.put("skipCount", skipCount);
		if (failData != null && !failData.isEmpty()) {
			result.put("failData", failData);
		}
		if (skipData != null && !skipData.isEmpty()) {
			result.put("skipData", skipData);
		}
		return result;
	}

	public int tenantInfoCountByTenantId(String tenantId) {
		Assert.hasText(tenantId, "tenantId can not be null");
		Integer result = databaseOperate
				.queryOne(SQL_TENANT_INFO_COUNT_BY_TENANT_ID, new String[] { tenantId },
						Integer.class);
		if (result == null) {
			return 0;
		}
		return result;
	}

}

