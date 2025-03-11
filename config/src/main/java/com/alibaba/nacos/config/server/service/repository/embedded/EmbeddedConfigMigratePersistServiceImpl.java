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
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigMigrateService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigMigratePersistService;
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
import com.alibaba.nacos.plugin.datasource.mapper.ConfigMigrateMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The type Embedded config migrate persist service.
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service("embeddedConfigMigratePersistServiceImpl")
public class EmbeddedConfigMigratePersistServiceImpl implements ConfigMigratePersistService {
    
    private static final String RESOURCE_CONFIG_INFO_ID = "config-info-id";
    
    private static final String RESOURCE_CONFIG_HISTORY_GRAY_ID = "config-history-gray-id";
    
    private final DatabaseOperate databaseOperate;
    
    private final IdGeneratorManager idGeneratorManager;
    
    private final ConfigMigrateService configMigrateService;
    
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
            @Qualifier("embeddedConfigInfoGrayPersistServiceImpl") ConfigInfoGrayPersistService configInfoGrayPersistService,
            ConfigMigrateService configMigrateService) {
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
        this.configMigrateService = configMigrateService;
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
            if (targetConfigInfoWrapper != null
                    && sourceConfigInfoWrapper.getLastModified() <= targetConfigInfoWrapper.getLastModified()) {
                return;
            }
            sourceConfigInfoWrapper.setTenant(targetTenant);
            configInfoPersistService.insertOrUpdate(null, srcUser, sourceConfigInfoWrapper, null);
        }
    }
    
    @Override
    public void syncConfigGray(String dataId, String group, String tenant, String grayName, String targetTenant,
            String srcUser) {
        ConfigInfoGrayWrapper sourceConfigInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group,
                tenant, grayName);
        ConfigInfoGrayWrapper targetConfigInfoGrayWrapper = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group,
                targetTenant, grayName);
        if (sourceConfigInfoGrayWrapper == null) {
            configInfoGrayPersistService.removeConfigInfoGray(dataId, group, targetTenant, grayName, null, srcUser);
        } else {
            if (targetConfigInfoGrayWrapper != null
                    && sourceConfigInfoGrayWrapper.getLastModified() <= targetConfigInfoGrayWrapper.getLastModified()) {
                return;
            }
            sourceConfigInfoGrayWrapper.setTenant(targetTenant);
            configInfoGrayPersistService.insertOrUpdateGray(sourceConfigInfoGrayWrapper,
                    sourceConfigInfoGrayWrapper.getGrayName(), sourceConfigInfoGrayWrapper.getGrayRule(),
                    null, srcUser);
        }
    }
}
