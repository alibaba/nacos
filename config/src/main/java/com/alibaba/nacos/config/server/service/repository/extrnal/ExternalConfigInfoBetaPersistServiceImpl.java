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

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.PaginationHelper;
import com.alibaba.nacos.persistence.repository.extrnal.ExternalStoragePaginationHelperImpl;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoBetaMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;

/**
 * ExternalConfigInfoBetaPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalConfigInfoBetaPersistServiceImpl")
public class ExternalConfigInfoBetaPersistServiceImpl implements ConfigInfoBetaPersistService {
    
    private DataSourceService dataSourceService;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private MapperManager mapperManager;
    
    public ExternalConfigInfoBetaPersistServiceImpl() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jt = dataSourceService.getJdbcTemplate();
        this.tjt = dataSourceService.getTransactionTemplate();
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new ExternalStoragePaginationHelperImpl<>(jt);
    }
    
    @Override
    public ConfigOperateResult addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE);
        String encryptedDataKey = StringUtils.defaultEmptyIfBlank(configInfo.getEncryptedDataKey());
        try {
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);
            
            jt.update(configInfoBetaMapper.insert(
                            Arrays.asList("data_id", "group_id", "tenant_id", "app_name", "content", "md5", "beta_ips",
                                    "src_ip", "src_user", "gmt_create@NOW()", "gmt_modified@NOW()", "encrypted_data_key")),
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(), md5,
                    betaIps, srcIp, srcUser, encryptedDataKey);
            return getBetaOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser) {
        
        ConfigInfoStateWrapper configInfo4BetaState = this.findConfigInfo4BetaState(configInfo.getDataId(),
                configInfo.getGroup(), configInfo.getTenant());
        if (configInfo4BetaState == null) {
            return addConfigInfo4Beta(configInfo, betaIps, srcIp, srcUser);
            
        } else {
            return updateConfigInfo4Beta(configInfo, betaIps, srcIp, srcUser);
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateBetaCas(final ConfigInfo configInfo, final String betaIps,
            final String srcIp, final String srcUser) {
        ConfigInfoStateWrapper configInfo4BetaState = this.findConfigInfo4BetaState(configInfo.getDataId(),
                configInfo.getGroup(), configInfo.getTenant());
        if (configInfo4BetaState == null) {
            return addConfigInfo4Beta(configInfo, betaIps, srcIp, srcUser);
        } else {
            return updateConfigInfo4BetaCas(configInfo, betaIps, srcIp, srcUser);
        }
    }
    
    @Override
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        tjt.execute(status -> {
            try {
                ConfigInfoStateWrapper configInfo = findConfigInfo4BetaState(dataId, group, tenant);
                if (configInfo != null) {
                    ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(
                            dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_BETA);
                    jt.update(configInfoBetaMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id")), dataId,
                            group, tenantTmp);
                }
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw e;
            }
            return Boolean.TRUE;
        });
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp,
            String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String encryptedDataKey = StringUtils.defaultEmptyIfBlank(configInfo.getEncryptedDataKey());
        try {
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);

            jt.update(configInfoBetaMapper.update(
                            Arrays.asList("content", "md5", "beta_ips", "src_ip", "src_user", "gmt_modified@NOW()",
                                    "app_name", "encrypted_data_key"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    configInfo.getContent(), md5, betaIps, srcIp, srcUser, appNameTmp, encryptedDataKey,
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            return getBetaOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfoStateWrapper findConfigInfo4BetaState(final String dataId, final String group,
            final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            return this.jt.queryForObject(
                    "SELECT id,data_id,group_id,tenant_id,gmt_modified FROM config_info_beta WHERE data_id=? AND group_id=? AND tenant_id=? ",
                    new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    private ConfigOperateResult getBetaOperateResult(String dataId, String group, String tenant) {
        ConfigInfoStateWrapper configInfo4Beta = this.findConfigInfo4BetaState(dataId, group, tenant);
        if (configInfo4Beta == null) {
            return new ConfigOperateResult(false);
        }
        return new ConfigOperateResult(configInfo4Beta.getId(), configInfo4Beta.getLastModified());
        
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4BetaCas(ConfigInfo configInfo, String betaIps, String srcIp,
            String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        try {
            ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_BETA);

            MapperContext context = new MapperContext();
            context.putUpdateParameter(FieldConstant.CONTENT, configInfo.getContent());
            context.putUpdateParameter(FieldConstant.MD5, md5);
            context.putUpdateParameter(FieldConstant.BETA_IPS, betaIps);
            context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
            context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
            context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
            
            context.putWhereParameter(FieldConstant.DATA_ID, configInfo.getDataId());
            context.putWhereParameter(FieldConstant.GROUP_ID, configInfo.getGroup());
            context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
            context.putWhereParameter(FieldConstant.MD5, configInfo.getMd5());
            MapperResult mapperResult = configInfoBetaMapper.updateConfigInfo4BetaCas(context);
            final String sql = mapperResult.getSql();
            List<Object> paramList = mapperResult.getParamList();
            final Object[] args = paramList.toArray();
            
            boolean result = jt.update(sql, args) > 0;
            if (result) {
                return getBetaOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
            } else {
                return new ConfigOperateResult(false);
            }
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
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
                                    "encrypted_data_key", "gmt_modified"), Arrays.asList("data_id", "group_id", "tenant_id")),
                    new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public int configInfoBetaCount() {
        ConfigInfoBetaMapper configInfoBetaMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_BETA);
        String sql = configInfoBetaMapper.count(null);
        Integer result = jt.queryForObject(sql, Integer.class);
        
        return result.intValue();
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
        try {
            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[] {}, pageNo, pageSize,
                    CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
}
