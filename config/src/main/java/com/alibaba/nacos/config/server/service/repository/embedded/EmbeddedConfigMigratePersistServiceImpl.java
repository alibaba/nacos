/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigMigratePersistService;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.event.DerbyImportEvent;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedPaginationHelperImpl;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoGrayMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigMigrateMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER;

/**
 * The type Embedded config migrate persist service.
 *
 * @author Sunrisea
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service("embeddedConfigMigratePersistServiceImpl")
public class EmbeddedConfigMigratePersistServiceImpl implements ConfigMigratePersistService {
    
    private static final String RESOURCE_CONFIG_INFO_ID = "config-info-id";
    
    private static final String RESOURCE_CONFIG_HISTORY_GRAY_ID = "config-history-gray-id";
    
    private final DatabaseOperate databaseOperate;
    
    private final IdGeneratorManager idGeneratorManager;
    
    private DataSourceService dataSourceService;
    
    private MapperManager mapperManager;
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    /**
     * Instantiates a new Embedded config migrate persist service.
     *
     * @param databaseOperate              the database operate
     * @param idGeneratorManager           the id generator manager
     * @param configInfoPersistService     the config info persist service
     * @param configInfoGrayPersistService the config info gray persist service
     */
    public EmbeddedConfigMigratePersistServiceImpl(DatabaseOperate databaseOperate,
            IdGeneratorManager idGeneratorManager,
            @Qualifier("embeddedConfigInfoPersistServiceImpl") ConfigInfoPersistService configInfoPersistService,
            @Qualifier("embeddedConfigInfoGrayPersistServiceImpl") ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.databaseOperate = databaseOperate;
        this.idGeneratorManager = idGeneratorManager;
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        idGeneratorManager.register(RESOURCE_CONFIG_INFO_ID, RESOURCE_CONFIG_HISTORY_GRAY_ID);
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        NotifyCenter.registerToSharePublisher(DerbyImportEvent.class);
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<>(databaseOperate);
    }
    
    @Override
    public Integer configInfoConflictCount(String srcUser) {
        ConfigMigrateMapper configInfoMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        MapperResult mapperResult = configInfoMigrateMapper.getConfigConflictCount(context);
        Integer result = databaseOperate.queryOne(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoConflictCount error");
        }
        return result;
    }
    
    @Override
    public Integer configInfoGrayConflictCount(String srcUser) {
        ConfigMigrateMapper configInfoMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        MapperResult mapperResult = configInfoMigrateMapper.getConfigGrayConflictCount(context);
        Integer result = databaseOperate.queryOne(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoGrayConflictCount error");
        }
        return result;
    }
    
    @Override
    public List<Long> getMigrateConfigInsertIdList(long startId, int pageSize) {
        ConfigMigrateMapper configInfoMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, startId);
        context.setPageSize(pageSize);
        MapperResult mapperResult = configInfoMigrateMapper.findConfigIdNeedInsertMigrate(context);
        return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(), Long.class);
    }
    
    @Override
    public List<Long> getMigrateConfigGrayInsertIdList(long startId, int pageSize) {
        ConfigMigrateMapper configInfoMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, startId);
        context.setPageSize(pageSize);
        MapperResult mapperResult = configInfoMigrateMapper.findConfigGrayIdNeedInsertMigrate(context);
        return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(), Long.class);
    }
    
    @Override
    public List<ConfigInfo> getMigrateConfigUpdateList(long startId, int pageSize, String srcTenant, String targetTenant, String srcUser) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        context.putWhereParameter(FieldConstant.ID, startId);
        context.putWhereParameter(FieldConstant.SRC_TENANT, srcTenant);
        context.putWhereParameter(FieldConstant.TARGET_TENANT, targetTenant);
        context.setPageSize(pageSize);
        MapperResult mapperResult = configMigrateMapper.findConfigNeedUpdateMigrate(context);
        return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(), CONFIG_INFO_ROW_MAPPER);
    }
    
    @Override
    public List<ConfigInfoGrayWrapper> getMigrateConfigGrayUpdateList(long startId, int pageSize, String srcTenant,
            String targetTenant, String srcUser) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        context.putWhereParameter(FieldConstant.ID, startId);
        context.putWhereParameter(FieldConstant.SRC_TENANT, srcTenant);
        context.putWhereParameter(FieldConstant.TARGET_TENANT, targetTenant);
        context.setPageSize(pageSize);
        MapperResult mapperResult = configMigrateMapper.findConfigGrayNeedUpdateMigrate(context);
        return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
    }
    
    @Override
    public void migrateConfigInsertByIds(List<Long> ids, String srcUser) {
        ConfigMigrateMapper configInfoMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        for (Long targetId : ids) {
            long configId = idGeneratorManager.nextId(RESOURCE_CONFIG_INFO_ID);
            MapperContext context = new MapperContext();
            context.putWhereParameter(FieldConstant.TARGET_ID, targetId);
            context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
            context.putWhereParameter(FieldConstant.ID, configId);
            MapperResult result = configInfoMigrateMapper.migrateConfigInsertByIds(context);
            EmbeddedStorageContextHolder.addSqlContext(result.getSql(), result.getParamList().toArray());
        }
        databaseOperate.blockUpdate();
    }
    
    @Override
    public void migrateConfigGrayInsertByIds(List<Long> ids, String srcUser) {
        ConfigMigrateMapper configInfoMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        for (Long targetId : ids) {
            long configId = idGeneratorManager.nextId(RESOURCE_CONFIG_HISTORY_GRAY_ID);
            MapperContext context = new MapperContext();
            context.putWhereParameter(FieldConstant.TARGET_ID, targetId);
            context.putWhereParameter(FieldConstant.ID, configId);
            context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
            MapperResult result = configInfoMigrateMapper.migrateConfigGrayInsertByIds(context);
            EmbeddedStorageContextHolder.addSqlContext(result.getSql(), result.getParamList().toArray());
        }
        databaseOperate.blockUpdate();
    }
    
    @Override
    public void syncConfig(String dataId, String group, String tenant, String targetTenant, String srcUser) {
        ConfigInfoWrapper sourceConfigInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group, tenant);
        ConfigInfoWrapper targetConfigInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                targetTenant);
        if (sourceConfigInfoWrapper == null) {
            configInfoPersistService.removeConfigInfo(dataId, group, targetTenant, null, srcUser);
        } else {
            if (targetConfigInfoWrapper == null) {
                sourceConfigInfoWrapper.setTenant(targetTenant);
                long configId = idGeneratorManager.nextId(RESOURCE_CONFIG_INFO_ID);
                configInfoPersistService.addConfigInfoAtomic(configId, null, srcUser,
                        sourceConfigInfoWrapper, null);
            } else if (sourceConfigInfoWrapper.getLastModified() >= targetConfigInfoWrapper.getLastModified()) {
                sourceConfigInfoWrapper.setTenant(targetTenant);
                updateConfigInfoAtomic(sourceConfigInfoWrapper, null, srcUser, null,
                        targetConfigInfoWrapper.getLastModified(), targetConfigInfoWrapper.getMd5());
            }
        }
    }
    
    /**
     * Update config info atomic.
     *
     * @param configInfo        the config info
     * @param srcIp             the src ip
     * @param srcUser           the src user
     * @param configAdvanceInfo the config advance info
     * @param lastModified      the last modified
     */
    public void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            Map<String, Object> configAdvanceInfo, long lastModified, final String targetMd5) {
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
                Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified@NOW()", "app_name", "c_desc",
                        "c_use", "effect", "type", "c_schema", "encrypted_data_key"),
                Arrays.asList("data_id", "group_id", "tenant_id", "gmt_modified", "md5"));
        
        final Object[] args = new Object[] {configInfo.getContent(), md5Tmp, srcIp, srcUser, appNameTmp, desc, use,
                effect, type, schema, encryptedDataKey, configInfo.getDataId(), configInfo.getGroup(), tenantTmp,
                new Timestamp(lastModified), targetMd5};
        
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
    }
    
    @Override
    public void syncConfigGray(String dataId, String group, String tenant, String grayName, String targetTenant,
            String srcUser) {
        ConfigInfoGrayWrapper sourceConfigInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group,
                tenant, grayName);
        ConfigInfoGrayWrapper targetConfigInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group,
                targetTenant, grayName);
        if (sourceConfigInfoGrayWrapper == null) {
            removeConfigInfoGrayWithoutHistory(dataId, group, targetTenant, grayName, null, srcUser);
        } else {
            if (targetConfigInfoGrayWrapper == null) {
                sourceConfigInfoGrayWrapper.setTenant(targetTenant);
                long configGrayId = idGeneratorManager.nextId(RESOURCE_CONFIG_HISTORY_GRAY_ID);
                configInfoGrayPersistService.addConfigInfoGrayAtomic(configGrayId, sourceConfigInfoGrayWrapper,
                        grayName, sourceConfigInfoGrayWrapper.getGrayRule(), null, srcUser);
            } else if (sourceConfigInfoGrayWrapper.getLastModified() >= targetConfigInfoGrayWrapper.getLastModified()) {
                sourceConfigInfoGrayWrapper.setTenant(targetTenant);
                updateConfigInfo4GrayWithoutHistory(sourceConfigInfoGrayWrapper,
                        sourceConfigInfoGrayWrapper.getGrayName(), sourceConfigInfoGrayWrapper.getGrayRule(),
                        null, srcUser, targetConfigInfoGrayWrapper.getLastModified(), targetConfigInfoGrayWrapper.getMd5());
            }
        }
    }
    
    /**
     * Remove config info gray without history.
     *
     * @param dataId   the data id
     * @param group    the group
     * @param tenant   the tenant
     * @param grayName the gray name
     * @param srcIp    the src ip
     * @param srcUser  the src user
     */
    public void removeConfigInfoGrayWithoutHistory(final String dataId, final String group, final String tenant,
            final String grayName, final String srcIp, final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName;
        
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        final String sql = configInfoGrayMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "gray_name"));
        final Object[] args = new Object[] {dataId, group, tenantTmp, grayNameTmp};
        
        EmbeddedStorageContextUtils.onDeleteConfigGrayInfo(tenantTmp, group, dataId, grayNameTmp, srcIp);
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
        try {
            databaseOperate.update(EmbeddedStorageContextHolder.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    /**
     * Update config info 4 gray without history.
     *
     * @param configInfo the config info
     * @param grayName   the gray name
     * @param grayRule   the gray rule
     * @param srcIp      the src ip
     * @param srcUser    the src user
     */
    public void updateConfigInfo4GrayWithoutHistory(ConfigInfo configInfo, String grayName, String grayRule,
            String srcIp, String srcUser, long lastModified, final String targetMd5) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
        configInfo.setTenant(tenantTmp);
        
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_GRAY);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            
            final String sql = configInfoGrayMapper.update(
                    Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name", "gray_rule"),
                    Arrays.asList("data_id", "group_id", "tenant_id", "gray_name", "gmt_modified", "md5"));
            final Object[] args = new Object[] {configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp,
                    grayRuleTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp, new Timestamp(lastModified), targetMd5};
            EmbeddedStorageContextUtils.onModifyConfigGrayInfo(configInfo, grayNameTmp, grayRuleTmp, srcIp, time);
            EmbeddedStorageContextHolder.addSqlContext(sql, args);
            databaseOperate.blockUpdate();
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
}
