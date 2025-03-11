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

import com.alibaba.nacos.common.utils.StringUtils;
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

import java.util.List;

@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalConfigMigratePersistServiceImpl")
public class ExternalConfigMigratePersistServiceImpl implements ConfigMigratePersistService {
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private DataSourceService dataSourceService;
    
    private MapperManager mapperManager;
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
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
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
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
                    configInfoGrayPersistService.removeConfigInfoGray(dataId, group, targetTenant, grayName, null, srcUser);
                    ConfigInfoGrayWrapper configInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                            dataId, group, tenant, grayName);
                    if (configInfoGrayWrapper != null) {
                        throw new Exception("sourceConfigInfoGray has been updated,dataId=" + dataId + ",group=" + group
                                + ",tenant=" + tenant + ",grayName=" + grayName);
                    }
                } else {
                    if (targetConfigInfoGrayWrapper != null
                            && sourceConfigInfoGrayWrapper.getLastModified() <= targetConfigInfoGrayWrapper.getLastModified()) {
                        return null;
                    }
                    sourceConfigInfoGrayWrapper.setTenant(targetTenant);
                    configInfoGrayPersistService.insertOrUpdateGray(sourceConfigInfoGrayWrapper,
                            sourceConfigInfoGrayWrapper.getGrayName(), sourceConfigInfoGrayWrapper.getGrayRule(), null,
                            srcUser);
                    ConfigInfoGrayWrapper configInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(
                            dataId, group, tenant, grayName);
                    if (!StringUtils.equals(configInfoGrayWrapper.getMd5(), sourceConfigInfoGrayWrapper.getMd5())
                            || !StringUtils.equals(configInfoGrayWrapper.getGrayRule(),
                            sourceConfigInfoGrayWrapper.getGrayRule())) {
                        throw new Exception("sourceConfigInfoGray has been updated,dataId=" + dataId + ",group=" + group
                                + ",tenant=" + tenant + ",grayName=" + grayName);
                    }
                }
            } catch (Exception e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw new RuntimeException(e);
            }
            return null;
        });
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
                    configInfoPersistService.removeConfigInfo(dataId, group, targetTenant, null, srcUser);
                    ConfigInfoWrapper configInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                            tenant);
                    if (configInfoWrapper != null) {
                        throw new Exception(
                                "sourceConfigInfo has been updated,dataId=" + dataId + ",group=" + group + ",tenant="
                                        + tenant);
                    }
                } else {
                    if (targetConfigInfoWrapper != null
                            && sourceConfigInfoWrapper.getLastModified() <= targetConfigInfoWrapper.getLastModified()) {
                        return null;
                    }
                    sourceConfigInfoWrapper.setTenant(targetTenant);
                    configInfoPersistService.insertOrUpdate(null, srcUser, sourceConfigInfoWrapper, null);
                    ConfigInfoWrapper configInfoWrapper = configInfoPersistService.findConfigInfo(dataId, group,
                            tenant);
                    if (!StringUtils.equals(configInfoWrapper.getMd5(), sourceConfigInfoWrapper.getMd5())) {
                        throw new Exception(
                                "sourceConfigInfo has been updated,dataId=" + dataId + ",group=" + group + ",tenant="
                                        + tenant);
                    }
                }
            } catch (Exception e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
