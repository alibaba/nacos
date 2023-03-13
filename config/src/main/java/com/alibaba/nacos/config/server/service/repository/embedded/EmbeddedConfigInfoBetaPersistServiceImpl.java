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

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.event.DerbyImportEvent;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoBetaMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Arrays;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;

/**
 * EmbeddedConfigInfoBetaPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings({"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service("embeddedConfigInfoBetaPersistServiceImpl")
public class EmbeddedConfigInfoBetaPersistServiceImpl implements ConfigInfoBetaPersistService {
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    private MapperManager mapperManager;
    
    /**
     * The constructor sets the dependency injection order.
     *
     * @param databaseOperate {@link EmbeddedStoragePersistServiceImpl}
     */
    public EmbeddedConfigInfoBetaPersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(Constants.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        NotifyCenter.registerToSharePublisher(DerbyImportEvent.class);
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<>(databaseOperate);
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
            final String sql = configInfoBetaMapper.insert(
                    Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "content", "md5", "beta_ips",
                            "src_ip", "src_user", "gmt_create", "gmt_modified", "encrypted_data_key"));
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
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfo configInfo = findConfigInfo4Beta(dataId, group, tenant);
        if (configInfo != null) {
            try {
                ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(
                        dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_BETA);
                final String sql = configInfoBetaMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
                final Object[] args = new Object[] {dataId, group, tenantTmp};
                
                EmbeddedStorageContextUtils.onDeleteConfigBetaInfo(tenantTmp, group, dataId,
                        System.currentTimeMillis());
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
            final String sql = configInfoBetaMapper.update(
                    Arrays.asList("content", "md5", "beta_ips", "src_ip", "src_user", "gmt_modified", "app_name",
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
    public ConfigInfoBetaWrapper findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        final String sql = configInfoBetaMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "beta_ips",
                        "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
        
        return databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp},
                CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public int configInfoBetaCount() {
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        String sql = configInfoBetaMapper.count(null);
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result;
    }
    
    @Override
    public Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        String sqlCountRows = configInfoBetaMapper.count(null);
        String sqlFetchRows = configInfoBetaMapper.findAllConfigInfoBetaForDumpAllFetchRows(startRow, pageSize);
        PaginationHelper<ConfigInfoBetaWrapper> helper = createPaginationHelper();
        return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo, pageSize,
                CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        
    }
}
