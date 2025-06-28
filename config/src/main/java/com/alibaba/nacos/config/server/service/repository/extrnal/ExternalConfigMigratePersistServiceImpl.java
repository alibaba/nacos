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

package com.alibaba.nacos.config.server.service.repository.extrnal;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigMigratePersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.extrnal.ExternalStoragePaginationHelperImpl;
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
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER;

/**
 * The type External config migrate persist service.
 *
 * @author Sunrisea
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalConfigMigratePersistServiceImpl")
public class ExternalConfigMigratePersistServiceImpl implements ConfigMigratePersistService {
    
    /**
     * The Jt.
     */
    protected JdbcTemplate jt;
    
    /**
     * The Tjt.
     */
    protected TransactionTemplate tjt;
    
    private DataSourceService dataSourceService;
    
    private MapperManager mapperManager;
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    /**
     * Instantiates a new External config migrate persist service.
     *
     * @param configInfoPersistService     the config info persist service
     * @param configInfoGrayPersistService the config info gray persist service
     */
    public ExternalConfigMigratePersistServiceImpl(
            @Qualifier("externalConfigInfoPersistServiceImpl") ConfigInfoPersistService configInfoPersistService,
            @Qualifier("externalConfigInfoGrayPersistServiceImpl") ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jt = dataSourceService.getJdbcTemplate();
        this.tjt = dataSourceService.getTransactionTemplate();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new ExternalStoragePaginationHelperImpl<>(jt);
    }
    
    @Override
    public Integer configInfoConflictCount(String srcUser) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        MapperResult mapperResult = configMigrateMapper.getConfigConflictCount(context);
        Integer result = jt.queryForObject(mapperResult.getSql(), mapperResult.getParamList().toArray(), Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoConflictCount error");
        }
        return result;
    }
    
    @Override
    public Integer configInfoGrayConflictCount(String srcUser) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        MapperResult mapperResult = configMigrateMapper.getConfigGrayConflictCount(context);
        Integer result = jt.queryForObject(mapperResult.getSql(), mapperResult.getParamList().toArray(), Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoGrayConflictCount error");
        }
        return result;
    }
    
    @Override
    public List<Long> getMigrateConfigInsertIdList(long startId, int pageSize) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, startId);
        context.setPageSize(pageSize);
        MapperResult mapperResult = configMigrateMapper.findConfigIdNeedInsertMigrate(context);
        return jt.queryForList(mapperResult.getSql(), mapperResult.getParamList().toArray(), Long.class);
    }
    
    @Override
    public List<Long> getMigrateConfigGrayInsertIdList(long startId, int pageSize) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, startId);
        context.setPageSize(pageSize);
        MapperResult mapperResult = configMigrateMapper.findConfigGrayIdNeedInsertMigrate(context);
        return jt.queryForList(mapperResult.getSql(), mapperResult.getParamList().toArray(), Long.class);
    }
    
    @Override
    public List<ConfigInfo> getMigrateConfigUpdateList(long startId, int pageSize, String srcTenant,
            String targetTenant, String srcUser) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        context.putWhereParameter(FieldConstant.ID, startId);
        context.putWhereParameter(FieldConstant.SRC_TENANT, srcTenant);
        context.putWhereParameter(FieldConstant.TARGET_TENANT, targetTenant);
        context.setPageSize(pageSize);
        MapperResult mapperResult = configMigrateMapper.findConfigNeedUpdateMigrate(context);
        return jt.query(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                CONFIG_INFO_ROW_MAPPER);
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
        return jt.query(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
    }
    
    @Override
    public void migrateConfigInsertByIds(List<Long> ids, String srcUser) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.IDS, ids);
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        MapperResult mapperResult = configMigrateMapper.migrateConfigInsertByIds(context);
        try {
            jt.update(mapperResult.getSql(), mapperResult.getParamList().toArray());
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] migrateConfigInsertByIds" + e, e);
            throw e;
        }
    }
    
    @Override
    public void migrateConfigGrayInsertByIds(List<Long> ids, String srcUser) {
        ConfigMigrateMapper configMigrateMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.MIGRATE_CONFIG);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.IDS, ids);
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
        MapperResult mapperResult = configMigrateMapper.migrateConfigGrayInsertByIds(context);
        try {
            jt.update(mapperResult.getSql(), mapperResult.getParamList().toArray());
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] migrateConfigGrayInsertByIds" + e, e);
            throw e;
        }
    }
    
    @Override
    public void syncConfigGray(String dataId, String group, String tenant, String grayName, String targetTenant,
            String srcUser) {
        tjt.execute(status -> {
            try {
                ConfigInfoGrayWrapper sourceConfigInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                        dataId, group, tenant, grayName);
                ConfigInfoGrayWrapper targetConfigInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                        dataId, group, targetTenant, grayName);
                if (sourceConfigInfoGrayWrapper == null) {
                    removeConfigInfoGrayWithoutHistory(dataId, group, targetTenant, grayName, null, srcUser);
                    ConfigInfoGrayWrapper configInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                            dataId, group, tenant, grayName);
                    if (configInfoGrayWrapper != null) {
                        throw new Exception("sourceConfigInfoGray has been updated,dataId=" + dataId + ",group=" + group
                                + ",tenant=" + tenant + ",grayName=" + grayName);
                    }
                } else {
                    if (targetConfigInfoGrayWrapper == null) {
                        sourceConfigInfoGrayWrapper.setTenant(targetTenant);
                        configInfoGrayPersistService.addConfigInfoGrayAtomic(-1, sourceConfigInfoGrayWrapper,
                                sourceConfigInfoGrayWrapper.getGrayName(), sourceConfigInfoGrayWrapper.getGrayRule(),
                                null, srcUser);
                        ConfigInfoGrayWrapper configInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                                dataId, group, tenant, grayName);
                        if (!StringUtils.equals(configInfoGrayWrapper.getMd5(), sourceConfigInfoGrayWrapper.getMd5())
                                || !StringUtils.equals(configInfoGrayWrapper.getGrayRule(),
                                sourceConfigInfoGrayWrapper.getGrayRule())) {
                            throw new Exception(
                                    "sourceConfigInfoGray has been updated,dataId=" + dataId + ",group=" + group
                                            + ",tenant=" + tenant + ",grayName=" + grayName);
                        }
                    } else if (sourceConfigInfoGrayWrapper.getLastModified()
                            >= targetConfigInfoGrayWrapper.getLastModified()) {
                        sourceConfigInfoGrayWrapper.setTenant(targetTenant);
                        updateConfigInfo4GrayWithoutHistory(sourceConfigInfoGrayWrapper,
                                sourceConfigInfoGrayWrapper.getGrayName(), sourceConfigInfoGrayWrapper.getGrayRule(),
                                null, srcUser, targetConfigInfoGrayWrapper.getLastModified(), targetConfigInfoGrayWrapper.getMd5());
                        ConfigInfoGrayWrapper configInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                                dataId, group, tenant, grayName);
                        if (!StringUtils.equals(configInfoGrayWrapper.getMd5(), sourceConfigInfoGrayWrapper.getMd5())
                                || !StringUtils.equals(configInfoGrayWrapper.getGrayRule(),
                                sourceConfigInfoGrayWrapper.getGrayRule())) {
                            throw new Exception(
                                    "sourceConfigInfoGray has been updated,dataId=" + dataId + ",group=" + group
                                            + ",tenant=" + tenant + ",grayName=" + grayName);
                        }
                    }
                }
            } catch (Exception e) {
                LogUtil.FATAL_LOG.error("[db-error] syncConfigGray" + e, e);
                throw new RuntimeException(e);
            }
            return null;
        });
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
        try {
            ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_GRAY);
            jt.update(configInfoGrayMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "gray_name")),
                    dataId, group, tenantTmp, grayNameTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
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
        Timestamp modifiedTime = new Timestamp(lastModified);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_GRAY);
            jt.update(configInfoGrayMapper.update(
                            Arrays.asList("content", "encrypted_data_key", "md5", "src_ip", "src_user", "gmt_modified@NOW()",
                                    "app_name", "gray_rule"), Arrays.asList("data_id", "group_id", "tenant_id", "gray_name", "gmt_modified", "md5")),
                    configInfo.getContent(), configInfo.getEncryptedDataKey(), md5, srcIp, srcUser, appNameTmp,
                    grayRuleTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp, modifiedTime, targetMd5);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public void syncConfig(String dataId, String group, String tenant, String targetTenant, String srcUser) {
        tjt.execute(status -> {
            try {
                ConfigInfoWrapper sourceConfigInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                        tenant);
                ConfigInfoWrapper targetConfigInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                        targetTenant);
                if (sourceConfigInfoWrapper == null) {
                    configInfoPersistService.removeConfigInfoAtomic(dataId, group, targetTenant, null, srcUser);
                    ConfigInfoWrapper configInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                            tenant);
                    if (configInfoWrapper != null) {
                        LogUtil.FATAL_LOG.error(
                                "syncConfig failed, sourceConfigInfo has been updated,dataId=" + dataId + ",group="
                                        + group + ",tenant=" + tenant);
                        throw new Exception(
                                "syncConfig failed,sourceConfigInfo has been updated,dataId=" + dataId + ",group="
                                        + group + ",tenant=" + tenant);
                    }
                } else {
                    if (targetConfigInfoWrapper == null) {
                        sourceConfigInfoWrapper.setTenant(targetTenant);
                        configInfoPersistService.addConfigInfoAtomic(-1, null, srcUser, sourceConfigInfoWrapper, null);
                        ConfigInfoWrapper configInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                                tenant);
                        if (!StringUtils.equals(configInfoWrapper.getMd5(), sourceConfigInfoWrapper.getMd5())) {
                            LogUtil.FATAL_LOG.error(
                                    "syncConfig failed, sourceConfigInfo has been updated,dataId=" + dataId + ",group="
                                            + group + ",tenant=" + tenant);
                            throw new Exception(
                                    "syncConfig failed, sourceConfigInfo has been updated,dataId=" + dataId + ",group="
                                            + group + ",tenant=" + tenant);
                        }
                    } else if (sourceConfigInfoWrapper.getLastModified() >= targetConfigInfoWrapper.getLastModified()) {
                        sourceConfigInfoWrapper.setTenant(targetTenant);
                        updateConfigInfoAtomic(sourceConfigInfoWrapper, null, srcUser, null, targetConfigInfoWrapper.getLastModified(),
                                 targetConfigInfoWrapper.getMd5());
                        ConfigInfoWrapper configInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                                tenant);
                        if (!StringUtils.equals(configInfoWrapper.getMd5(), sourceConfigInfoWrapper.getMd5())) {
                            LogUtil.FATAL_LOG.error(
                                    "syncConfig failed, sourceConfigInfo has been updated,dataId=" + dataId + ",group="
                                            + group + ",tenant=" + tenant);
                            throw new Exception(
                                    "syncConfig failed, sourceConfigInfo has been updated,dataId=" + dataId + ",group="
                                            + group + ",tenant=" + tenant);
                        }
                    }
                }
            } catch (Exception e) {
                LogUtil.FATAL_LOG.error("[db-error] syncConfig" + e, e);
                throw new RuntimeException(e);
            }
            return null;
        });
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
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        Timestamp modifiedTime = new Timestamp(lastModified);
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        try {
            ConfigInfoMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO);
            jt.update(configInfoMapper.update(
                            Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified@NOW()", "app_name", "c_desc",
                                    "c_use", "effect", "type", "c_schema", "encrypted_data_key"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "gmt_modified", "md5")), configInfo.getContent(), md5Tmp, srcIp,
                    srcUser, appNameTmp, desc, use, effect, type, schema, encryptedDataKey, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp, modifiedTime, targetMd5);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
}
