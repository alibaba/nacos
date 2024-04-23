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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.model.event.DerbyImportEvent;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedPaginationHelperImpl;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
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
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ADVANCE_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ALL_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;
import static com.alibaba.nacos.persistence.repository.RowMapperManager.MAP_ROW_MAPPER;

/**
 * EmbeddedConfigInfoPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings({"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service("embeddedConfigInfoPersistServiceImpl")
public class EmbeddedConfigInfoPersistServiceImpl implements ConfigInfoPersistService {
    
    private static final String RESOURCE_CONFIG_INFO_ID = "config-info-id";
    
    private static final String RESOURCE_CONFIG_HISTORY_ID = "config-history-id";
    
    private static final String RESOURCE_CONFIG_TAG_RELATION_ID = "config-tag-relation-id";
    
    private static final String RESOURCE_APP_CONFIGDATA_RELATION_SUBS = "app-configdata-relation-subs";
    
    private static final String RESOURCE_CONFIG_BETA_ID = "config-beta-id";
    
    private static final String RESOURCE_NAMESPACE_ID = "namespace-id";
    
    private static final String RESOURCE_USER_ID = "user-id";
    
    private static final String RESOURCE_ROLE_ID = "role-id";
    
    private static final String RESOURCE_PERMISSIONS_ID = "permissions_id";
    
    private static final String DATA_ID = "dataId";
    
    private static final String GROUP = "group";
    
    private static final String APP_NAME = "appName";
    
    private static final String CONTENT = "content";
    
    private static final String TENANT = "tenant_id";
    
    public static final String SPOT = ".";
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    private final IdGeneratorManager idGeneratorManager;
    
    MapperManager mapperManager;
    
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    /**
     * The constructor sets the dependency injection order.
     *
     * @param databaseOperate    databaseOperate.
     * @param idGeneratorManager {@link IdGeneratorManager}
     */
    public EmbeddedConfigInfoPersistServiceImpl(DatabaseOperate databaseOperate, IdGeneratorManager idGeneratorManager,
            @Qualifier("embeddedHistoryConfigInfoPersistServiceImpl") HistoryConfigInfoPersistService historyConfigInfoPersistService) {
        this.databaseOperate = databaseOperate;
        this.idGeneratorManager = idGeneratorManager;
        idGeneratorManager.register(RESOURCE_CONFIG_INFO_ID, RESOURCE_CONFIG_HISTORY_ID,
                RESOURCE_CONFIG_TAG_RELATION_ID, RESOURCE_APP_CONFIGDATA_RELATION_SUBS, RESOURCE_CONFIG_BETA_ID,
                RESOURCE_NAMESPACE_ID, RESOURCE_USER_ID, RESOURCE_ROLE_ID, RESOURCE_PERMISSIONS_ID);
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
        NotifyCenter.registerToSharePublisher(DerbyImportEvent.class);
        
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<>(databaseOperate);
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
    public ConfigInfoStateWrapper findConfigInfoState(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        
        final String sql = configInfoMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "gmt_modified"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        return databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp},
                CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        
    }
    
    private ConfigOperateResult getConfigInfoOperateResult(String dataId, String group, String tenant) {
        ConfigInfoStateWrapper configInfo4 = this.findConfigInfoState(dataId, group, tenant);
        if (configInfo4 == null) {
            return new ConfigOperateResult(false);
        }
        return new ConfigOperateResult(configInfo4.getId(), configInfo4.getLastModified());
        
    }
    
    @Override
    public ConfigOperateResult addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Map<String, Object> configAdvanceInfo) {
        return addConfigInfo(srcIp, srcUser, configInfo, configAdvanceInfo, null);
    }
    
    private ConfigOperateResult addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Map<String, Object> configAdvanceInfo, BiConsumer<Boolean, Throwable> consumer) {
        
        try {
            final String tenantTmp =
                    StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
            configInfo.setTenant(tenantTmp);
            
            long configId = idGeneratorManager.nextId(RESOURCE_CONFIG_INFO_ID);
            long hisId = idGeneratorManager.nextId(RESOURCE_CONFIG_HISTORY_ID);
            
            addConfigInfoAtomic(configId, srcIp, srcUser, configInfo, configAdvanceInfo);
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
            
            addConfigTagsRelation(configId, configTags, configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            Timestamp now = new Timestamp(System.currentTimeMillis());
            
            historyConfigInfoPersistService.insertConfigHistoryAtomic(hisId, configInfo, srcIp, srcUser, now, "I");
            EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, now);
            databaseOperate.blockUpdate(consumer);
            return getConfigInfoOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo) {
        if (Objects.isNull(
                findConfigInfoState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()))) {
            return addConfigInfo(srcIp, srcUser, configInfo, configAdvanceInfo);
        } else {
            return updateConfigInfo(configInfo, srcIp, srcUser, configAdvanceInfo);
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateCas(String srcIp, String srcUser, ConfigInfo configInfo,
            Map<String, Object> configAdvanceInfo) {
        if (Objects.isNull(
                findConfigInfoState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()))) {
            return addConfigInfo(srcIp, srcUser, configInfo, configAdvanceInfo);
        } else {
            return updateConfigInfoCas(configInfo, srcIp, srcUser, configAdvanceInfo);
        }
    }
    
    @Override
    public long addConfigInfoAtomic(final long id, final String srcIp, final String srcUser,
            final ConfigInfo configInfo, Map<String, Object> configAdvanceInfo) {
        final String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        final String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE);
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        Timestamp time = new Timestamp(System.currentTimeMillis());
        
        final String sql = configInfoMapper.insert(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip",
                        "src_user", "gmt_create", "gmt_modified", "c_desc", "c_use", "effect", "type", "c_schema",
                        "encrypted_data_key"));
        final Object[] args = new Object[] {id, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp,
                configInfo.getContent(), md5Tmp, srcIp, srcUser, time, time, desc, use, effect, type, schema,
                encryptedDataKey};
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
        return id;
    }
    
    @Override
    public void addConfigTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
        final String sql = configTagsRelationMapper.insert(
                Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {configId, tagName, StringUtils.EMPTY, dataId, group, tenant};
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
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
        
        final BiConsumer<Boolean, Throwable> callFinally = (result, t) -> {
            if (t != null) {
                throw new NacosRuntimeException(0, t);
            }
        };
        
        for (int i = 0; i < configInfoList.size(); i++) {
            ConfigAllInfo configInfo = configInfoList.get(i);
            try {
                ParamUtils.checkParam(configInfo.getDataId(), configInfo.getGroup(), "datumId",
                        configInfo.getContent());
            } catch (Throwable e) {
                DEFAULT_LOG.error("data verification failed", e);
                throw e;
            }
            ConfigInfo configInfo2Save = new ConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), configInfo.getAppName(), configInfo.getContent());
            configInfo2Save.setEncryptedDataKey(
                    configInfo.getEncryptedDataKey() == null ? "" : configInfo.getEncryptedDataKey());
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
                ConfigInfoStateWrapper foundCfg = findConfigInfoState(configInfo2Save.getDataId(),
                        configInfo2Save.getGroup(), configInfo2Save.getTenant());
                if (foundCfg != null) {
                    throw new Throwable("DuplicateKeyException: config already exists, should be overridden");
                }
                addConfigInfo(srcIp, srcUser, configInfo2Save, configAdvanceInfo, callFinally);
                succCount++;
            } catch (Throwable e) {
                if (!StringUtils.contains(e.toString(), "DuplicateKeyException")) {
                    throw new NacosException(NacosException.SERVER_ERROR, e);
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
        final Timestamp time = new Timestamp(System.currentTimeMillis());
        ConfigInfo configInfo = findConfigInfo(dataId, group, tenant);
        if (Objects.nonNull(configInfo)) {
            try {
                String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
                
                removeConfigInfoAtomic(dataId, group, tenantTmp, srcIp, srcUser);
                removeTagByIdAtomic(configInfo.getId());
                historyConfigInfoPersistService.insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp,
                        srcUser, time, "D");
                
                EmbeddedStorageContextUtils.onDeleteConfigInfo(tenantTmp, group, dataId, srcIp, time);
                
                boolean result = databaseOperate.update(EmbeddedStorageContextHolder.getCurrentSqlContext());
                if (!result) {
                    throw new NacosConfigException("config deletion failed");
                }
            } finally {
                EmbeddedStorageContextHolder.cleanAllContext();
            }
        }
    }
    
    @Override
    public List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        ids.removeAll(Collections.singleton(null));
        final Timestamp time = new Timestamp(System.currentTimeMillis());
        try {
            String idsStr = StringUtils.join(ids, StringUtils.COMMA);
            List<ConfigInfo> configInfoList = findConfigInfosByIds(idsStr);
            if (CollectionUtils.isNotEmpty(configInfoList)) {
                removeConfigInfoByIdsAtomic(idsStr);
                for (ConfigInfo configInfo : configInfoList) {
                    removeTagByIdAtomic(configInfo.getId());
                    historyConfigInfoPersistService.insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp,
                            srcUser, time, "D");
                }
            }
            
            EmbeddedStorageContextUtils.onBatchDeleteConfigInfo(configInfoList);
            boolean result = databaseOperate.update(EmbeddedStorageContextHolder.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("Failed to config batch deletion");
            }
            
            return configInfoList;
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public void removeTagByIdAtomic(long id) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
        final String sql = configTagsRelationMapper.delete(Collections.singletonList("id"));
        final Object[] args = new Object[] {id};
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
    }
    
    @Override
    public void removeConfigInfoAtomic(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp};
        
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
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
        EmbeddedStorageContextHolder.addSqlContext(result.getSql(), result.getParamList().toArray());
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Map<String, Object> configAdvanceInfo) {
        try {
            ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            
            final String tenantTmp =
                    StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
            
            oldConfigInfo.setTenant(tenantTmp);
            
            String appNameTmp = oldConfigInfo.getAppName();
            // If the appName passed by the user is not empty, the appName of the user is persisted;
            // otherwise, the appName of db is used. Empty string is required to clear appName
            if (configInfo.getAppName() == null) {
                configInfo.setAppName(appNameTmp);
            }
            
            updateConfigInfoAtomic(configInfo, srcIp, srcUser, configAdvanceInfo);
            
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
            if (configTags != null) {
                // Delete all tags and recreate them
                removeTagByIdAtomic(oldConfigInfo.getId());
                addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
            }
            
            Timestamp time = new Timestamp(System.currentTimeMillis());
            
            historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp,
                    srcUser, time, "U");
            
            EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, time);
            databaseOperate.blockUpdate();
            return getConfigInfoOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfoCas(final ConfigInfo configInfo, final String srcIp,
            final String srcUser, final Map<String, Object> configAdvanceInfo) {
        try {
            ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            
            final String tenantTmp =
                    StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
            
            oldConfigInfo.setTenant(tenantTmp);
            
            String appNameTmp = oldConfigInfo.getAppName();
            // If the appName passed by the user is not empty, the appName of the user is persisted;
            // otherwise, the appName of db is used. Empty string is required to clear appName
            if (configInfo.getAppName() == null) {
                configInfo.setAppName(appNameTmp);
            }
            
            updateConfigInfoAtomicCas(configInfo, srcIp, srcUser, configAdvanceInfo);
            
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
            if (configTags != null) {
                // Delete all tags and recreate them
                removeTagByIdAtomic(oldConfigInfo.getId());
                addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
            }
            Timestamp time = new Timestamp(System.currentTimeMillis());
            
            historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp,
                    srcUser, time, "U");
            
            EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, time);
            boolean success = databaseOperate.blockUpdate();
            if (success) {
                return getConfigInfoOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            } else {
                return new ConfigOperateResult(false);
            }
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    private ConfigOperateResult updateConfigInfoAtomicCas(final ConfigInfo configInfo, final String srcIp,
            final String srcUser, Map<String, Object> configAdvanceInfo) {
        final String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        final String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        Timestamp time = new Timestamp(System.currentTimeMillis());
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.CONTENT, configInfo.getContent());
        context.putUpdateParameter(FieldConstant.MD5, md5Tmp);
        context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
        context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, time);
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
        
        EmbeddedStorageContextHolder.addSqlContext(Boolean.TRUE, mapperResult.getSql(), mapperResult.getParamList().toArray());
        return getConfigInfoOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
        
    }
    
    @Override
    public void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            Map<String, Object> configAdvanceInfo) {
        final String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        final String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.update(
                Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name", "c_desc", "c_use",
                        "effect", "type", "c_schema", "encrypted_data_key"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        Timestamp time = new Timestamp(System.currentTimeMillis());
        
        final Object[] args = new Object[] {configInfo.getContent(), md5Tmp, srcIp, srcUser, time, appNameTmp, desc,
                use, effect, type, schema, encryptedDataKey, configInfo.getDataId(), configInfo.getGroup(), tenantTmp};
        
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
    }
    
    @Override
    public long findConfigMaxId() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        MapperResult mapperResult = configInfoMapper.findConfigMaxId(null);
        return Optional.ofNullable(databaseOperate.queryOne(mapperResult.getSql(), Long.class)).orElse(0L);
    }
    
    @Override
    public ConfigInfo findConfigInfo(long id) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content"),
                Collections.singletonList("id"));
        return databaseOperate.queryOne(sql, new Object[] {id}, CONFIG_INFO_ROW_MAPPER);
    }
    
    @Override
    public ConfigInfoWrapper findConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "type",
                        "encrypted_data_key", "gmt_modified"), Arrays.asList("data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp};
        return databaseOperate.queryOne(sql, args, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
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
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        Page<ConfigInfo> page = helper.fetchPageLimit(sqlCount, sql, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        
        for (ConfigInfo configInfo : page.getPageItems()) {
            Pair<String, String> pair = EncryptionHandler.decryptHandler(configInfo.getDataId(),
                    configInfo.getEncryptedDataKey(), configInfo.getContent());
            configInfo.setContent(pair.getSecond());
        }
        
        return page;
    }
    
    @Override
    public int configInfoCount() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.count(null);
        Integer result = databaseOperate.queryOne(sql, Integer.class);
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
        Integer result = databaseOperate.queryOne(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result;
    }
    
    @Override
    public List<String> getTenantIdList(int page, int pageSize) {
        PaginationHelper<Map<String, Object>> helper = createPaginationHelper();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        MapperResult mapperResult = configInfoMapper.getTenantIdList(new MapperContext(from, pageSize));
        
        Page<Map<String, Object>> pageList = helper.fetchPageLimit(mapperResult.getSql(),
                mapperResult.getParamList().toArray(), page, pageSize, MAP_ROW_MAPPER);
        return pageList.getPageItems().stream().map(map -> String.valueOf(map.get("TENANT_ID")))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getGroupIdList(int page, int pageSize) {
        PaginationHelper<Map<String, Object>> helper = createPaginationHelper();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        MapperResult mapperResult = configInfoMapper.getGroupIdList(new MapperContext(from, pageSize));
        
        Page<Map<String, Object>> pageList = helper.fetchPageLimit(mapperResult.getSql(),
                mapperResult.getParamList().toArray(), page, pageSize, MAP_ROW_MAPPER);
        return pageList.getPageItems().stream().map(map -> String.valueOf(map.get("GROUP_ID")))
                .collect(Collectors.toList());
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
        return helper.fetchPageLimit(select.getSql(), select.getParamList().toArray(), 1, pageSize,
                CONFIG_INFO_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
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
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        Page<ConfigInfo> page = helper.fetchPageLimit(sqlCountRows, sqlFetchRows, pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        for (ConfigInfo configInfo : page.getPageItems()) {
            Pair<String, String> pair = EncryptionHandler.decryptHandler(configInfo.getDataId(),
                    configInfo.getEncryptedDataKey(), configInfo.getContent());
            configInfo.setContent(pair.getSecond());
        }
        return page;
        
    }
    
    @Override
    public List<ConfigInfoStateWrapper> findChangeConfig(final Timestamp startTime, long lastMaxId,
            final int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        
        MapperResult mapperResult = configInfoMapper.findChangeConfig(context);
        return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.CONFIG_TAGS_RELATION);
        String sql = configTagsRelationMapper.select(Collections.singletonList("tag_name"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        return databaseOperate.queryMany(sql, new Object[] {dataId, group, tenant}, String.class);
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
        return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                CONFIG_INFO_ROW_MAPPER);
        
    }
    
    @Override
    public ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        ConfigAdvanceInfo configAdvance = databaseOperate.queryOne(configInfoMapper.select(
                        Arrays.asList("gmt_create", "gmt_modified", "src_user", "src_ip", "c_desc", "c_use", "effect", "type",
                                "c_schema"), Arrays.asList("data_id", "group_id", "tenant_id")),
                new Object[] {dataId, group, tenantTmp}, CONFIG_ADVANCE_INFO_ROW_MAPPER);
        
        if (CollectionUtils.isNotEmpty(configTagList)) {
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
    }
    
    @Override
    public ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "gmt_create",
                        "gmt_modified", "src_user", "src_ip", "c_desc", "c_use", "effect", "type", "c_schema",
                        "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
        
        List<String> configTagList = selectTagByConfig(dataId, group, tenant);
        
        ConfigAllInfo configAdvance = databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp},
                CONFIG_ALL_INFO_ROW_MAPPER);
        
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
        return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                CONFIG_ALL_INFO_ROW_MAPPER);
    }
    
    @Override
    public List<ConfigInfoWrapper> queryConfigInfoByNamespace(String tenantId) {
        if (Objects.isNull(tenantId)) {
            throw new IllegalArgumentException("tenantId can not be null");
        }
        String tenantTmp = StringUtils.isBlank(tenantId) ? StringUtils.EMPTY : tenantId;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(
                Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "type", "gmt_modified"),
                Collections.singletonList("tenant_id"));
        return databaseOperate.queryMany(sql, new Object[] {tenantTmp}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
    }
    
}
