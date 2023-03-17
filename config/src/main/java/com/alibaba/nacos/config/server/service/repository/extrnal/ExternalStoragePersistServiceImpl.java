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

package com.alibaba.nacos.config.server.service.repository.extrnal;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
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
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoAggrMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoBetaMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoTagMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigTagsRelationMapper;
import com.alibaba.nacos.plugin.datasource.mapper.HistoryConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.mapper.TenantInfoMapper;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_ADVANCE_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_ALL_INFO_ROW_MAPPER;
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

/**
 * External Storage Persist Service.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author klw
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Component
@Deprecated
public class ExternalStoragePersistServiceImpl implements PersistService {
    
    private DataSourceService dataSourceService;
    
    private static final String PATTERN_STR = "*";
    
    private static final int QUERY_LIMIT_SIZE = 50;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private MapperManager mapperManager;
    
    private static final String DATA_ID = "dataId";
    
    private static final String GROUP = "group";
    
    private static final String APP_NAME = "appName";
    
    private static final String CONTENT = "content";
    
    private static final String TENANT = "tenant_id";
    
    /**
     * constant variables.
     */
    public static final String SPOT = ".";
    
    /**
     * init datasource.
     */
    @PostConstruct
    public void init() {
        dataSourceService = DynamicDataSource.getInstance().getDataSource();
        
        jt = getJdbcTemplate();
        tjt = getTransactionTemplate();
        Boolean isDataSourceLogEnable = EnvUtil
                .getProperty(Constants.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        mapperManager = MapperManager.instance(isDataSourceLogEnable);
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
    
    /**
     * For unit testing.
     */
    public JdbcTemplate getJdbcTemplate() {
        return this.dataSourceService.getJdbcTemplate();
    }
    
    public TransactionTemplate getTransactionTemplate() {
        return this.dataSourceService.getTransactionTemplate();
    }
    
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public String getCurrentDBUrl() {
        return this.dataSourceService.getCurrentDbUrl();
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new ExternalStoragePaginationHelperImpl<>(jt);
    }
    
    // ----------------------- config_info table insert update delete
    
    @Override
    public void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
        tjt.execute(status -> {
            try {
                long configId = addConfigInfoAtomic(-1, srcIp, srcUser, configInfo, time, configAdvanceInfo);
                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                addConfigTagsRelation(configId, configTags, configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
                
                insertConfigHistoryAtomic(0, configInfo, srcIp, srcUser, time, "I");
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                throw e;
            }
            return Boolean.TRUE;
        });
    }
    
    @Override
    public void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String encryptedDataKey = StringUtils.isBlank(configInfo.getEncryptedDataKey()) ? StringUtils.EMPTY
                : configInfo.getEncryptedDataKey();
        try {
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            jt.update(configInfoBetaMapper.insert(
                            Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "content", "md5", "beta_ips",
                                    "src_ip", "src_user", "gmt_create", "gmt_modified", "encrypted_data_key")),
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(), md5,
                    betaIps, srcIp, srcUser, time, time, encryptedDataKey);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        try {
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            jt.update(configInfoTagMapper.insert(
                            Arrays.asList("data_id", "group_id", "tenant_id", "tag_id", "app_name", "content", "md5", "src_ip",
                                    "src_user", "gmt_create", "gmt_modified")), configInfo.getDataId(), configInfo.getGroup(),
                    tenantTmp, tagTmp, appNameTmp, configInfo.getContent(), md5, srcIp, srcUser, time, time);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
        tjt.execute(status -> {
            try {
                ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
                String appNameTmp = oldConfigInfo.getAppName();
                /*
                 If the appName passed by the user is not empty, use the persistent user's appName,
                 otherwise use db; when emptying appName, you need to pass an empty string
                 */
                if (configInfo.getAppName() == null) {
                    configInfo.setAppName(appNameTmp);
                }
                updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);
                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                if (configTags != null) {
                    // delete all tags and then recreate
                    removeTagByIdAtomic(oldConfigInfo.getId());
                    addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(),
                            configInfo.getGroup(), configInfo.getTenant());
                }
                insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                throw e;
            }
            return Boolean.TRUE;
        });
    }
    
    @Override
    public boolean updateConfigInfoCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
        return tjt.execute(status -> {
            try {
                ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
                String appNameTmp = oldConfigInfo.getAppName();
                /*
                 If the appName passed by the user is not empty, use the persistent user's appName,
                 otherwise use db; when emptying appName, you need to pass an empty string
                 */
                if (configInfo.getAppName() == null) {
                    configInfo.setAppName(appNameTmp);
                }
                int rows = updateConfigInfoAtomicCas(configInfo, srcIp, srcUser, time, configAdvanceInfo);
                if (rows < 1) {
                    return Boolean.FALSE;
                }
                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                if (configTags != null) {
                    // delete all tags and then recreate
                    removeTagByIdAtomic(oldConfigInfo.getId());
                    addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(),
                            configInfo.getGroup(), configInfo.getTenant());
                }
                insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                throw e;
            }
            return Boolean.TRUE;
        });
    }
    
    @Override
    public void updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String encryptedDataKey = StringUtils.isBlank(configInfo.getEncryptedDataKey()) ? StringUtils.EMPTY
                : configInfo.getEncryptedDataKey();
        try {
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            jt.update(configInfoBetaMapper.update(
                            Arrays.asList("content", "md5", "beta_ips", "src_ip", "src_user", "gmt_modified", "app_name",
                                    "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    configInfo.getContent(), md5, betaIps, srcIp, srcUser, time, appNameTmp, encryptedDataKey,
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public boolean updateConfigInfo4BetaCas(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        try {
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            return jt.update(configInfoBetaMapper.updateConfigInfo4BetaCas(), configInfo.getContent(), md5, betaIps,
                    srcIp, srcUser, time, appNameTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp,
                    configInfo.getMd5()) > 0;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            jt.update(configInfoTagMapper.update(
                            Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "tag_id")), configInfo.getContent(), md5, srcIp,
                    srcUser, time, appNameTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public boolean updateConfigInfo4TagCas(ConfigInfo configInfo, String tag, String srcIp, String srcUser,
            Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            return jt.update(configInfoTagMapper.updateConfigInfo4TagCas(), configInfo.getContent(), md5, srcIp,
                    srcUser, time, appNameTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp,
                    configInfo.getMd5()) > 0;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            updateConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        }
    }
    
    @Override
    public boolean insertOrUpdateBetaCas(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
            return true;
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            return updateConfigInfo4BetaCas(configInfo, betaIps, srcIp, null, time, notify);
        }
    }
    
    @Override
    public void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            updateConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        }
    }
    
    @Override
    public boolean insertOrUpdateTagCas(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
            return true;
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            return updateConfigInfo4TagCas(configInfo, tag, srcIp, null, time, notify);
        }
    }
    
    @Override
    public void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            jt.update(configInfoMapper.update(Collections.singletonList("md5"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "gmt_modified")), md5, dataId, group, tenantTmp,
                    lastTime);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo) {
        insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, true);
    }
    
    @Override
    public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo, boolean notify) {
        try {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            updateConfigInfo(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
        }
    }
    
    @Override
    public boolean insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo) {
        return insertOrUpdateCas(srcIp, srcUser, configInfo, time, configAdvanceInfo, true);
    }
    
    @Override
    public boolean insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo, boolean notify) {
        try {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
            return true;
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            return updateConfigInfoCas(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
        }
    }
    
    @Override
    public void removeConfigInfo(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser) {
        tjt.execute(new TransactionCallback<Boolean>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());
            
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    ConfigInfo configInfo = findConfigInfo(dataId, group, tenant);
                    if (configInfo != null) {
                        removeConfigInfoAtomic(dataId, group, tenant, srcIp, srcUser);
                        removeTagByIdAtomic(configInfo.getId());
                        insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    @Override
    public List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        ids.removeAll(Collections.singleton(null));
        return tjt.execute(new TransactionCallback<List<ConfigInfo>>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());
            
            @Override
            public List<ConfigInfo> doInTransaction(TransactionStatus status) {
                try {
                    String idsStr = StringUtils.join(ids, StringUtils.COMMA);
                    List<ConfigInfo> configInfoList = findConfigInfosByIds(idsStr);
                    if (!CollectionUtils.isEmpty(configInfoList)) {
                        removeConfigInfoByIdsAtomic(idsStr);
                        for (ConfigInfo configInfo : configInfoList) {
                            removeTagByIdAtomic(configInfo.getId());
                            insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");
                        }
                    }
                    return configInfoList;
                } catch (CannotGetJdbcConnectionException e) {
                    LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                    throw e;
                }
            }
        });
    }
    
    @Override
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        tjt.execute(status -> {
            try {
                ConfigInfo configInfo = findConfigInfo4Beta(dataId, group, tenant);
                if (configInfo != null) {
                    ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(
                            dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_BETA);
                    jt.update(configInfoBetaMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id")), dataId,
                            group, tenantTmp);
                }
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                throw e;
            }
            return Boolean.TRUE;
        });
    }
    
    // ----------------------- config_aggr_info table insert update delete
    
    @Override
    public boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
            String appName, final String content) {
        String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String select = configInfoAggrMapper.select(Collections.singletonList("content"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        String insert = configInfoAggrMapper.insert(
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content", "gmt_modified"));
        String update = configInfoAggrMapper.update(Arrays.asList("content", "gmt_modified"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        try {
            try {
                String dbContent = jt.queryForObject(select, new Object[] {dataId, group, tenantTmp, datumId},
                        String.class);
                
                if (dbContent != null && dbContent.equals(content)) {
                    return true;
                } else {
                    return jt.update(update, content, now, dataId, group, tenantTmp, datumId) > 0;
                }
            } catch (EmptyResultDataAccessException ex) { // no data, insert
                return jt.update(insert, dataId, group, tenantTmp, datumId, appNameTmp, content, now) > 0;
            }
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant,
            final String datumId) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        try {
            this.jt.update(sql, ps -> {
                int index = 1;
                ps.setString(index++, dataId);
                ps.setString(index++, group);
                ps.setString(index++, tenantTmp);
                ps.setString(index, datumId);
            });
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
        
        try {
            this.jt.update(sql, ps -> {
                int index = 1;
                ps.setString(index++, dataId);
                ps.setString(index++, group);
                ps.setString(index, tenantTmp);
            });
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public boolean batchRemoveAggr(final String dataId, final String group, final String tenant,
            final List<String> datumList) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.batchRemoveAggr(datumList.size());
        final Object[] args = new Object[3 + datumList.size()];
        args[0] = dataId;
        args[1] = group;
        args[2] = tenantTmp;
        System.arraycopy(datumList.toArray(), 0, args, 3, datumList.size());
        try {
            jt.update(sql, args);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            return false;
        }
        return true;
    }
    
    @Override
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sql = historyConfigInfoMapper.removeConfigHistory();
        PaginationHelper<Object> paginationHelper = createPaginationHelper();
        paginationHelper.updateLimit(sql, new Object[] {startTime, limitSize});
    }
    
    @Override
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sql = historyConfigInfoMapper.findConfigHistoryCountByTime();
        Integer result = jt.queryForObject(sql, Integer.class, new Object[] {startTime});
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }
    
    @Override
    public long findConfigMaxId() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.findConfigMaxId();
        try {
            return jt.queryForObject(sql, Long.class);
        } catch (NullPointerException e) {
            return 0;
        }
    }
    
    @Override
    public boolean batchPublishAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        try {
            Boolean isPublishOk = tjt.execute(status -> {
                for (Map.Entry<String, String> entry : datumMap.entrySet()) {
                    try {
                        if (!addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName, entry.getValue())) {
                            throw new TransactionSystemException("error in addAggrConfigInfo");
                        }
                    } catch (Throwable e) {
                        throw new TransactionSystemException("error in addAggrConfigInfo");
                    }
                }
                return Boolean.TRUE;
            });
            if (isPublishOk == null) {
                return false;
            }
            return isPublishOk;
        } catch (TransactionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            return false;
        }
    }
    
    @Override
    public boolean replaceAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        try {
            ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_AGGR);
            Boolean isReplaceOk = tjt.execute(status -> {
                try {
                    String appNameTmp = appName == null ? "" : appName;
                    removeAggrConfigInfo(dataId, group, tenant);
                    String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
                    String sql = configInfoAggrMapper.insert(
                            Arrays.asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content",
                                    "gmt_modified"));
                    for (Map.Entry<String, String> datumEntry : datumMap.entrySet()) {
                        jt.update(sql, dataId, group, tenantTmp, datumEntry.getKey(), appNameTmp, datumEntry.getValue(),
                                new Timestamp(System.currentTimeMillis()));
                    }
                } catch (Throwable e) {
                    throw new TransactionSystemException("error in addAggrConfigInfo");
                }
                return Boolean.TRUE;
            });
            if (isReplaceOk == null) {
                return false;
            }
            return isReplaceOk;
        } catch (TransactionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            return false;
        }
        
    }
    
    @Deprecated
    @Override
    public List<ConfigInfo> findAllDataIdAndGroup() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.findAllDataIdAndGroup();
        
        try {
            return jt.query(sql, new Object[] {}, CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public ConfigInfoBetaWrapper findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            return this.jt.queryForObject(configInfoBetaMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "beta_ips",
                                    "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfoTagWrapper findConfigInfo4Tag(final String dataId, final String group, final String tenant,
            final String tag) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            return this.jt.queryForObject(configInfoTagMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "tag_id", "app_name", "content"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "tag_id")),
                    new Object[] {dataId, group, tenantTmp, tagTmp}, CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return this.jt.queryForObject(configInfoMapper.select(Arrays.asList("id", "data_id", "group_id", "content"),
                            Arrays.asList("data_id", "group_id", "tenant_id")), new Object[] {dataId, group, StringUtils.EMPTY},
                    CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfo findConfigInfo(long id) {
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return this.jt.queryForObject(configInfoMapper.select(
                    Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content"),
                    Collections.singletonList("id")), new Object[] {id}, CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfoWrapper findConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return this.jt.queryForObject(configInfoMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "type",
                                    "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        String sql = null;
        String sqlCount = null;
        List<String> paramList = new ArrayList<>();
        paramList.add(tenantTmp);
        Map<String, String> paramsMap = new HashMap<>(16);
        if (StringUtils.isNotBlank(dataId)) {
            paramList.add(dataId);
            paramsMap.put(DATA_ID, DATA_ID);
        }
        if (StringUtils.isNotBlank(group)) {
            paramList.add(group);
            paramsMap.put(GROUP, GROUP);
        }
        if (StringUtils.isNotBlank(appName)) {
            paramList.add(appName);
            paramsMap.put(APP_NAME, APP_NAME);
        }
        final int startRow = (pageNo - 1) * pageSize;
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            paramList.addAll(Arrays.asList(tagArr));
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
            sqlCount = configTagsRelationMapper.findConfigInfo4PageCountRows(paramsMap, tagArr.length);
            sql = configTagsRelationMapper.findConfigInfo4PageFetchRows(paramsMap, tagArr.length, startRow,
                    pageSize);
        } else {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            sqlCount = configInfoMapper.findConfigInfo4PageCountRows(paramsMap);
            sql = configInfoMapper.findConfigInfo4PageFetchRows(paramsMap, startRow, pageSize);
        }
        try {
            Page<ConfigInfo> page = helper.fetchPage(sqlCount, sql, paramList.toArray(), pageNo, pageSize,
                    CONFIG_INFO_ROW_MAPPER);
            for (ConfigInfo configInfo : page.getPageItems()) {
                Pair<String, String> pair = EncryptionHandler.decryptHandler(configInfo.getDataId(),
                        configInfo.getEncryptedDataKey(), configInfo.getContent());
                configInfo.setContent(pair.getSecond());
            }
            return page;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] ", e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByApp(final int pageNo, final int pageSize, final String tenant,
            final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        try {
            final int startRow = (pageNo - 1) * pageSize;
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return helper.fetchPage(configInfoMapper.findConfigInfoByAppCountRows(),
                    configInfoMapper.findConfigInfoByAppFetchRows(startRow, pageSize),
                    new Object[] {generateLikeArgument(tenantTmp), appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo, final int pageSize, final String group) {
        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
        try {
            final int startRow = (pageNo - 1) * pageSize;
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return helper.fetchPage(configInfoMapper.count(Arrays.asList("group_id", "tenant_id")),
                    configInfoMapper.findConfigInfoBaseByGroupFetchRows(startRow, pageSize),
                    new Object[] {group, StringUtils.EMPTY}, pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public int configInfoCount() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.count(null);
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int configInfoCount(String tenant) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.configInfoLikeTenantCount();
        Integer result = jt.queryForObject(sql, new Object[] {tenant}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int configInfoBetaCount() {
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        String sql = configInfoBetaMapper.count(null);
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int configInfoTagCount() {
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_TAG);
        String sql = configInfoTagMapper.count(null);
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }
    
    @Override
    public List<String> getTenantIdList(int page, int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        String sql = configInfoMapper.getTenantIdList(from, pageSize);
        return jt.queryForList(sql, String.class);
    }
    
    @Override
    public List<String> getGroupIdList(int page, int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        String sql = configInfoMapper.getGroupIdList(from, pageSize);
        return jt.queryForList(sql, String.class);
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        Integer result = jt.queryForObject(sql, Integer.class, new Object[] {dataId, group, tenantTmp});
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds, boolean isIn) {
        if (datumIds == null || datumIds.isEmpty()) {
            return 0;
        }
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.aggrConfigInfoCount(datumIds.size(), isIn);
        List<Object> objectList = com.alibaba.nacos.common.utils.CollectionUtils.list(dataId, group, tenantTmp);
        objectList.addAll(datumIds);
        Integer result = jt.queryForObject(sql, Integer.class, objectList.toArray());
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.count(null);
        String sqlFetchRows = configInfoMapper.findAllConfigInfoFetchRows(startRow, pageSize);
        
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        try {
            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
                    new Object[] {generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize,
                    CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        int startRow = (pageNo - 1) * pageSize;
        String select = configInfoMapper.findAllConfigKey(startRow, pageSize);
        
        final int totalCount = configInfoCount(tenant);
        int pageCount = totalCount / pageSize;
        if (totalCount > pageSize * pageCount) {
            pageCount++;
        }
        
        if (pageNo > pageCount) {
            return null;
        }
        
        final Page<ConfigKey> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(pageCount);
        page.setTotalCount(totalCount);
        
        try {
            List<ConfigKey> result = jt.query(select, new Object[] {generateLikeArgument(tenantTmp)},
                    // new Object[0],
                    CONFIG_KEY_ROW_MAPPER);
            
            for (ConfigKey item : result) {
                page.getPageItems().add(item);
            }
            return page;
        } catch (EmptyResultDataAccessException e) {
            return page;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    @Deprecated
    public Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.count(null);
        String sqlFetchRows = configInfoMapper.findAllConfigInfoBaseFetchRows(startRow, pageSize);
        
        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
        try {
            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {(pageNo - 1) * pageSize, pageSize},
                    pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String select = configInfoMapper.findAllConfigInfoFragment(0, pageSize);
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        try {
            return helper.fetchPageLimit(select, new Object[] {lastMaxId}, 1, pageSize, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        String sqlCountRows = configInfoBetaMapper.count(null);
        String sqlFetchRows = configInfoBetaMapper.findAllConfigInfoBetaForDumpAllFetchRows(startRow, pageSize);
        PaginationHelper<ConfigInfoBetaWrapper> helper = createPaginationHelper();
        try {
            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo, pageSize,
                    CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_TAG);
        String sqlCountRows = configInfoTagMapper.count(null);
        String sqlFetchRows = configInfoTagMapper.findAllConfigInfoTagForDumpAllFetchRows(startRow, pageSize);
        PaginationHelper<ConfigInfoTagWrapper> helper = createPaginationHelper();
        try {
            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo, pageSize,
                    CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final ConfigKey[] configKeys,
            final boolean blacklist) {
        String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String sqlFetchRows = "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE ";
        StringBuilder where = new StringBuilder(" 1=1 ");
        // Whitelist, please leave the synchronization condition empty, there is no configuration that meets the conditions
        if (configKeys.length == 0 && blacklist == false) {
            Page<ConfigInfo> page = new Page<>();
            page.setTotalCount(0);
            return page;
        }
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        List<String> params = new ArrayList<>();
        boolean isFirst = true;
        for (ConfigKey configInfo : configKeys) {
            String dataId = configInfo.getDataId();
            String group = configInfo.getGroup();
            String appName = configInfo.getAppName();
            
            if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group) && StringUtils.isBlank(appName)) {
                break;
            }
            
            if (blacklist) {
                if (isFirst) {
                    isFirst = false;
                    where.append(" AND ");
                } else {
                    where.append(" AND ");
                }
                
                where.append('(');
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where.append(" data_id NOT LIKE ? ");
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where.append(" OR ");
                    }
                    where.append(" group_id NOT LIKE ? ");
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where.append(" OR ");
                    }
                    where.append(" app_name != ? ");
                    params.add(appName);
                    isFirstSub = false;
                }
                where.append(") ");
            } else {
                if (isFirst) {
                    isFirst = false;
                    where.append(" AND ");
                } else {
                    where.append(" OR ");
                }
                where.append('(');
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where.append(" data_id LIKE ? ");
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where.append(" AND ");
                    }
                    where.append(" group_id LIKE ? ");
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where.append(" AND ");
                    }
                    where.append(" app_name = ? ");
                    params.add(appName);
                    isFirstSub = false;
                }
                where.append(") ");
            }
        }
        
        try {
            return helper.fetchPage(sqlCountRows + where.toString(), sqlFetchRows + where.toString(), params.toArray(),
                    pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        String sqlCountRows = null;
        String sqlFetchRows = null;
        Map<String, String> paramsMap = new HashMap<>(16);
        
        List<String> params = new ArrayList<>();
        params.add(generateLikeArgument(tenantTmp));
        if (!StringUtils.isBlank(dataId)) {
            params.add(generateLikeArgument(dataId));
            paramsMap.put(DATA_ID, DATA_ID);
        }
        if (!StringUtils.isBlank(group)) {
            params.add(generateLikeArgument(group));
            paramsMap.put(GROUP, GROUP);
        }
        if (!StringUtils.isBlank(appName)) {
            params.add(appName);
            paramsMap.put(APP_NAME, APP_NAME);
        }
        if (!StringUtils.isBlank(content)) {
            params.add(generateLikeArgument(content));
            paramsMap.put(CONTENT, CONTENT);
        }
        final int startRow = (pageNo - 1) * pageSize;
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            params.addAll(Arrays.asList(tagArr));
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
            sqlCountRows = configTagsRelationMapper.findConfigInfoLike4PageCountRows(paramsMap, tagArr.length);
            sqlFetchRows = configTagsRelationMapper.findConfigInfoLike4PageFetchRows(paramsMap, tagArr.length, startRow,
                    pageSize);
        } else {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            sqlCountRows = configInfoMapper.findConfigInfoLike4PageCountRows(paramsMap);
            sqlFetchRows = configInfoMapper.findConfigInfoLike4PageFetchRows(paramsMap, startRow, pageSize);
        }
        
        try {
            Page<ConfigInfo> page = helper.fetchPage(sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize,
                    CONFIG_INFO_ROW_MAPPER);
            
            for (ConfigInfo configInfo : page.getPageItems()) {
                Pair<String, String> pair = EncryptionHandler.decryptHandler(configInfo.getDataId(),
                        configInfo.getEncryptedDataKey(), configInfo.getContent());
                configInfo.setContent(pair.getSecond());
            }
            return page;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo, final int pageSize, final String dataId,
            final String group, final String content) throws IOException {
        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
            throw new IOException("invalid param");
        }
        
        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
        Map<String, String> paramsMap = new HashMap<>(16);
        List<String> params = new ArrayList<>();
        
        if (!StringUtils.isBlank(dataId)) {
            params.add(generateLikeArgument(dataId));
            paramsMap.put(DATA_ID, DATA_ID);
        }
        if (!StringUtils.isBlank(group)) {
            params.add(generateLikeArgument(group));
            paramsMap.put(GROUP, GROUP);
        }
        if (!StringUtils.isBlank(content)) {
            params.add(generateLikeArgument(content));
            paramsMap.put(CONTENT, CONTENT);
        }
        
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.findConfigInfoBaseLikeCountRows(paramsMap);
        String sqlFetchRows = configInfoMapper.findConfigInfoBaseLikeFetchRows(paramsMap, startRow, pageSize);
        
        try {
            return helper.fetchPage(sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize,
                    CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "datum_id", "app_name", "content"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        try {
            return this.jt.queryForObject(sql, new Object[] {dataId, group, tenantTmp, datumId},
                    CONFIG_INFO_AGGR_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            // EmptyResultDataAccessException, indicating that the data does not exist, returns null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findConfigInfoAggrIsOrdered();
        
        try {
            return this.jt.query(sql, new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_AGGR_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
            final int pageSize) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final int startRow = (pageNo - 1) * pageSize;
        String sqlCountRows = configInfoAggrMapper.select(Arrays.asList("count(*)"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        String sqlFetchRows = configInfoAggrMapper.findConfigInfoAggrByPageFetchRows(startRow, pageSize);
        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
        try {
            return helper.fetchPageLimit(sqlCountRows, new Object[] {dataId, group, tenantTmp}, sqlFetchRows,
                    new Object[] {dataId, group, tenantTmp}, pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
            boolean blacklist) {
        
        String sqlCountRows = "SELECT count(*) FROM config_info_aggr WHERE ";
        String sqlFetchRows = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE ";
        StringBuilder where = new StringBuilder(" 1=1 ");
        // Whitelist, please leave the synchronization condition empty, there is no configuration that meets the conditions
        if (configKeys.length == 0 && blacklist == false) {
            Page<ConfigInfoAggr> page = new Page<>();
            page.setTotalCount(0);
            return page;
        }
        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
        List<String> params = new ArrayList<>();
        boolean isFirst = true;
        
        for (ConfigKey configInfoAggr : configKeys) {
            String dataId = configInfoAggr.getDataId();
            String group = configInfoAggr.getGroup();
            String appName = configInfoAggr.getAppName();
            if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group) && StringUtils.isBlank(appName)) {
                break;
            }
            if (blacklist) {
                if (isFirst) {
                    isFirst = false;
                    where.append(" AND ");
                } else {
                    where.append(" AND ");
                }
                
                where.append('(');
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where.append(" data_id NOT LIKE ? ");
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where.append(" OR ");
                    }
                    where.append(" group_id NOT LIKE ? ");
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where.append(" OR ");
                    }
                    where.append(" app_name != ? ");
                    params.add(appName);
                    isFirstSub = false;
                }
                where.append(") ");
            } else {
                if (isFirst) {
                    isFirst = false;
                    where.append(" AND ");
                } else {
                    where.append(" OR ");
                }
                where.append('(');
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where.append(" data_id LIKE ? ");
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where.append(" AND ");
                    }
                    where.append(" group_id LIKE ? ");
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where.append(" AND ");
                    }
                    where.append(" app_name = ? ");
                    params.add(appName);
                    isFirstSub = false;
                }
                where.append(") ");
            }
        }
        
        try {
            Page<ConfigInfoAggr> result = helper.fetchPage(sqlCountRows + where.toString(),
                    sqlFetchRows + where.toString(), params.toArray(), pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
            return result;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfoChanged> findAllAggrGroup() {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findAllAggrGroupByDistinct();
        
        try {
            return jt.query(sql, new Object[] {}, CONFIG_INFO_CHANGED_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public List<String> findDatumIdByContent(String dataId, String groupId, String content) {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.select(Collections.singletonList("datum_id"),
                Arrays.asList("data_id", "group_id", "content"));
        
        try {
            return this.jt.queryForList(sql, new Object[] {dataId, groupId, content}, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime, final Timestamp endTime) {
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            List<Map<String, Object>> list = jt.queryForList(configInfoMapper.findChangeConfig(),
                    new Object[] {startTime, endTime});
            return convertChangeConfig(list);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoWrapper> findChangeConfig(final String dataId, final String group, final String tenant,
            final String appName, final Timestamp startTime, final Timestamp endTime, final int pageNo,
            final int pageSize, final long lastMaxId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        Map<String, String> paramsMap = new HashMap<>(16);
        List<Object> params = new ArrayList<>();
        if (!StringUtils.isBlank(dataId)) {
            params.add(generateLikeArgument(dataId));
            paramsMap.put(DATA_ID, DATA_ID);
        }
        if (!StringUtils.isBlank(group)) {
            params.add(generateLikeArgument(group));
            paramsMap.put(GROUP, GROUP);
        }
        
        if (!StringUtils.isBlank(tenantTmp)) {
            params.add(tenantTmp);
            paramsMap.put(TENANT, TENANT);
        }
        
        if (!StringUtils.isBlank(appName)) {
            params.add(appName);
            paramsMap.put(APP_NAME, APP_NAME);
        }
        if (startTime != null) {
            params.add(startTime);
        }
        if (endTime != null) {
            params.add(endTime);
        }
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.findChangeConfigCountRows(paramsMap, startTime, endTime);
        String sqlFetchRows = configInfoMapper.findChangeConfigFetchRows(paramsMap, startTime, endTime, startRow,
                pageSize, lastMaxId);
        
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        try {
            return helper.fetchPage(sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize, lastMaxId,
                    CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfo> findDeletedConfig(final Timestamp startTime, final Timestamp endTime) {
        try {
            HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
            List<Map<String, Object>> list = jt.queryForList(historyConfigInfoMapper.findDeletedConfig(),
                    new Object[] {startTime, endTime});
            return convertDeletedConfig(list);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public long addConfigInfoAtomic(final long configId, final String srcIp, final String srcUser,
            final ConfigInfo configInfo, final Timestamp time, Map<String, Object> configAdvanceInfo) {
        final String appNameTmp =
                StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        final String tenantTmp =
                StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.insert(
                Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip", "src_user",
                        "gmt_create", "gmt_modified", "c_desc", "c_use", "effect", "type", "c_schema",
                        "encrypted_data_key"));
        String[] returnGeneratedKeys = configInfoMapper.getPrimaryKeyGeneratedKeys();
        try {
            jt.update(new PreparedStatementCreator() {
                @Override
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(sql, returnGeneratedKeys);
                    ps.setString(1, configInfo.getDataId());
                    ps.setString(2, configInfo.getGroup());
                    ps.setString(3, tenantTmp);
                    ps.setString(4, appNameTmp);
                    ps.setString(5, configInfo.getContent());
                    ps.setString(6, md5Tmp);
                    ps.setString(7, srcIp);
                    ps.setString(8, srcUser);
                    ps.setTimestamp(9, time);
                    ps.setTimestamp(10, time);
                    ps.setString(11, desc);
                    ps.setString(12, use);
                    ps.setString(13, effect);
                    ps.setString(14, type);
                    ps.setString(15, schema);
                    ps.setString(16, encryptedDataKey);
                    return ps;
                }
            }, keyHolder);
            Number nu = keyHolder.getKey();
            if (nu == null) {
                throw new IllegalArgumentException("insert config_info fail");
            }
            return nu.longValue();
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void addConfigTagsRelation(long configId, String configTags, String dataId, String group, String tenant) {
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                addConfigTagRelationAtomic(configId, tagArr[i], dataId, group, tenant);
            }
        }
    }
    
    @Override
    public void addConfigTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant) {
        try {
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
            jt.update(configTagsRelationMapper.insert(
                            Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id", "tenant_id")), configId, tagName,
                    StringUtils.EMPTY, dataId, group, tenant);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void removeTagByIdAtomic(long id) {
        try {
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
            jt.update(configTagsRelationMapper.delete(Arrays.asList("id")), id);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
        String sql = configTagsRelationMapper.select(Arrays.asList("tag_name"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        try {
            return jt.queryForList(sql, new Object[] {dataId, group, tenant}, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void removeConfigInfoAtomic(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            jt.update(configInfoMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id")), dataId, group,
                    tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void removeConfigInfoByIdsAtomic(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return;
        }
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (String s : tagArr) {
            paramList.add(Long.parseLong(s));
        }
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.removeConfigInfoByIdsAtomic(paramList.size());
        try {
            jt.update(sql, paramList.toArray());
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
            final String srcIp, final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag;
        try {
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            jt.update(configInfoTagMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "tag_id")), dataId,
                    group, tenantTmp, tagTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, Map<String, Object> configAdvanceInfo) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            jt.update(configInfoMapper.update(
                            Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name", "c_desc", "c_use",
                                    "effect", "type", "c_schema", "encrypted_data_key"),
                            Arrays.asList("data_id", "group_id", "tenant_id")), configInfo.getContent(), md5Tmp, srcIp, srcUser,
                    time, appNameTmp, desc, use, effect, type, schema, encryptedDataKey, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    private int updateConfigInfoAtomicCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, Map<String, Object> configAdvanceInfo) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return jt.update(configInfoMapper.updateConfigInfoAtomicCas(), configInfo.getContent(), md5Tmp, srcIp,
                    srcUser, time, appNameTmp, desc, use, effect, type, schema, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp, configInfo.getMd5());
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfo> findConfigInfosByIds(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return null;
        }
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (int i = 0; i < tagArr.length; i++) {
            paramList.add(Long.parseLong(tagArr[i]));
        }
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return this.jt.query(configInfoMapper.findConfigInfosByIds(tagArr.length), paramList.toArray(),
                    CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            ConfigAdvanceInfo configAdvance = this.jt.queryForObject(configInfoMapper.select(
                            Arrays.asList("gmt_create", "gmt_modified", "src_user", "src_ip", "c_desc", "c_use", "effect",
                                    "type", "c_schema"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    new Object[] {dataId, group, tenantTmp}, CONFIG_ADVANCE_INFO_ROW_MAPPER);
            if (configTagList != null && !configTagList.isEmpty()) {
                StringBuilder configTagsTmp = new StringBuilder();
                for (String configTag : configTagList) {
                    if (configTagsTmp.length() == 0) {
                        configTagsTmp.append(configTag);
                    } else {
                        configTagsTmp.append(',').append(configTag);
                    }
                }
                configAdvance.setConfigTags(configTagsTmp.toString());
            }
            return configAdvance;
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            ConfigAllInfo configAdvance = this.jt.queryForObject(configInfoMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "gmt_create",
                                    "gmt_modified", "src_user", "src_ip", "c_desc", "c_use", "effect", "type", "c_schema",
                                    "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    new Object[] {dataId, group, tenantTmp}, CONFIG_ALL_INFO_ROW_MAPPER);
            if (configTagList != null && !configTagList.isEmpty()) {
                StringBuilder configTagsTmp = new StringBuilder();
                for (String configTag : configTagList) {
                    if (configTagsTmp.length() == 0) {
                        configTagsTmp.append(configTag);
                    } else {
                        configTagsTmp.append(',').append(configTag);
                    }
                }
                configAdvance.setConfigTags(configTagsTmp.toString());
            }
            return configAdvance;
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser,
            final Timestamp time, String ops) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String encryptedDataKey = StringUtils.isBlank(configInfo.getEncryptedDataKey()) ? StringUtils.EMPTY
                : configInfo.getEncryptedDataKey();
        
        try {
            HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
            jt.update(historyConfigInfoMapper.insert(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip",
                                    "src_user", "gmt_modified", "op_type", "encrypted_data_key")), id, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(), md5Tmp, srcIp, srcUser, time,
                    ops, encryptedDataKey);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
            int pageSize) {
        PaginationHelper<ConfigHistoryInfo> helper = createPaginationHelper();
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sqlCountRows = historyConfigInfoMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        String sqlFetchRows = historyConfigInfoMapper.findConfigHistoryFetchRows();
        
        Page<ConfigHistoryInfo> page = null;
        try {
            page = helper.fetchPage(sqlCountRows, sqlFetchRows, new Object[] {dataId, group, tenantTmp}, pageNo,
                    pageSize, HISTORY_LIST_ROW_MAPPER);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[list-config-history] error, dataId:{}, group:{}", new Object[] {dataId, group},
                    e);
            throw e;
        }
        return page;
    }
    
    @Override
    public ConfigHistoryInfo detailConfigHistory(Long nid) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sqlFetchRows = historyConfigInfoMapper.select(
                Arrays.asList("nid", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_user",
                        "src_ip", "op_type", "gmt_create", "gmt_modified", "encrypted_data_key"),
                Collections.singletonList("nid"));
        try {
            ConfigHistoryInfo historyInfo = jt.queryForObject(sqlFetchRows, new Object[] {nid},
                    HISTORY_DETAIL_ROW_MAPPER);
            return historyInfo;
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[detail-config-history] error, nid:{}", new Object[] {nid}, e);
            throw e;
        }
    }
    
    @Override
    public ConfigHistoryInfo detailPreviousConfigHistory(Long id) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sqlFetchRows = historyConfigInfoMapper.detailPreviousConfigHistory();
        try {
            ConfigHistoryInfo historyInfo = jt.queryForObject(sqlFetchRows, new Object[] {id},
                    HISTORY_DETAIL_ROW_MAPPER);
            return historyInfo;
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[detail-previous-config-history] error, id:{}", new Object[] {id}, e);
            throw e;
        }
    }
    
    @Override
    public void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
            String createResoure, final long time) {
        try {
            TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.TENANT_INFO);
            jt.update(tenantInfoMapper.insert(
                    Arrays.asList("kp", "tenant_id", "tenant_name", "tenant_desc", "create_source", "gmt_create",
                            "gmt_modified")), kp, tenantId, tenantName, tenantDesc, createResoure, time, time);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc) {
        try {
            TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.TENANT_INFO);
            jt.update(tenantInfoMapper.update(Arrays.asList("tenant_name", "tenant_desc", "gmt_modified"),
                            Arrays.asList("kp", "tenant_id")), tenantName, tenantDesc, System.currentTimeMillis(), kp,
                    tenantId);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<TenantInfo> findTenantByKp(String kp) {
        TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper.select(Arrays.asList("tenant_id", "tenant_name", "tenant_desc"),
                Collections.singletonList("kp"));
        try {
            return this.jt.query(sql, new Object[] {kp}, TENANT_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public TenantInfo findTenantByKp(String kp, String tenantId) {
        TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper.select(Arrays.asList("tenant_id", "tenant_name", "tenant_desc"),
                Arrays.asList("kp", "tenant_id"));
        try {
            return jt.queryForObject(sql, new Object[] {kp, tenantId}, TENANT_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void removeTenantInfoAtomic(final String kp, final String tenantId) {
        try {
            TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.TENANT_INFO);
            jt.update(tenantInfoMapper.delete(Arrays.asList("kp", "tenant_id")), kp, tenantId);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list) {
        List<ConfigInfo> configs = new ArrayList<>();
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
    
    @Override
    public List<ConfigInfoWrapper> convertChangeConfig(List<Map<String, Object>> list) {
        List<ConfigInfoWrapper> configs = new ArrayList<>();
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
    
    @Override
    public List<ConfigInfoWrapper> listAllGroupKeyMd5() {
        final int pageSize = 10000;
        int totalCount = configInfoCount();
        int pageCount = (int) Math.ceil(totalCount * 1.0 / pageSize);
        List<ConfigInfoWrapper> allConfigInfo = new ArrayList<>();
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            List<ConfigInfoWrapper> configInfoList = listGroupKeyMd5ByPage(pageNo, pageSize);
            allConfigInfo.addAll(configInfoList);
        }
        return allConfigInfo;
    }
    
    @Override
    public List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.count(null);
        String sqlFetchRows = configInfoMapper.listGroupKeyMd5ByPageFetchRows((pageNo - 1) * pageSize, pageSize);
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        try {
            Page<ConfigInfoWrapper> page = helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo,
                    pageSize, CONFIG_INFO_WRAPPER_ROW_MAPPER);
            
            return page.getPageItems();
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public String generateLikeArgument(String s) {
        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }
    
    @Override
    public ConfigInfoWrapper queryConfigInfo(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return this.jt.queryForObject(configInfoMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "type",
                                    "gmt_modified", "md5", "encrypted_data_key"),
                            Arrays.asList("data_id", "group_id", "tenant_id")), new Object[] {dataId, group, tenantTmp},
                    CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public boolean isExistTable(String tableName) {
        String sql = String.format("SELECT 1 FROM %s LIMIT 1", tableName);
        try {
            jt.queryForObject(sql, Integer.class);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
    
    @Override
    public List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
            final String appName, final List<Long> ids) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        Map<String, String> params = new HashMap<>(16);
        List<Object> paramList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(ids)) {
            paramList.addAll(ids);
        } else {
            paramList.add(tenantTmp);
            params.put(TENANT, TENANT);
            if (!StringUtils.isBlank(dataId)) {
                paramList.add(generateLikeArgument(dataId));
                params.put(DATA_ID, DATA_ID);
            }
            if (StringUtils.isNotBlank(group)) {
                paramList.add(group);
                params.put(GROUP, GROUP);
            }
            if (StringUtils.isNotBlank(appName)) {
                paramList.add(appName);
                params.put(APP_NAME, APP_NAME);
            }
        }
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.findAllConfigInfo4Export(ids, params);
        try {
            return this.jt.query(sql, paramList.toArray(), CONFIG_ALL_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
            Map<String, Object> configAdvanceInfo, Timestamp time, boolean notify, SameConfigPolicy policy)
            throws NacosException {
        int succCount = 0;
        int skipCount = 0;
        List<Map<String, String>> failData = null;
        List<Map<String, String>> skipData = null;
        
        for (int i = 0; i < configInfoList.size(); i++) {
            ConfigAllInfo configInfo = configInfoList.get(i);
            try {
                ParamUtils.checkParam(configInfo.getDataId(), configInfo.getGroup(), "datumId",
                        configInfo.getContent());
            } catch (NacosException e) {
                LogUtil.DEFAULT_LOG.error("data verification failed", e);
                throw e;
            }
            ConfigInfo configInfo2Save = new ConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), configInfo.getAppName(), configInfo.getContent());
            configInfo2Save.setEncryptedDataKey(
                    configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey());
            
            String type = configInfo.getType();
            if (StringUtils.isBlank(type)) {
                // simple judgment of file type based on suffix
                if (configInfo.getDataId().contains(SPOT)) {
                    String extName = configInfo.getDataId().substring(configInfo.getDataId().lastIndexOf(SPOT) + 1);
                    FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(extName);
                    type = fileTypeEnum.getFileType();
                } else {
                    type = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(null).getFileType();
                }
            }
            if (configAdvanceInfo == null) {
                configAdvanceInfo = new HashMap<>(16);
            }
            configAdvanceInfo.put("type", type);
            configAdvanceInfo.put("desc", configInfo.getDesc());
            try {
                addConfigInfo(srcIp, srcUser, configInfo2Save, time, configAdvanceInfo, notify);
                succCount++;
            } catch (DataIntegrityViolationException ive) {
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
                } else if (SameConfigPolicy.SKIP.equals(policy)) {
                    skipCount++;
                    if (skipData == null) {
                        skipData = new ArrayList<>();
                    }
                    Map<String, String> skipitem = new HashMap<>(2);
                    skipitem.put("dataId", configInfo2Save.getDataId());
                    skipitem.put("group", configInfo2Save.getGroup());
                    skipData.add(skipitem);
                } else if (SameConfigPolicy.OVERWRITE.equals(policy)) {
                    succCount++;
                    updateConfigInfo(configInfo2Save, srcIp, srcUser, time, configAdvanceInfo, notify);
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
    
    @Override
    public int tenantInfoCountByTenantId(String tenantId) {
        if (Objects.isNull(tenantId)) {
            throw new IllegalArgumentException("tenantId can not be null");
        }
        TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper.count(Arrays.asList("tenant_id"));
        Integer result = this.jt.queryForObject(sql, new String[] {tenantId}, Integer.class);
        if (result == null) {
            return 0;
        }
        return result.intValue();
    }
    
    @Override
    public List<ConfigInfoWrapper> queryConfigInfoByNamespace(String tenant) {
        if (Objects.isNull(tenant)) {
            throw new IllegalArgumentException("tenantId can not be null");
        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            return this.jt.query(
                    configInfoMapper.select(Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "type"),
                            Collections.singletonList("tenant_id")), new Object[] {tenantTmp},
                    CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return Collections.EMPTY_LIST;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
}
