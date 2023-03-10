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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
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
import com.alibaba.nacos.config.server.model.SubInfo;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.model.event.DerbyImportEvent;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.MAP_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.TENANT_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;

/**
 * For Apache Derby.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings({"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
@Deprecated
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
    
    private MapperManager mapperManager;
    
    private static final String DATA_ID = "dataId";
    
    private static final String GROUP = "group";
    
    private static final String APP_NAME = "appName";
    
    private static final String CONTENT = "content";
    
    private static final String TENANT = "tenant_id";
    
    /**
     * The constructor sets the dependency injection order.
     *
     * @param databaseOperate    {@link EmbeddedStoragePersistServiceImpl}
     * @param idGeneratorManager {@link IdGeneratorManager}
     */
    public EmbeddedStoragePersistServiceImpl(DatabaseOperate databaseOperate, IdGeneratorManager idGeneratorManager) {
        this.databaseOperate = databaseOperate;
        this.idGeneratorManager = idGeneratorManager;
        Boolean isDataSourceLogEnable = EnvUtil
                .getProperty(Constants.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        NotifyCenter.registerToSharePublisher(DerbyImportEvent.class);
        mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    /**
     * init DataSourceService and IdGeneratorManager.
     */
    @PostConstruct
    public void init() {
        dataSourceService = DynamicDataSource.getInstance().getDataSource();
        idGeneratorManager
                .register(RESOURCE_CONFIG_INFO_ID, RESOURCE_CONFIG_HISTORY_ID, RESOURCE_CONFIG_TAG_RELATION_ID,
                        RESOURCE_APP_CONFIGDATA_RELATION_SUBS, RESOURCE_CONFIG_BETA_ID, RESOURCE_NAMESPACE_ID,
                        RESOURCE_USER_ID, RESOURCE_ROLE_ID, RESOURCE_PERMISSIONS_ID);
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
    
    // ----------------------- config_info table insert update delete
    
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
    
    public DatabaseOperate getDatabaseOperate() {
        return databaseOperate;
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<>(databaseOperate);
    }
    
    @Override
    public void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
        addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify, null);
    }
    
    private void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify,
            BiConsumer<Boolean, Throwable> consumer) {
        
        try {
            final String tenantTmp =
                    StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
            configInfo.setTenant(tenantTmp);
            
            long configId = idGeneratorManager.nextId(RESOURCE_CONFIG_INFO_ID);
            long hisId = idGeneratorManager.nextId(RESOURCE_CONFIG_HISTORY_ID);
            
            addConfigInfoAtomic(configId, srcIp, srcUser, configInfo, time, configAdvanceInfo);
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
            
            addConfigTagsRelation(configId, configTags, configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            insertConfigHistoryAtomic(hisId, configInfo, srcIp, srcUser, time, "I");
            EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, time);
            databaseOperate.blockUpdate(consumer);
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String encryptedDataKey = StringUtils.isBlank(configInfo.getEncryptedDataKey()) ? StringUtils.EMPTY
                : configInfo.getEncryptedDataKey();
        
        configInfo.setTenant(tenantTmp);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            final String sql = configInfoBetaMapper.insert(Arrays
                    .asList("data_id", "group_id", "tenant_id", "app_name", "content", "md5", "beta_ips", "src_ip",
                            "src_user", "gmt_create", "gmt_modified", "encrypted_data_key"));
            final Object[] args = new Object[] {configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp,
                    configInfo.getContent(), md5, betaIps, srcIp, srcUser, time, time, encryptedDataKey};
            
            EmbeddedStorageContextUtils.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
            
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        
        configInfo.setTenant(tenantTmp);
        
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            final String sql = configInfoTagMapper.insert(Arrays
                    .asList("data_id", "group_id", "tenant_id", "tag_id", "app_name", "content", "md5", "src_ip",
                            "src_user", "gmt_create", "gmt_modified"));
            final Object[] args = new Object[] {configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp,
                    appNameTmp, configInfo.getContent(), md5, srcIp, srcUser, time, time};
            
            EmbeddedStorageContextUtils.onModifyConfigTagInfo(configInfo, tagTmp, srcIp, time);
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
            
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
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
            
            updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);
            
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
            if (configTags != null) {
                // Delete all tags and recreate them
                removeTagByIdAtomic(oldConfigInfo.getId());
                addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
            }
            
            insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
            
            EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, time);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public boolean updateConfigInfoCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
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
            
            updateConfigInfoAtomicCas(configInfo, srcIp, srcUser, time, configAdvanceInfo);
            
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
            if (configTags != null) {
                // Delete all tags and recreate them
                removeTagByIdAtomic(oldConfigInfo.getId());
                addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
            }
            
            insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
            
            EmbeddedStorageContextUtils.onModifyConfigInfo(configInfo, srcIp, time);
            return databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String encryptedDataKey = StringUtils.isBlank(configInfo.getEncryptedDataKey()) ? StringUtils.EMPTY
                : configInfo.getEncryptedDataKey();
        
        configInfo.setTenant(tenantTmp);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            final String sql = configInfoBetaMapper.update(Arrays
                    .asList("content", "md5", "beta_ips", "src_ip", "src_user", "gmt_modified", "app_name",
                            "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
            
            final Object[] args = new Object[] {configInfo.getContent(), md5, betaIps, srcIp, srcUser, time, appNameTmp,
                    encryptedDataKey, configInfo.getDataId(), configInfo.getGroup(), tenantTmp};
            
            EmbeddedStorageContextUtils.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
            
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public boolean updateConfigInfo4BetaCas(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        
        configInfo.setTenant(tenantTmp);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
    
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            final String sql = configInfoBetaMapper.updateConfigInfo4BetaCas();
            
            final Object[] args = new Object[] {configInfo.getContent(), md5, betaIps, srcIp, srcUser, time, appNameTmp,
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, configInfo.getMd5()};
            
            EmbeddedStorageContextUtils.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
            
            return databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        
        configInfo.setTenant(tenantTmp);
        
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
    
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            final String sql = configInfoTagMapper
                    .update(Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "tag_id"));
            final Object[] args = new Object[] {configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp,
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp};
            
            EmbeddedStorageContextUtils.onModifyConfigTagInfo(configInfo, tagTmp, srcIp, time);
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
            
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public boolean updateConfigInfo4TagCas(ConfigInfo configInfo, String tag, String srcIp, String srcUser,
            Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        
        configInfo.setTenant(tenantTmp);
        
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            final String sql = configInfoTagMapper.updateConfigInfo4TagCas();
            final Object[] args = new Object[] {configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp,
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp, configInfo.getMd5()};
            
            EmbeddedStorageContextUtils.onModifyConfigTagInfo(configInfo, tagTmp, srcIp, time);
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
            
            return databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        if (findConfigInfo4Beta(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()) == null) {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        } else {
            updateConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        }
    }
    
    @Override
    public boolean insertOrUpdateBetaCas(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        if (findConfigInfo4Beta(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()) == null) {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
            return true;
        } else {
            return updateConfigInfo4BetaCas(configInfo, betaIps, srcIp, null, time, notify);
        }
        
    }
    
    @Override
    public void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        if (findConfigInfo4Tag(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), tag) == null) {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        } else {
            updateConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        }
    }
    
    @Override
    public boolean insertOrUpdateTagCas(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        if (findConfigInfo4Tag(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), tag) == null) {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
            return true;
        } else {
            return updateConfigInfo4TagCas(configInfo, tag, srcIp, null, time, notify);
        }
    }
    
    @Override
    public void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
            final String sql = configInfoMapper
                    .update(Arrays.asList("md5"), Arrays.asList("data_id", "group_id", "tenant_id", "gmt_modified"));
            final Object[] args = new Object[] {md5, dataId, group, tenantTmp, lastTime};
            
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
            
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("Failed to config the MD5 modification");
            }
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
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
        if (Objects.isNull(findConfigInfo(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()))) {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
        } else {
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
        if (Objects.isNull(findConfigInfo(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()))) {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
            return true;
        } else {
            return updateConfigInfoCas(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
        }
    }
    
    private boolean isAlreadyExist(SubInfo subInfo) {
        final String sql = "SELECT * FROM app_configdata_relation_subs WHERE dara_id=? AND group_id=? AND app_name=?";
        Map obj = databaseOperate
                .queryOne(sql, new Object[] {subInfo.getDataId(), subInfo.getGroup(), subInfo.getAppName()}, Map.class);
        return obj != null;
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
                insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");
                
                EmbeddedStorageContextUtils.onDeleteConfigInfo(tenantTmp, group, dataId, srcIp, time);
                
                boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
                if (!result) {
                    throw new NacosConfigException("config deletion failed");
                }
            } finally {
                EmbeddedStorageContextUtils.cleanAllContext();
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
                    insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");
                }
            }
            
            EmbeddedStorageContextUtils.onBatchDeleteConfigInfo(configInfoList);
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("Failed to config batch deletion");
            }
            
            return configInfoList;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfo configInfo = findConfigInfo4Beta(dataId, group, tenant);
        if (configInfo != null) {
            try {
                ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                        TableConstant.CONFIG_INFO_BETA);
                final String sql = configInfoBetaMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
                final Object[] args = new Object[] {dataId, group, tenantTmp};
                
                EmbeddedStorageContextUtils
                        .onDeleteConfigBetaInfo(tenantTmp, group, dataId, System.currentTimeMillis());
                EmbeddedStorageContextUtils.addSqlContext(sql, args);
                
                boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
                if (!result) {
                    throw new NacosConfigException("[Tag] Configuration deletion failed");
                }
            } finally {
                EmbeddedStorageContextUtils.cleanAllContext();
            }
            
        }
    }
    
    @Override
    public boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
            String appName, final String content) {
        String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String contentTmp = StringUtils.isBlank(content) ? StringUtils.EMPTY : content;
        final Timestamp now = new Timestamp(System.currentTimeMillis());
    
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String select = configInfoAggrMapper.select(Collections.singletonList("content"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        final String insert = configInfoAggrMapper.insert(Arrays
                .asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content", "gmt_modified"));
        final String update = configInfoAggrMapper.update(Arrays.asList("content", "gmt_modified"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        String dbContent = databaseOperate
                .queryOne(select, new Object[] {dataId, group, tenantTmp, datumId}, String.class);
        
        if (Objects.isNull(dbContent)) {
            final Object[] args = new Object[] {dataId, group, tenantTmp, datumId, appNameTmp, contentTmp, now};
            EmbeddedStorageContextUtils.addSqlContext(insert, args);
        } else if (!dbContent.equals(content)) {
            final Object[] args = new Object[] {contentTmp, now, dataId, group, tenantTmp, datumId};
            EmbeddedStorageContextUtils.addSqlContext(update, args);
        }
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[Merge] Configuration release failed");
            }
            return true;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant,
            final String datumId) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
    
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp, datumId};
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[aggregation with single] Configuration deletion failed");
            }
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
    
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp};
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[aggregation with all] Configuration deletion failed");
            }
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
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
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[aggregation] Failed to configure batch deletion");
            }
            return true;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.HIS_CONFIG_INFO);
        String sql = historyConfigInfoMapper.removeConfigHistory();
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        helper.updateLimit(sql, new Object[] {startTime, limitSize});
    }
    
    @Override
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        HistoryConfigInfoMapper historyConfigInfoMapper =  mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.HIS_CONFIG_INFO);
        String sql = historyConfigInfoMapper.findConfigHistoryCountByTime();
        Integer result = databaseOperate.queryOne(sql, new Object[] {startTime}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result;
    }
    
    @Override
    public long findConfigMaxId() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.findConfigMaxId();
        return Optional.ofNullable(databaseOperate.queryOne(sql, Long.class)).orElse(0L);
    }
    
    @Override
    public boolean batchPublishAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        try {
            Boolean isPublishOk = false;
            for (Entry<String, String> entry : datumMap.entrySet()) {
                addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName, entry.getValue());
            }
            
            isPublishOk = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            
            if (isPublishOk == null) {
                return false;
            }
            return isPublishOk;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public boolean replaceAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        Boolean isReplaceOk = false;
        String appNameTmp = appName == null ? "" : appName;
        
        removeAggrConfigInfo(dataId, group, tenant);
        
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.insert(Arrays
                .asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content", "gmt_modified"));
        for (Entry<String, String> datumEntry : datumMap.entrySet()) {
            final Object[] args = new Object[] {dataId, group, tenantTmp, datumEntry.getKey(), appNameTmp,
                    datumEntry.getValue(), new Timestamp(System.currentTimeMillis())};
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
        }
        try {
            isReplaceOk = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            
            if (isReplaceOk == null) {
                return false;
            }
            return isReplaceOk;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
        
    }
    
    @Override
    public List<ConfigInfo> findAllDataIdAndGroup() {
        ConfigInfoMapper configInfoMapper =  mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.findAllDataIdAndGroup();
        return databaseOperate.queryMany(sql, EMPTY_ARRAY, CONFIG_INFO_ROW_MAPPER);
    }
    
    @Override
    public ConfigInfoBetaWrapper findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        final String sql = configInfoBetaMapper.select(Arrays
                .asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "beta_ips",
                        "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
        
        return databaseOperate
                .queryOne(sql, new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public ConfigInfoTagWrapper findConfigInfo4Tag(final String dataId, final String group, final String tenant,
            final String tag) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_TAG);
        final String sql = configInfoTagMapper
                .select(Arrays.asList("id", "data_id", "group_id", "tenant_id", "tag_id", "app_name", "content"),
                        Arrays.asList("data_id", "group_id", "tenant_id", "tag_id"));
        
        return databaseOperate
                .queryOne(sql, new Object[] {dataId, group, tenantTmp, tagTmp}, CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
    }
    
    @Override
    public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(Arrays.asList("id", "data_id", "group_id", "content"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        
        return databaseOperate
                .queryOne(sql, new Object[] {dataId, group, StringUtils.EMPTY}, CONFIG_INFO_BASE_ROW_MAPPER);
    }
    
    @Override
    public ConfigInfo findConfigInfo(long id) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper
                .select(Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content"),
                        Collections.singletonList("id"));
        return databaseOperate.queryOne(sql, new Object[] {id}, CONFIG_INFO_ROW_MAPPER);
    }
    
    @Override
    public ConfigInfoWrapper findConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(Arrays
                .asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "type",
                        "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp};
        return databaseOperate.queryOne(sql, args, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
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
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION);
            sqlCount = configTagsRelationMapper.findConfigInfo4PageCountRows(paramsMap, tagArr.length);
            sql = configTagsRelationMapper
                    .findConfigInfo4PageFetchRows(paramsMap, tagArr.length, startRow, pageSize);
        } else {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
            sqlCount = configInfoMapper.findConfigInfo4PageCountRows(paramsMap);
            sql = configInfoMapper.findConfigInfo4PageFetchRows(paramsMap, startRow, pageSize);
        }
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        Page<ConfigInfo> page = helper
                .fetchPage(sqlCount, sql, paramList.toArray(), pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        
        for (ConfigInfo configInfo : page.getPageItems()) {
            Pair<String, String> pair = EncryptionHandler
                    .decryptHandler(configInfo.getDataId(), configInfo.getEncryptedDataKey(), configInfo.getContent());
            configInfo.setContent(pair.getSecond());
        }
        
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByApp(final int pageNo, final int pageSize, final String tenant,
            final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        return helper.fetchPage(configInfoMapper.findConfigInfoByAppCountRows(),
                configInfoMapper.findConfigInfoByAppFetchRows(startRow, pageSize),
                new Object[] {generateLikeArgument(tenantTmp), appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo, final int pageSize, final String group) {
        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        return helper.fetchPage(configInfoMapper.count(Arrays.asList("group_id", "tenant_id")),
                configInfoMapper.findConfigInfoBaseByGroupFetchRows(startRow, pageSize),
                new Object[] {group, StringUtils.EMPTY}, pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
    }
    
    @Override
    public int configInfoCount() {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.count(null);
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result;
    }
    
    @Override
    public int configInfoCount(String tenant) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.configInfoLikeTenantCount();
        Integer result = databaseOperate.queryOne(sql, new Object[] {tenant}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result;
    }
    
    @Override
    public int configInfoBetaCount() {
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_BETA);
        String sql = configInfoBetaMapper.count(null);
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result;
    }
    
    @Override
    public int configInfoTagCount() {
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_TAG);
        String sql = configInfoTagMapper.count(null);
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result;
    }
    
    @Override
    public List<String> getTenantIdList(int page, int pageSize) {
        PaginationHelper<Map<String, Object>> helper = createPaginationHelper();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        String sql = configInfoMapper.getTenantIdList(from, pageSize);
        
        Page<Map<String, Object>> pageList = helper
                .fetchPageLimit(sql, new Object[] {}, page, pageSize, MAP_ROW_MAPPER);
        return pageList.getPageItems().stream().map(map -> String.valueOf(map.get("TENANT_ID")))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getGroupIdList(int page, int pageSize) {
        PaginationHelper<Map<String, Object>> helper = createPaginationHelper();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        int from = (page - 1) * pageSize;
        String sql = configInfoMapper.getGroupIdList(from, pageSize);
        
        Page<Map<String, Object>> pageList = helper
                .fetchPageLimit(sql, new Object[] {}, page, pageSize, MAP_ROW_MAPPER);
        return pageList.getPageItems().stream().map(map -> String.valueOf(map.get("GROUP_ID")))
                .collect(Collectors.toList());
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        Integer result = databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result;
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds, boolean isIn) {
        if (datumIds == null || datumIds.isEmpty()) {
            return 0;
        }
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.aggrConfigInfoCount(datumIds.size(), isIn);
        
        List<Object> objectList = com.alibaba.nacos.common.utils.CollectionUtils.list(dataId, group, tenantTmp);
        objectList.addAll(datumIds);
        
        Integer result = databaseOperate.queryOne(sql, objectList.toArray(), Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result;
    }
    
    @Override
    public Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.count(null);
        String sqlFetchRows = configInfoMapper.findAllConfigInfoFetchRows(startRow, pageSize);
        
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        return helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
                new Object[] {generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String select = configInfoMapper.findAllConfigKey((pageNo - 1) * pageSize, pageSize);
        
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
        
        List<ConfigKey> result = databaseOperate
                .queryMany(select, new Object[] {generateLikeArgument(tenantTmp)},
                        // new Object[0],
                        CONFIG_KEY_ROW_MAPPER);
        
        for (ConfigKey item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }
    
    @Override
    public Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.count(null);
        String sqlFetchRows = configInfoMapper.findAllConfigInfoBaseFetchRows(startRow, pageSize);
        
        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
        return helper
                .fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {(pageNo - 1) * pageSize, pageSize}, pageNo,
                        pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String select = configInfoMapper.findAllConfigInfoFragment(0, pageSize);
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        return helper.fetchPageLimit(select, new Object[] {lastMaxId}, 1, pageSize,
                CONFIG_INFO_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_BETA);
        String sqlCountRows = configInfoBetaMapper.count(null);
        String sqlFetchRows = configInfoBetaMapper.findAllConfigInfoBetaForDumpAllFetchRows(startRow, pageSize);
        PaginationHelper<ConfigInfoBetaWrapper> helper = createPaginationHelper();
        return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo, pageSize,
                CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_TAG);
        String sqlCountRows = configInfoTagMapper.count(null);
        String sqlFetchRows = configInfoTagMapper.findAllConfigInfoTagForDumpAllFetchRows(startRow, pageSize);
        
        PaginationHelper<ConfigInfoTagWrapper> helper = createPaginationHelper();
        return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo, pageSize,
                CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final ConfigKey[] configKeys,
            final boolean blacklist) {
        String sqlCountRows = "SELECT count(*) FROM config_info WHERE ";
        String sqlFetchRows = "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE ";
        StringBuilder where = new StringBuilder(" 1=1 ");
        // White list, please synchronize the condition is empty, there is no qualified configuration
        if (configKeys.length == 0 && !blacklist) {
            Page<ConfigInfo> page = new Page<>();
            page.setTotalCount(0);
            return page;
        }
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
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        return helper
                .fetchPage(sqlCountRows + where.toString(), sqlFetchRows + where.toString(), params.toArray(), pageNo,
                        pageSize, CONFIG_INFO_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
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
            ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION);
            sqlCountRows = configTagsRelationMapper.findConfigInfoLike4PageCountRows(paramsMap, tagArr.length);
            sqlFetchRows = configTagsRelationMapper
                    .findConfigInfoLike4PageFetchRows(paramsMap, tagArr.length, startRow, pageSize);
        } else {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
            sqlCountRows = configInfoMapper.findConfigInfoLike4PageCountRows(paramsMap);
            sqlFetchRows = configInfoMapper.findConfigInfoLike4PageFetchRows(paramsMap, startRow, pageSize);
        }
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        Page<ConfigInfo> page = helper
                .fetchPage(sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        for (ConfigInfo configInfo : page.getPageItems()) {
            Pair<String, String> pair = EncryptionHandler
                    .decryptHandler(configInfo.getDataId(), configInfo.getEncryptedDataKey(), configInfo.getContent());
            configInfo.setContent(pair.getSecond());
        }
        return page;
        
    }
    
    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo, final int pageSize, final String dataId,
            final String group, final String content) throws IOException {
        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
            throw new IOException("invalid param");
        }
        
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
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.findConfigInfoBaseLikeCountRows(paramsMap);
        String sqlFetchRows = configInfoMapper.findConfigInfoBaseLikeFetchRows(paramsMap, startRow, pageSize);
        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
        return helper
                .fetchPage(sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
        
    }
    
    @Override
    public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper
                .select(Arrays.asList("id", "data_id", "group_id", "tenant_id", "datum_id", "app_name", "content"),
                        Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        return databaseOperate
                .queryOne(sql, new Object[] {dataId, group, tenantTmp, datumId}, CONFIG_INFO_AGGR_ROW_MAPPER);
        
    }
    
    @Override
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findConfigInfoAggrIsOrdered();
        
        return databaseOperate.queryMany(sql, new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_AGGR_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
            final int pageSize) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_AGGR);
        final int startRow = (pageNo - 1) * pageSize;
        String sqlCountRows = configInfoAggrMapper
                .select(Arrays.asList("count(*)"), Arrays.asList("data_id", "group_id", "tenant_id"));
        String sqlFetchRows = configInfoAggrMapper.findConfigInfoAggrByPageFetchRows(startRow, pageSize);
        
        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
        return helper.fetchPageLimit(sqlCountRows, new Object[] {dataId, group, tenantTmp}, sqlFetchRows,
                new Object[] {dataId, group, tenantTmp}, pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
            boolean blacklist) {
        
        String sqlCountRows = "SELECT count(*) FROM config_info_aggr WHERE ";
        String sqlFetchRows = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE ";
        StringBuilder where = new StringBuilder(" 1=1 ");
        // White list, please synchronize the condition is empty, there is no qualified configuration
        if (configKeys.length == 0 && blacklist == false) {
            Page<ConfigInfoAggr> page = new Page<>();
            page.setTotalCount(0);
            return page;
        }
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
        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
        return helper
                .fetchPage(sqlCountRows + where.toString(), sqlFetchRows + where.toString(), params.toArray(), pageNo,
                        pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
        
    }
    
    @Override
    public List<ConfigInfoChanged> findAllAggrGroup() {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findAllAggrGroupByDistinct();
        
        return databaseOperate.queryMany(sql, EMPTY_ARRAY, CONFIG_INFO_CHANGED_ROW_MAPPER);
        
    }
    
    @Override
    public List<String> findDatumIdByContent(String dataId, String groupId, String content) {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper
                .select(Collections.singletonList("datum_id"), Arrays.asList("data_id", "group_id", "content"));
        return databaseOperate.queryMany(sql, new Object[] {dataId, groupId, content}, String.class);
        
    }
    
    @Override
    public List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime, final Timestamp endTime) {
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        List<Map<String, Object>> list = databaseOperate
                .queryMany(configInfoMapper.findChangeConfig(), new Object[] {startTime, endTime});
        return convertChangeConfig(list);
        
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
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.findChangeConfigCountRows(paramsMap, startTime, endTime);
        String sqlFetchRows = configInfoMapper
                .findChangeConfigFetchRows(paramsMap, startTime, endTime, startRow, pageSize, lastMaxId);
        
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        return helper.fetchPage(sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize, lastMaxId,
                CONFIG_INFO_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public List<ConfigInfo> findDeletedConfig(final Timestamp startTime, final Timestamp endTime) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.HIS_CONFIG_INFO);
        List<Map<String, Object>> list = databaseOperate
                .queryMany(historyConfigInfoMapper.findDeletedConfig(), new Object[] {startTime, endTime});
        return convertDeletedConfig(list);
        
    }
    
    @Override
    public long addConfigInfoAtomic(final long id, final String srcIp, final String srcUser,
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
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.insert(Arrays
                .asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip", "src_user",
                        "gmt_create", "gmt_modified", "c_desc", "c_use", "effect", "type", "c_schema",
                        "encrypted_data_key"));
        final Object[] args = new Object[] {id, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp,
                configInfo.getContent(), md5Tmp, srcIp, srcUser, time, time, desc, use, effect, type, schema,
                encryptedDataKey};
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        return id;
    }
    
    @Override
    public void addConfigTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_TAGS_RELATION);
        final String sql = configTagsRelationMapper.insert(
                Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {configId, tagName, StringUtils.EMPTY, dataId, group, tenant};
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
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
    public void removeTagByIdAtomic(long id) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_TAGS_RELATION);
        final String sql = configTagsRelationMapper.delete(Arrays.asList("id"));
        final Object[] args = new Object[] {id};
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
    }
    
    @Override
    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        ConfigTagsRelationMapper configTagsRelationMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_TAGS_RELATION);
        String sql = configTagsRelationMapper.select(Arrays.asList("tag_name"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        return databaseOperate.queryMany(sql, new Object[] {dataId, group, tenant}, String.class);
    }
    
    @Override
    public void removeConfigInfoAtomic(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp};
        
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
    }
    
    @Override
    public void removeConfigInfoByIdsAtomic(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return;
        }
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (int i = 0; i < tagArr.length; i++) {
            paramList.add(Long.parseLong(tagArr[i]));
        }
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.removeConfigInfoByIdsAtomic(paramList.size());
        EmbeddedStorageContextUtils.addSqlContext(sql.toString(), paramList.toArray());
    }
    
    @Override
    public void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
            final String srcIp, final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag;
        
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_TAG);
        final String sql = configInfoTagMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "tag_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp, tagTmp};
        
        EmbeddedStorageContextUtils.onDeleteConfigTagInfo(tenantTmp, group, dataId, tagTmp, srcIp);
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        try {
            databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, Map<String, Object> configAdvanceInfo) {
        final String appNameTmp =
                StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        final String tenantTmp =
                StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.update(Arrays
                .asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name", "c_desc", "c_use", "effect",
                        "type", "c_schema", "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {configInfo.getContent(), md5Tmp, srcIp, srcUser, time, appNameTmp, desc,
                use, effect, type, schema, encryptedDataKey, configInfo.getDataId(), configInfo.getGroup(), tenantTmp};
        
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
    }
    
    private void updateConfigInfoAtomicCas(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, Map<String, Object> configAdvanceInfo) {
        final String appNameTmp =
                StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        final String tenantTmp =
                StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.updateConfigInfoAtomicCas();
        final Object[] args = new Object[] {configInfo.getContent(), md5Tmp, srcIp, srcUser, time, appNameTmp, desc,
                use, effect, type, schema, configInfo.getDataId(), configInfo.getGroup(), tenantTmp,
                configInfo.getMd5()};
        
        EmbeddedStorageContextUtils.addSqlContext(true, sql, args);
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
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        return databaseOperate.queryMany(configInfoMapper.findConfigInfosByIds(tagArr.length), paramList.toArray(),
                CONFIG_INFO_ROW_MAPPER);
        
    }
    
    @Override
    public ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        ConfigAdvanceInfo configAdvance = databaseOperate.queryOne(configInfoMapper.select(Arrays
                        .asList("gmt_create", "gmt_modified", "src_user", "src_ip", "c_desc", "c_use", "effect", "type",
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
        
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(Arrays
                .asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "gmt_create",
                        "gmt_modified", "src_user", "src_ip", "c_desc", "c_use", "effect", "type", "c_schema",
                        "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
        
        List<String> configTagList = selectTagByConfig(dataId, group, tenant);
        
        ConfigAllInfo configAdvance = databaseOperate
                .queryOne(sql, new Object[] {dataId, group, tenantTmp}, CONFIG_ALL_INFO_ROW_MAPPER);
        
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
    public void insertConfigHistoryAtomic(long configHistoryId, ConfigInfo configInfo, String srcIp, String srcUser,
            final Timestamp time, String ops) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String encryptedDataKey = StringUtils.isBlank(configInfo.getEncryptedDataKey()) ? StringUtils.EMPTY
                : configInfo.getEncryptedDataKey();
        
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.HIS_CONFIG_INFO);
        final String sql = historyConfigInfoMapper.insert(Arrays
                .asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip", "src_user",
                        "gmt_modified", "op_type", "encrypted_data_key"));
        final Object[] args = new Object[] {configHistoryId, configInfo.getDataId(), configInfo.getGroup(), tenantTmp,
                appNameTmp, configInfo.getContent(), md5Tmp, srcIp, srcUser, time, ops, encryptedDataKey};
        
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
    }
    
    @Override
    public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
            int pageSize) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.HIS_CONFIG_INFO);
        String sqlCountRows = historyConfigInfoMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        String sqlFetchRows = historyConfigInfoMapper.findConfigHistoryFetchRows();
        
        PaginationHelper<ConfigHistoryInfo> helper = createPaginationHelper();
        return helper.fetchPage(sqlCountRows, sqlFetchRows, new Object[] {dataId, group, tenantTmp}, pageNo, pageSize,
                HISTORY_LIST_ROW_MAPPER);
    }
    
    @Override
    public ConfigHistoryInfo detailConfigHistory(Long nid) {
        HistoryConfigInfoMapper historyConfigInfoMapper =  mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.HIS_CONFIG_INFO);
        String sqlFetchRows = historyConfigInfoMapper.select(Arrays
                        .asList("nid", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_user", "src_ip",
                                "op_type", "gmt_create", "gmt_modified", "encrypted_data_key"),
                Collections.singletonList("nid"));
        return databaseOperate.queryOne(sqlFetchRows, new Object[] {nid}, HISTORY_DETAIL_ROW_MAPPER);
    }
    
    @Override
    public ConfigHistoryInfo detailPreviousConfigHistory(Long id) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.HIS_CONFIG_INFO);
        String sqlFetchRows = historyConfigInfoMapper.detailPreviousConfigHistory();
        return databaseOperate.queryOne(sqlFetchRows, new Object[] {id}, HISTORY_DETAIL_ROW_MAPPER);
    }
    
    @Override
    public void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
            String createResoure, final long time) {
        
        TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        final String sql = tenantInfoMapper.insert(Arrays.asList("kp", "tenant_id", "tenant_name", "tenant_desc", "create_source", "gmt_create", "gmt_modified"));
        final Object[] args = new Object[] {kp, tenantId, tenantName, tenantDesc, createResoure, time, time};
        
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("Namespace creation failed");
            }
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc) {
    
        TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        final String sql = tenantInfoMapper.update(Arrays.asList("tenant_name", "tenant_desc", "gmt_modified"), Arrays.asList("kp", "tenant_id"));
        final Object[] args = new Object[] {tenantName, tenantDesc, System.currentTimeMillis(), kp, tenantId};
        
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("Namespace update failed");
            }
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public List<TenantInfo> findTenantByKp(String kp) {
        TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper.select(Arrays.asList("tenant_id", "tenant_name", "tenant_desc"),
                Collections.singletonList("kp"));
        return databaseOperate.queryMany(sql, new Object[] {kp}, TENANT_INFO_ROW_MAPPER);
        
    }
    
    @Override
    public TenantInfo findTenantByKp(String kp, String tenantId) {
        TenantInfoMapper tenantInfoMapper =  mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper.select(Arrays.asList("tenant_id", "tenant_name", "tenant_desc"),
                Arrays.asList("kp", "tenant_id"));
        return databaseOperate.queryOne(sql, new Object[] {kp, tenantId}, TENANT_INFO_ROW_MAPPER);
        
    }
    
    @Override
    public void removeTenantInfoAtomic(final String kp, final String tenantId) {
        TenantInfoMapper tenantInfoMapper =  mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        
        EmbeddedStorageContextUtils.addSqlContext(tenantInfoMapper.delete(Arrays.asList("kp", "tenant_id")), kp, tenantId);
        try {
            databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
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
        ConfigInfoMapper configInfoMapper =  mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sqlCountRows = configInfoMapper.count(null);
        String sqlFetchRows = configInfoMapper.listGroupKeyMd5ByPageFetchRows((pageNo - 1) * pageSize, pageSize);
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        Page<ConfigInfoWrapper> page = helper
                .fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo,
                        pageSize, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        
        return page.getPageItems();
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
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper.select(Arrays
                .asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "type", "gmt_modified", "md5",
                        "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
        
        return databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
    }
    
    @Override
    public boolean isExistTable(String tableName) {
        String sql = String.format("SELECT 1 FROM %s FETCH FIRST ROW ONLY", tableName);
        try {
            databaseOperate.queryOne(sql, Integer.class);
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
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        String sql = configInfoMapper.findAllConfigInfo4Export(ids, params);
        return databaseOperate.queryMany(sql, paramList.toArray(), CONFIG_ALL_INFO_ROW_MAPPER);
    }
    
    @Override
    public Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
            Map<String, Object> configAdvanceInfo, Timestamp time, boolean notify, SameConfigPolicy policy)
            throws NacosException {
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
                ParamUtils
                        .checkParam(configInfo.getDataId(), configInfo.getGroup(), "datumId", configInfo.getContent());
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
                ConfigInfo foundCfg = findConfigInfo(configInfo2Save.getDataId(), configInfo2Save.getGroup(),
                        configInfo2Save.getTenant());
                if (foundCfg != null) {
                    throw new Throwable("DuplicateKeyException: config already exists, should be overridden");
                }
                addConfigInfo(srcIp, srcUser, configInfo2Save, time, configAdvanceInfo, notify, callFinally);
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
        TenantInfoMapper tenantInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper.count(Arrays.asList("tenant_id"));
        Integer result = databaseOperate
                .queryOne(sql, new String[] {tenantId}, Integer.class);
        if (result == null) {
            return 0;
        }
        return result;
    }
    
    @Override
    public List<ConfigInfoWrapper> queryConfigInfoByNamespace(String tenantId) {
        if (Objects.isNull(tenantId)) {
            throw new IllegalArgumentException("tenantId can not be null");
        }
        String tenantTmp = StringUtils.isBlank(tenantId) ? StringUtils.EMPTY : tenantId;
        ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        final String sql = configInfoMapper
                .select(Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "type"),
                        Collections.singletonList("tenant_id"));
        return databaseOperate.queryMany(sql, new Object[] {tenantTmp}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
    }
}

