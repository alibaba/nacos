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

package com.alibaba.nacos.config.server.service.repository.extrnal;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.constant.Symbols;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.ParametersField;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.sql.ExternalStorageUtils;
import com.alibaba.nacos.config.server.utils.ConfigExtInfoUtil;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.extrnal.ExternalStoragePaginationHelperImpl;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigTagsRelationMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.Optional;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ADVANCE_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ALL_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER;

/**
 * ExternalConfigInfoPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalConfigInfoPersistServiceImpl")
public class ExternalConfigInfoPersistServiceImpl implements ConfigInfoPersistService {
    
    /**
     * constant variables.
     */
    public static final String SPOT = ".";
    
    private DataSourceService dataSourceService;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    MapperManager mapperManager;
    
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    public ExternalConfigInfoPersistServiceImpl(
            @Qualifier("externalHistoryConfigInfoPersistServiceImpl") HistoryConfigInfoPersistService historyConfigInfoPersistService) {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jt = dataSourceService.getJdbcTemplate();
        this.tjt = dataSourceService.getTransactionTemplate();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new ExternalStoragePaginationHelperImpl<>(jt);
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
    public ConfigOperateResult addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Map<String, Object> configAdvanceInfo) {
        return tjt.execute(status -> {
            try {
                long configId = addConfigInfoAtomic(-1, srcIp, srcUser, configInfo, configAdvanceInfo);
                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                addConfigTagsRelation(configId, configTags, configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
                Timestamp now = new Timestamp(System.currentTimeMillis());
                
                historyConfigInfoPersistService.insertConfigHistoryAtomic(0, configInfo, srcIp, srcUser, now, "I",
                        Constants.FORMAL, ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(configAdvanceInfo, srcUser));
                ConfigInfoStateWrapper configInfoCurrent = this.findConfigInfoState(configInfo.getDataId(),
                        configInfo.getGroup(), configInfo.getTenant());
                if (configInfoCurrent == null) {
                    return new ConfigOperateResult(false);
                }
                return new ConfigOperateResult(configInfoCurrent.getId(), configInfoCurrent.getLastModified());
                
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw e;
            }
        });
    }
    
    /**
     * insert or update config.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param configAdvanceInfo advance info
     * @return
     */
    public ConfigOperateResult insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo) {
        try {
            ConfigInfoStateWrapper configInfoState = findConfigInfoState(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            if (configInfoState == null) {
                return addConfigInfo(srcIp, srcUser, configInfo, configAdvanceInfo);
            } else {
                return updateConfigInfo(configInfo, srcIp, srcUser, configAdvanceInfo);
            }
            
        } catch (Exception exception) {
            LogUtil.FATAL_LOG.error("[db-error] try to update or add config failed, {}", exception.getMessage(),
                    exception);
            throw exception;
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo) {
        try {
            ConfigInfoStateWrapper configInfoState = findConfigInfoState(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            if (configInfoState == null) {
                return addConfigInfo(srcIp, srcUser, configInfo, configAdvanceInfo);
            } else {
                return updateConfigInfoCas(configInfo, srcIp, srcUser, configAdvanceInfo);
            }
            
        } catch (Exception exception) {
            LogUtil.FATAL_LOG.error("[db-error] try to update or add config failed, {}", exception.getMessage(),
                    exception);
            throw exception;
        }
    }
    
    @Override
    public long addConfigInfoAtomic(final long configId, final String srcIp, final String srcUser,
            final ConfigInfo configInfo, Map<String, Object> configAdvanceInfo) {
        
        KeyHolder keyHolder = ExternalStorageUtils.createKeyHolder();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        try {
            jt.update(
                    connection -> createPsForInsertConfigInfo(srcIp, srcUser, configInfo, configAdvanceInfo, connection,
                            configInfoMapper), keyHolder);
            Number nu = keyHolder.getKey();
            if (nu == null) {
                throw new IllegalArgumentException("insert config_info fail");
            }
            return nu.longValue();
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    PreparedStatement createPsForInsertConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo, Connection connection, ConfigInfoMapper configInfoMapper)
            throws SQLException {
        final String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        final String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);

        String insertSql = configInfoMapper.insert(
                Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip", "src_user",
                        "gmt_create@NOW()", "gmt_modified@NOW()", "c_desc", "c_use", "effect", "type", "c_schema",
                        "encrypted_data_key"));
        PreparedStatement ps = connection.prepareStatement(insertSql, configInfoMapper.getPrimaryKeyGeneratedKeys());
        ps.setString(1, configInfo.getDataId());
        ps.setString(2, configInfo.getGroup());
        ps.setString(3, tenantTmp);
        ps.setString(4, appNameTmp);
        ps.setString(5, configInfo.getContent());
        ps.setString(6, md5Tmp);
        ps.setString(7, srcIp);
        ps.setString(8, srcUser);
        ps.setString(9, desc);
        ps.setString(10, use);
        ps.setString(11, effect);
        ps.setString(12, type);
        ps.setString(13, schema);
        ps.setString(14, encryptedDataKey);
        return ps;
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
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
    public Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
            Map<String, Object> configAdvanceInfo, SameConfigPolicy policy) throws NacosException {
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
            boolean success;
            try {
                ConfigOperateResult configOperateResult = addConfigInfo(srcIp, srcUser, configInfo2Save,
                        configAdvanceInfo);
                success = configOperateResult.isSuccess();
            } catch (DataIntegrityViolationException ive) {
                success = false;
            }
            if (success) {
                succCount++;
            } else {
                // uniqueness constraint conflict or add config info fail.
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
                        skipCount++;
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
                    updateConfigInfo(configInfo2Save, srcIp, srcUser, configAdvanceInfo);
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
    public void removeConfigInfo(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser) {
        tjt.execute(new TransactionCallback<Boolean>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());
            
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    ConfigAllInfo oldConfigAllInfo = findConfigAllInfo(dataId, group, tenant);
                    if (oldConfigAllInfo != null) {
                        removeConfigInfoAtomic(dataId, group, tenant, srcIp, srcUser);
                        removeTagByIdAtomic(oldConfigAllInfo.getId());
                        historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo.getId(), oldConfigAllInfo,
                                srcIp, srcUser, time, "D", Constants.FORMAL, ConfigExtInfoUtil.getExtInfoFromAllInfo(oldConfigAllInfo));
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    @Override
    public List<ConfigAllInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        ids.removeAll(Collections.singleton(null));
        return tjt.execute(new TransactionCallback<List<ConfigAllInfo>>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());
            
            @Override
            public List<ConfigAllInfo> doInTransaction(TransactionStatus status) {
                try {
                    String idsStr = StringUtils.join(ids, StringUtils.COMMA);
                    List<ConfigAllInfo> oldConfigAllInfoList = findAllConfigInfo4Export(null, null, null, null, ids);
                    if (!CollectionUtils.isEmpty(oldConfigAllInfoList)) {
                        removeConfigInfoByIdsAtomic(idsStr);
                        for (ConfigAllInfo configAllInfo : oldConfigAllInfoList) {
                            removeTagByIdAtomic(configAllInfo.getId());
                            historyConfigInfoPersistService.insertConfigHistoryAtomic(configAllInfo.getId(),
                                    configAllInfo, srcIp, srcUser, time, "D", Constants.FORMAL,
                                    ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo));
                        }
                    }
                    return oldConfigAllInfoList;
                } catch (CannotGetJdbcConnectionException e) {
                    LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                    throw e;
                }
            }
        });
    }
    
    @Override
    public void removeTagByIdAtomic(long id) {
        try {
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
            jt.update(configTagsRelationMapper.delete(Collections.singletonList("id")), id);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public void removeConfigInfoByIdsAtomic(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return;
        }
        List<Long> paramList = new ArrayList<>();
        String[] idArr = ids.split(",");
        for (int i = 0; i < idArr.length; i++) {
            paramList.add(Long.parseLong(idArr[i]));
        }
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.IDS, paramList);
        MapperResult result = configInfoMapper.removeConfigInfoByIdsAtomic(context);
        try {
            jt.update(result.getSql(), result.getParamList().toArray());
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Map<String, Object> configAdvanceInfo) {
        return tjt.execute(status -> {
            try {
                ConfigAllInfo oldConfigAllInfo = findConfigAllInfo(configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
                if (oldConfigAllInfo == null) {
                    if (LogUtil.FATAL_LOG.isErrorEnabled()) {
                        LogUtil.FATAL_LOG.error("expected config info[dataid:{}, group:{}, tenent:{}] but not found.",
                                configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant());
                    }
                    return new ConfigOperateResult(false);
                }
                
                String appNameTmp = oldConfigAllInfo.getAppName();
                /*
                 If the appName passed by the user is not empty, use the persistent user's appName,
                 otherwise use db; when emptying appName, you need to pass an empty string
                 */
                if (configInfo.getAppName() == null) {
                    configInfo.setAppName(appNameTmp);
                }
                updateConfigInfoAtomic(configInfo, srcIp, srcUser, configAdvanceInfo);
                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                if (configTags != null) {
                    // delete all tags and then recreate
                    removeTagByIdAtomic(oldConfigAllInfo.getId());
                    addConfigTagsRelation(oldConfigAllInfo.getId(), configTags, configInfo.getDataId(),
                            configInfo.getGroup(), configInfo.getTenant());
                }
                
                Timestamp now = new Timestamp(System.currentTimeMillis());
                historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo.getId(), oldConfigAllInfo, srcIp, srcUser,
                        now, "U", Constants.FORMAL, ConfigExtInfoUtil.getExtInfoFromAllInfo(oldConfigAllInfo));
                return getConfigInfoOperateResult(configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw e;
            }
        });
    }
    
    private ConfigOperateResult getConfigInfoOperateResult(String dataId, String group, String tenant) {
        ConfigInfoStateWrapper configInfoLast = this.findConfigInfoState(dataId, group, tenant);
        if (configInfoLast == null) {
            return new ConfigOperateResult(false);
        }
        return new ConfigOperateResult(configInfoLast.getId(), configInfoLast.getLastModified());
        
    }
    
    @Override
    public ConfigOperateResult updateConfigInfoCas(final ConfigInfo configInfo, final String srcIp,
            final String srcUser, final Map<String, Object> configAdvanceInfo) {
        return tjt.execute(status -> {
            try {
                ConfigAllInfo oldAllConfigInfo = findConfigAllInfo(configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
                if (oldAllConfigInfo == null) {
                    if (LogUtil.FATAL_LOG.isErrorEnabled()) {
                        LogUtil.FATAL_LOG.error("expected config info[dataid:{}, group:{}, tenent:{}] but not found.",
                                configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant());
                    }
                    return new ConfigOperateResult(false);
                }
                String appNameTmp = oldAllConfigInfo.getAppName();
                /*
                 If the appName passed by the user is not empty, use the persistent user's appName,
                 otherwise use db; when emptying appName, you need to pass an empty string
                 */
                if (configInfo.getAppName() == null) {
                    configInfo.setAppName(appNameTmp);
                }
                int rows = updateConfigInfoAtomicCas(configInfo, srcIp, srcUser, configAdvanceInfo);
                if (rows < 1) {
                    return new ConfigOperateResult(false);
                }
                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                if (configTags != null) {
                    // delete all tags and then recreate
                    removeTagByIdAtomic(oldAllConfigInfo.getId());
                    addConfigTagsRelation(oldAllConfigInfo.getId(), configTags, configInfo.getDataId(),
                            configInfo.getGroup(), configInfo.getTenant());
                }
                Timestamp now = new Timestamp(System.currentTimeMillis());
                
                historyConfigInfoPersistService.insertConfigHistoryAtomic(oldAllConfigInfo.getId(), oldAllConfigInfo, srcIp, srcUser, now,
                        "U", Constants.FORMAL, ConfigExtInfoUtil.getExtInfoFromAllInfo(oldAllConfigInfo));
                ConfigInfoStateWrapper configInfoLast = this.findConfigInfoState(configInfo.getDataId(),
                        configInfo.getGroup(), configInfo.getTenant());
                if (configInfoLast == null) {
                    return new ConfigOperateResult(false);
                }
                return new ConfigOperateResult(configInfoLast.getId(), configInfoLast.getLastModified());
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw e;
            }
        });
    }
    
    private int updateConfigInfoAtomicCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            Map<String, Object> configAdvanceInfo) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE);
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
            
            MapperContext context = new MapperContext();
            context.putUpdateParameter(FieldConstant.CONTENT, configInfo.getContent());
            context.putUpdateParameter(FieldConstant.MD5, md5Tmp);
            context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
            context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
            context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
            context.putUpdateParameter(FieldConstant.C_DESC, desc);
            context.putUpdateParameter(FieldConstant.C_USE, use);
            context.putUpdateParameter(FieldConstant.EFFECT, effect);
            context.putUpdateParameter(FieldConstant.TYPE, type);
            context.putUpdateParameter(FieldConstant.C_SCHEMA, schema);
            context.putUpdateParameter(FieldConstant.ENCRYPTED_DATA_KEY, encryptedDataKey);
            context.putWhereParameter(FieldConstant.DATA_ID, configInfo.getDataId());
            context.putWhereParameter(FieldConstant.GROUP_ID, configInfo.getGroup());
            context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
            context.putWhereParameter(FieldConstant.MD5, configInfo.getMd5());
            
            MapperResult mapperResult = configInfoMapper.updateConfigInfoAtomicCas(context);
            return jt.update(mapperResult.getSql(), mapperResult.getParamList().toArray());
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            Map<String, Object> configAdvanceInfo) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
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
                            Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified@NOW()",
                                    "app_name", "c_desc", "c_use", "effect", "type", "c_schema", "encrypted_data_key"),
                            Arrays.asList("data_id", "group_id", "tenant_id")),
                    configInfo.getContent(), md5Tmp, srcIp, srcUser, appNameTmp, desc, use, effect, type, schema,
                    encryptedDataKey, configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public long findConfigMaxId() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        
        MapperResult mapperResult = configInfoMapper.findConfigMaxId(null);
        try {
            return jt.queryForObject(mapperResult.getSql(), Long.class);
        } catch (NullPointerException e) {
            return 0;
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
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
                                    "encrypted_data_key", "gmt_modified"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        MapperResult sql;
        MapperResult sqlCount;
        
        final MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
        if (StringUtils.isNotBlank(dataId)) {
            context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            context.putWhereParameter(FieldConstant.GROUP_ID, group);
            
        }
        if (StringUtils.isNotBlank(appName)) {
            context.putWhereParameter(FieldConstant.APP_NAME, appName);
        }
        if (!StringUtils.isBlank(content)) {
            context.putWhereParameter(FieldConstant.CONTENT, content);
        }
        context.setStartRow((pageNo - 1) * pageSize);
        context.setPageSize(pageSize);
        
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
            sqlCount = configTagsRelationMapper.findConfigInfo4PageCountRows(context);
            sql = configTagsRelationMapper.findConfigInfo4PageFetchRows(context);
        } else {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            
            sqlCount = configInfoMapper.findConfigInfo4PageCountRows(context);
            sql = configInfoMapper.findConfigInfo4PageFetchRows(context);
        }
        try {
            Page<ConfigInfo> page = helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
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
    public int configInfoCount() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.count(null);
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result;
    }
    
    @Override
    public int configInfoCount(String tenant) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.TENANT_ID, tenant);
        MapperResult mapperResult = configInfoMapper.configInfoLikeTenantCount(context);
        Integer result = jt.queryForObject(mapperResult.getSql(), mapperResult.getParamList().toArray(), Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result;
    }
    
    @Override
    public List<String> getTenantIdList(int page, int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        MapperResult mapperResult = configInfoMapper.getTenantIdList(new MapperContext(from, pageSize));
        return jt.queryForList(mapperResult.getSql(), mapperResult.getParamList().toArray(), String.class);
    }
    
    @Override
    public List<String> getGroupIdList(int page, int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        MapperResult mapperResult = configInfoMapper.getGroupIdList(new MapperContext(from, pageSize));
        return jt.queryForList(mapperResult.getSql(), mapperResult.getParamList().toArray(), String.class);
    }
    
    @Override
    public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize,
            boolean needContent) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        MapperContext context = new MapperContext(0, pageSize);
        context.putContextParameter(ContextConstant.NEED_CONTENT, String.valueOf(needContent));
        context.putWhereParameter(FieldConstant.ID, lastMaxId);
        MapperResult select = configInfoMapper.findAllConfigInfoFragment(context);
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        try {
            return helper.fetchPageLimit(select.getSql(), select.getParamList().toArray(), 1, pageSize,
                    CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String types = Optional.ofNullable(configAdvanceInfo).map(e -> (String) e.get(ParametersField.TYPES)).orElse(null);
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        MapperResult sqlCountRows;
        MapperResult sqlFetchRows;
        
        MapperContext context = new MapperContext((pageNo - 1) * pageSize, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, generateLikeArgument(tenantTmp));
        
        if (!StringUtils.isBlank(dataId)) {
            context.putWhereParameter(FieldConstant.DATA_ID, generateLikeArgument(dataId));
        }
        if (!StringUtils.isBlank(group)) {
            context.putWhereParameter(FieldConstant.GROUP_ID, generateLikeArgument(group));
        }
        if (!StringUtils.isBlank(appName)) {
            context.putWhereParameter(FieldConstant.APP_NAME, appName);
        }
        if (!StringUtils.isBlank(content)) {
            context.putWhereParameter(FieldConstant.CONTENT, generateLikeArgument(content));
        }
        if (StringUtils.isNotBlank(types)) {
            String[] typesArr = types.split(Symbols.COMMA);
            context.putWhereParameter(FieldConstant.TYPE, typesArr);
        }
        
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
            sqlCountRows = configTagsRelationMapper.findConfigInfoLike4PageCountRows(context);
            sqlFetchRows = configTagsRelationMapper.findConfigInfoLike4PageFetchRows(context);
        } else {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            sqlCountRows = configInfoMapper.findConfigInfoLike4PageCountRows(context);
            sqlFetchRows = configInfoMapper.findConfigInfoLike4PageFetchRows(context);
        }
        
        try {
            Page<ConfigInfo> page = helper.fetchPageLimit(sqlCountRows, sqlFetchRows, pageNo, pageSize,
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
    public List<ConfigInfoStateWrapper> findChangeConfig(final Timestamp startTime, long lastMaxId,
            final int pageSize) {
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            
            MapperContext context = new MapperContext();
            context.putWhereParameter(FieldConstant.START_TIME, startTime);
            context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
            context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
            
            MapperResult mapperResult = configInfoMapper.findChangeConfig(context);
            return jt.query(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                    CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
        String sql = configTagsRelationMapper.select(Collections.singletonList("tag_name"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        try {
            return jt.queryForList(sql, new Object[] {dataId, group, tenant}, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfo> findConfigInfosByIds(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return null;
        }
        List<Long> paramList = new ArrayList<>();
        String[] idArr = ids.split(",");
        for (int i = 0; i < idArr.length; i++) {
            paramList.add(Long.parseLong(idArr[i]));
        }
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.IDS, paramList);
        MapperResult mapperResult = configInfoMapper.findConfigInfosByIds(context);
        
        try {
            return this.jt.query(mapperResult.getSql(), mapperResult.getParamList().toArray(), CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfoStateWrapper findConfigInfoState(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            return this.jt.queryForObject(
                    "SELECT id,data_id,group_id,tenant_id,gmt_modified FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
                    new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
            final String appName, final List<Long> ids) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        MapperContext context = new MapperContext();
        if (!CollectionUtils.isEmpty(ids)) {
            context.putWhereParameter(FieldConstant.IDS, ids);
        } else {
            context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
            if (!StringUtils.isBlank(dataId)) {
                context.putWhereParameter(FieldConstant.DATA_ID, generateLikeArgument(dataId));
            }
            if (StringUtils.isNotBlank(group)) {
                context.putWhereParameter(FieldConstant.GROUP_ID, group);
            }
            if (StringUtils.isNotBlank(appName)) {
                context.putWhereParameter(FieldConstant.APP_NAME, appName);
            }
        }
        MapperResult mapperResult = configInfoMapper.findAllConfigInfo4Export(context);
        try {
            return this.jt.query(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                    CONFIG_ALL_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
}
