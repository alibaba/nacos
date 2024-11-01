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
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExtInfoUtil;
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
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoGrayMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;


/**
 * ExternalConfigInfoGrayPersistServiceImpl.
 *
 * @author rong
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalConfigInfoGrayPersistServiceImpl")
public class ExternalConfigInfoGrayPersistServiceImpl implements ConfigInfoGrayPersistService {
    
    private DataSourceService dataSourceService;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private MapperManager mapperManager;
    
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    public ExternalConfigInfoGrayPersistServiceImpl(
            @Qualifier("externalHistoryConfigInfoPersistServiceImpl") HistoryConfigInfoPersistService historyConfigInfoPersistService) {
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
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
    public ConfigInfoStateWrapper findConfigInfo4GrayState(final String dataId, final String group, final String tenant,
            String grayName) {
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        try {
            return this.jt.queryForObject(configInfoGrayMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "gray_rule", "gmt_modified"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "gray_name")),
                    new Object[] {dataId, group, tenantTmp, grayNameTmp}, CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    private ConfigOperateResult getGrayOperateResult(String dataId, String group, String tenant, String grayName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        ConfigInfoStateWrapper configInfo4Gray = this.findConfigInfo4GrayState(dataId, group, tenantTmp, grayName);
        if (configInfo4Gray == null) {
            return new ConfigOperateResult(false);
        }
        return new ConfigOperateResult(configInfo4Gray.getId(), configInfo4Gray.getLastModified());
        
    }
    
    @Override
    public ConfigOperateResult addConfigInfo4Gray(ConfigInfo configInfo, String grayName, String grayRule, String srcIp,
            String srcUser) {
        return tjt.execute(status -> {
            String tenantTmp =
                    StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant().trim();
            String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
            String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
            try {
                addConfigInfoGrayAtomic(-1, configInfo, grayNameTmp, grayRuleTmp, srcIp, srcUser);
                
                Timestamp now = new Timestamp(System.currentTimeMillis());
                historyConfigInfoPersistService.insertConfigHistoryAtomic(0, configInfo, srcIp, srcUser, now, "I",
                        Constants.GRAY, ConfigExtInfoUtil.getExtInfoFromGrayInfo(grayNameTmp, grayRuleTmp, srcUser));
                
                return getGrayOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp);
            } catch (Exception e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw e;
            }
        });
    }
    
    @Override
    public void addConfigInfoGrayAtomic(long configGrayId, ConfigInfo configInfo, String grayName, String grayRule,
            String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        final String encryptedDataKey =
                configInfo.getEncryptedDataKey() == null ? StringUtils.EMPTY : configInfo.getEncryptedDataKey();
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        jt.update(configInfoGrayMapper.insert(
                        Arrays.asList("data_id", "group_id", "tenant_id", "gray_name", "gray_rule", "app_name", "content",
                                "encrypted_data_key", "md5", "src_ip", "src_user", "gmt_create@NOW()", "gmt_modified@NOW()")),
                configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayName, grayRule, appNameTmp,
                configInfo.getContent(), encryptedDataKey, md5, srcIp, srcUser);
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateGray(final ConfigInfo configInfo, final String grayName,
            final String grayRule, final String srcIp, final String srcUser) {
        if (findConfigInfo4GrayState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), grayName)
                == null) {
            return addConfigInfo4Gray(configInfo, grayName, grayRule, srcIp, srcUser);
        } else {
            return updateConfigInfo4Gray(configInfo, grayName, grayRule, srcIp, srcUser);
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateGrayCas(final ConfigInfo configInfo, final String grayName,
            final String grayRule, final String srcIp, final String srcUser) {
        if (findConfigInfo4GrayState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), grayName)
                == null) {
            return addConfigInfo4Gray(configInfo, grayName, grayRule, srcIp, srcUser);
        } else {
            return updateConfigInfo4GrayCas(configInfo, grayName, grayRule, srcIp, srcUser);
        }
    }
    
    @Override
    public void removeConfigInfoGray(final String dataId, final String group, final String tenant,
            final String grayName, final String srcIp, final String srcUser) {
        tjt.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
                String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName;
                try {
                    ConfigInfoGrayWrapper oldConfigAllInfo4Gray = findConfigInfo4Gray(dataId, group, tenantTmp,
                            grayNameTmp);
                    if (oldConfigAllInfo4Gray == null) {
                        return;
                    }
                    
                    ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(
                            dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_GRAY);
                    jt.update(
                            configInfoGrayMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "gray_name")),
                            dataId, group, tenantTmp, grayNameTmp);
                    
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo4Gray.getId(),
                            oldConfigAllInfo4Gray, srcIp, srcUser, now, "D", Constants.GRAY,
                            ConfigExtInfoUtil.getExtInfoFromGrayInfo(oldConfigAllInfo4Gray.getGrayName(),
                                    oldConfigAllInfo4Gray.getGrayRule(), oldConfigAllInfo4Gray.getSrcUser()));
                } catch (CannotGetJdbcConnectionException e) {
                    LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                    throw e;
                }
            }
        });
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4Gray(ConfigInfo configInfo, String grayName, String grayRule,
            String srcIp, String srcUser) {
        return tjt.execute(status -> {
            String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
            String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
            String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
            String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
            try {
                ConfigInfoGrayWrapper oldConfigAllInfo4Gray = findConfigInfo4Gray(configInfo.getDataId(),
                        configInfo.getGroup(), tenantTmp, grayNameTmp);
                if (oldConfigAllInfo4Gray == null) {
                    if (LogUtil.FATAL_LOG.isErrorEnabled()) {
                        LogUtil.FATAL_LOG.error("expected config info[dataid:{}, group:{}, tenent:{}] but not found.",
                                configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant());
                    }
                }
                
                String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
                ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(
                        dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_GRAY);
                jt.update(configInfoGrayMapper.update(
                                Arrays.asList("content", "encrypted_data_key", "md5", "src_ip", "src_user",
                                        "gmt_modified@NOW()", "app_name", "gray_rule"),
                                Arrays.asList("data_id", "group_id", "tenant_id", "gray_name")), configInfo.getContent(),
                        configInfo.getEncryptedDataKey(), md5, srcIp, srcUser, appNameTmp, grayRuleTmp,
                        configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp);
                
                Timestamp now = new Timestamp(System.currentTimeMillis());
                historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo4Gray.getId(),
                        oldConfigAllInfo4Gray, srcIp, srcUser, now, "U", Constants.GRAY,
                        ConfigExtInfoUtil.getExtInfoFromGrayInfo(oldConfigAllInfo4Gray.getGrayName(),
                                oldConfigAllInfo4Gray.getGrayRule(), oldConfigAllInfo4Gray.getSrcUser()));
                
                return getGrayOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp);
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw e;
            }
        });
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4GrayCas(ConfigInfo configInfo, String grayName, String grayRule,
            String srcIp, String srcUser) {
        return tjt.execute(status -> {
            String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
            String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
            String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
            String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
            try {
                String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
                ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(
                        dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO_GRAY);
                Timestamp time = new Timestamp(System.currentTimeMillis());
                
                MapperContext context = new MapperContext();
                context.putUpdateParameter(FieldConstant.CONTENT, configInfo.getContent());
                context.putUpdateParameter(FieldConstant.MD5, md5);
                context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
                context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
                context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
                
                context.putWhereParameter(FieldConstant.DATA_ID, configInfo.getDataId());
                context.putWhereParameter(FieldConstant.GROUP_ID, configInfo.getGroup());
                context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
                context.putWhereParameter(FieldConstant.GRAY_NAME, grayNameTmp);
                context.putWhereParameter(FieldConstant.GRAY_RULE, grayRuleTmp);
                context.putWhereParameter(FieldConstant.MD5, configInfo.getMd5());
                
                final MapperResult mapperResult = configInfoGrayMapper.updateConfigInfo4GrayCas(context);
                boolean success = jt.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) > 0;
                
                ConfigInfoGrayWrapper oldConfigAllInfo4Gray = findConfigInfo4Gray(configInfo.getDataId(),
                        configInfo.getGroup(), tenantTmp, grayNameTmp);
                if (oldConfigAllInfo4Gray == null) {
                    if (LogUtil.FATAL_LOG.isErrorEnabled()) {
                        LogUtil.FATAL_LOG.error("expected config info[dataid:{}, group:{}, tenent:{}] but not found.",
                                configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant());
                    }
                }
                
                Timestamp now = new Timestamp(System.currentTimeMillis());
                historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo4Gray.getId(),
                        oldConfigAllInfo4Gray, srcIp, srcUser, now, "U", Constants.GRAY,
                        ConfigExtInfoUtil.getExtInfoFromGrayInfo(oldConfigAllInfo4Gray.getGrayName(),
                                oldConfigAllInfo4Gray.getGrayRule(), oldConfigAllInfo4Gray.getSrcUser()));
                
                if (success) {
                    return getGrayOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp);
                } else {
                    return new ConfigOperateResult(false);
                }
            } catch (CannotGetJdbcConnectionException e) {
                LogUtil.FATAL_LOG.error("[db-error] " + e, e);
                throw e;
            }
        });
    }
    
    @Override
    public ConfigInfoGrayWrapper findConfigInfo4Gray(final String dataId, final String group, final String tenant,
            final String grayName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        try {
            ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_GRAY);
            return this.jt.queryForObject(configInfoGrayMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "gray_name", "gray_rule", "app_name",
                                    "content", "md5", "encrypted_data_key", "gmt_modified", "src_user"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "gray_name")),
                    new Object[] {dataId, group, tenantTmp, grayNameTmp}, CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public int configInfoGrayCount() {
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        String sql = configInfoGrayMapper.count(null);
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoGrayCount error");
        }
        return result;
    }
    
    @Override
    public Page<ConfigInfoGrayWrapper> findAllConfigInfoGrayForDumpAll(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        String sqlCountRows = configInfoGrayMapper.count(null);
        MapperResult sqlFetchRows = configInfoGrayMapper.findAllConfigInfoGrayForDumpAllFetchRows(
                new MapperContext(startRow, pageSize));
        
        PaginationHelper<ConfigInfoGrayWrapper> helper = createPaginationHelper();
        
        try {
            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows.getSql(), sqlFetchRows.getParamList().toArray(),
                    pageNo, pageSize, CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfoGrayWrapper> findChangeConfig(final Timestamp startTime, long lastMaxId, final int pageSize) {
        try {
            ConfigInfoGrayMapper configInfoMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_GRAY);
            
            MapperContext context = new MapperContext();
            context.putWhereParameter(FieldConstant.START_TIME, startTime);
            context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
            context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
            
            MapperResult mapperResult = configInfoMapper.findChangeConfig(context);
            return jt.query(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                    CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public List<String> findConfigInfoGrays(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        String selectSql = configInfoGrayMapper.select(Collections.singletonList("gray_name"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        return jt.queryForList(selectSql, new Object[] {dataId, group, tenantTmp}, String.class);
    }
    
}
