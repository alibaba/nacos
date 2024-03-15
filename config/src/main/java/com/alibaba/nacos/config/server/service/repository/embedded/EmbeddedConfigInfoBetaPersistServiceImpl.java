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
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
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
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoBetaMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;

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
     * @param databaseOperate databaseOperate.
     */
    public EmbeddedConfigInfoBetaPersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
        NotifyCenter.registerToSharePublisher(DerbyImportEvent.class);
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<>(databaseOperate);
    }
    
    @Override
    public ConfigInfoStateWrapper findConfigInfo4BetaState(final String dataId, final String group,
            final String tenant) {
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        final String sql = configInfoBetaMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "gmt_modified"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        return databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp},
                CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
    }
    
    private ConfigOperateResult getBetaOperateResult(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        ConfigInfoStateWrapper configInfo4Beta = this.findConfigInfo4BetaState(dataId, group, tenantTmp);
        if (configInfo4Beta == null) {
            return new ConfigOperateResult(false);
        }
        return new ConfigOperateResult(configInfo4Beta.getId(), configInfo4Beta.getLastModified());
        
    }
    
    @Override
    public ConfigOperateResult addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String encryptedDataKey = StringUtils.defaultEmptyIfBlank(configInfo.getEncryptedDataKey());
        
        configInfo.setTenant(tenantTmp);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            
            final String sql = configInfoBetaMapper.insert(
                    Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "content", "md5", "beta_ips",
                            "src_ip", "src_user", "gmt_create", "gmt_modified", "encrypted_data_key"));
            final Object[] args = new Object[] {configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp,
                    configInfo.getContent(), md5, betaIps, srcIp, srcUser, time, time, encryptedDataKey};
            
            EmbeddedStorageContextUtils.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
            EmbeddedStorageContextHolder.addSqlContext(sql, args);
            
            databaseOperate.blockUpdate();
            return getBetaOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser) {
        if (findConfigInfo4BetaState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()) == null) {
            return addConfigInfo4Beta(configInfo, betaIps, srcIp, srcUser);
        } else {
            return updateConfigInfo4Beta(configInfo, betaIps, srcIp, srcUser);
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateBetaCas(final ConfigInfo configInfo, final String betaIps,
            final String srcIp, final String srcUser) {
        if (findConfigInfo4BetaState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()) == null) {
            return addConfigInfo4Beta(configInfo, betaIps, srcIp, srcUser);
        } else {
            return updateConfigInfo4BetaCas(configInfo, betaIps, srcIp, srcUser);
        }
        
    }
    
    @Override
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoStateWrapper configInfo = findConfigInfo4BetaState(dataId, group, tenant);
        if (configInfo != null) {
            try {
                ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(
                        dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_BETA);
                final String sql = configInfoBetaMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
                final Object[] args = new Object[] {dataId, group, tenantTmp};
                
                EmbeddedStorageContextUtils.onDeleteConfigBetaInfo(tenantTmp, group, dataId,
                        System.currentTimeMillis());
                EmbeddedStorageContextHolder.addSqlContext(sql, args);
                
                boolean result = databaseOperate.update(EmbeddedStorageContextHolder.getCurrentSqlContext());
                if (!result) {
                    throw new NacosConfigException("[Tag] Configuration deletion failed");
                }
            } finally {
                EmbeddedStorageContextHolder.cleanAllContext();
            }
            
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp,
            String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String encryptedDataKey = StringUtils.defaultEmptyIfBlank(configInfo.getEncryptedDataKey());
        
        configInfo.setTenant(tenantTmp);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            final String sql = configInfoBetaMapper.update(
                    Arrays.asList("content", "md5", "beta_ips", "src_ip", "src_user", "gmt_modified", "app_name",
                            "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id"));
            
            final Object[] args = new Object[] {configInfo.getContent(), md5, betaIps, srcIp, srcUser, time, appNameTmp,
                    encryptedDataKey, configInfo.getDataId(), configInfo.getGroup(), tenantTmp};
            
            EmbeddedStorageContextUtils.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
            EmbeddedStorageContextHolder.addSqlContext(sql, args);
            
            databaseOperate.blockUpdate();
            return getBetaOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4BetaCas(ConfigInfo configInfo, String betaIps, String srcIp,
            String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        
        configInfo.setTenant(tenantTmp);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            MapperContext context = new MapperContext();
            context.putUpdateParameter(FieldConstant.CONTENT, configInfo.getContent());
            context.putUpdateParameter(FieldConstant.MD5, md5);
            context.putUpdateParameter(FieldConstant.BETA_IPS, betaIps);
            context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
            context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
            context.putUpdateParameter(FieldConstant.GMT_MODIFIED, time);
            context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
            
            context.putWhereParameter(FieldConstant.DATA_ID, configInfo.getDataId());
            context.putWhereParameter(FieldConstant.GROUP_ID, configInfo.getGroup());
            context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
            context.putWhereParameter(FieldConstant.MD5, configInfo.getMd5());
            MapperResult mapperResult = configInfoBetaMapper.updateConfigInfo4BetaCas(context);
            
            final String sql = mapperResult.getSql();
            List<Object> paramList = mapperResult.getParamList();
            final Object[] args = paramList.toArray();
            
            EmbeddedStorageContextUtils.onModifyConfigBetaInfo(configInfo, betaIps, srcIp, time);
            EmbeddedStorageContextHolder.addSqlContext(sql, args);
            
            boolean success = databaseOperate.blockUpdate();
            if (success) {
                return getBetaOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            } else {
                return new ConfigOperateResult(false);
            }
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigInfoBetaWrapper findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        final String sql = configInfoBetaMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "beta_ips",
                        "encrypted_data_key", "gmt_modified"), Arrays.asList("data_id", "group_id", "tenant_id"));
        
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
        
        MapperContext context = new MapperContext();
        context.setStartRow(startRow);
        context.setPageSize(pageSize);
        
        MapperResult mapperResult = configInfoBetaMapper.findAllConfigInfoBetaForDumpAllFetchRows(context);
        
        String sqlFetchRows = mapperResult.getSql();
        
        PaginationHelper<ConfigInfoBetaWrapper> helper = createPaginationHelper();
        return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo, pageSize,
                CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        
    }
}
