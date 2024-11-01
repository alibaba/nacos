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
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.utils.ConfigExtInfoUtil;
import com.alibaba.nacos.config.server.utils.LogUtil;
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
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoGrayMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;

/**
 * EmbeddedConfigInfoGrayPersistServiceImpl.
 *
 * @author rong
 */
@SuppressWarnings({"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service("embeddedConfigInfoGrayPersistServiceImpl")
public class EmbeddedConfigInfoGrayPersistServiceImpl implements ConfigInfoGrayPersistService {
    
    private static final String RESOURCE_CONFIG_HISTORY_ID = "config-history-id";
    
    private static final String RESOURCE_CONFIG_HISTORY_GRAY_ID = "config-history-gray-id";
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    private MapperManager mapperManager;
    
    private final IdGeneratorManager idGeneratorManager;
    
    private final HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    /**
     * The constructor sets the dependency injection order.
     *
     * @param databaseOperate databaseOperate.
     */
    public EmbeddedConfigInfoGrayPersistServiceImpl(DatabaseOperate databaseOperate,
            IdGeneratorManager idGeneratorManager,
            @Qualifier("embeddedHistoryConfigInfoPersistServiceImpl") HistoryConfigInfoPersistService historyConfigInfoPersistService) {
        this.databaseOperate = databaseOperate;
        this.idGeneratorManager = idGeneratorManager;
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
        idGeneratorManager.register(RESOURCE_CONFIG_HISTORY_GRAY_ID, RESOURCE_CONFIG_HISTORY_ID);
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
    public ConfigInfoStateWrapper findConfigInfo4GrayState(final String dataId, final String group, final String tenant,
            String grayName) {
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        
        String sql = configInfoGrayMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "gmt_modified"),
                Arrays.asList("data_id", "group_id", "tenant_id", "gray_name"));
        return databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp, grayNameTmp},
                CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
    }
    
    private ConfigOperateResult getGrayOperateResult(String dataId, String group, String tenant, String grayName) {
        String tenantTmp = StringUtils.defaultEmptyIfBlank(tenant);
        ConfigInfoStateWrapper configInfo4Gray = this.findConfigInfo4GrayState(dataId, group, tenantTmp, grayName);
        if (configInfo4Gray == null) {
            return new ConfigOperateResult(false);
        }
        return new ConfigOperateResult(configInfo4Gray.getId(), configInfo4Gray.getLastModified());
        
    }
    
    @Override
    public ConfigOperateResult addConfigInfo4Gray(ConfigInfo configInfo, String grayName, String grayRule, String srcIp,
            String srcUser) {
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
        
        configInfo.setTenant(tenantTmp);
        
        try {
            long configGrayId = idGeneratorManager.nextId(RESOURCE_CONFIG_HISTORY_GRAY_ID);
            long hisId = idGeneratorManager.nextId(RESOURCE_CONFIG_HISTORY_ID);
            
            addConfigInfoGrayAtomic(configGrayId, configInfo, grayNameTmp, grayRuleTmp, srcIp, srcUser);
            
            Timestamp now = new Timestamp(System.currentTimeMillis());
            historyConfigInfoPersistService.insertConfigHistoryAtomic(hisId, configInfo, srcIp, srcUser, now, "I",
                    Constants.GRAY, ConfigExtInfoUtil.getExtInfoFromGrayInfo(grayNameTmp, grayRuleTmp, srcUser));
            
            EmbeddedStorageContextUtils.onModifyConfigGrayInfo(configInfo, grayNameTmp, grayRuleTmp, srcIp, now);
            databaseOperate.blockUpdate();
            
            return getGrayOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp);
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public void addConfigInfoGrayAtomic(long configGrayId, ConfigInfo configInfo, String grayName, String grayRule,
            String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        
        final String sql = configInfoGrayMapper.insert(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "gray_name", "gray_rule", "app_name", "content",
                        "md5", "src_ip", "src_user", "gmt_create", "gmt_modified"));
        
        Timestamp time = new Timestamp(System.currentTimeMillis());
        final Object[] args = new Object[] {configGrayId, configInfo.getDataId(), configInfo.getGroup(), tenantTmp,
                grayNameTmp, grayRuleTmp, appNameTmp, configInfo.getContent(), md5, srcIp, srcUser, time, time};
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
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
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName;
        
        ConfigInfoGrayWrapper oldConfigAllInfo4Gray = findConfigInfo4Gray(dataId, group, tenantTmp, grayNameTmp);
        if (oldConfigAllInfo4Gray == null) {
            if (LogUtil.FATAL_LOG.isErrorEnabled()) {
                LogUtil.FATAL_LOG.error("expected config info[dataid:{}, group:{}, tenent:{}] but not found.", dataId,
                        group, tenant);
            }
        }
        
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        final String sql = configInfoGrayMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "gray_name"));
        final Object[] args = new Object[] {dataId, group, tenantTmp, grayNameTmp};
        
        Timestamp now = new Timestamp(System.currentTimeMillis());
        historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo4Gray.getId(), oldConfigAllInfo4Gray,
                srcIp, srcUser, now, "D", Constants.GRAY,
                ConfigExtInfoUtil.getExtInfoFromGrayInfo(oldConfigAllInfo4Gray.getGrayName(),
                        oldConfigAllInfo4Gray.getGrayRule(), oldConfigAllInfo4Gray.getSrcUser()));
        
        EmbeddedStorageContextUtils.onDeleteConfigGrayInfo(tenantTmp, group, dataId, grayNameTmp, srcIp);
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
        try {
            databaseOperate.update(EmbeddedStorageContextHolder.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4Gray(ConfigInfo configInfo, String grayName, String grayRule,
            String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
        
        configInfo.setTenant(tenantTmp);
        
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
            ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_GRAY);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            
            final String sql = configInfoGrayMapper.update(
                    Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name", "gray_rule"),
                    Arrays.asList("data_id", "group_id", "tenant_id", "gray_name"));
            final Object[] args = new Object[] {configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp,
                    grayRuleTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp};
            
            Timestamp now = new Timestamp(System.currentTimeMillis());
            historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo4Gray.getId(),
                    oldConfigAllInfo4Gray, srcIp, srcUser, now, "U", Constants.GRAY,
                    ConfigExtInfoUtil.getExtInfoFromGrayInfo(oldConfigAllInfo4Gray.getGrayName(),
                            oldConfigAllInfo4Gray.getGrayRule(), oldConfigAllInfo4Gray.getSrcUser()));
            
            EmbeddedStorageContextUtils.onModifyConfigGrayInfo(configInfo, grayNameTmp, grayRuleTmp, srcIp, time);
            EmbeddedStorageContextHolder.addSqlContext(sql, args);
            
            databaseOperate.blockUpdate();
            return getGrayOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp);
            
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4GrayCas(ConfigInfo configInfo, String grayName, String grayRule,
            String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        String grayRuleTmp = StringUtils.isBlank(grayRule) ? StringUtils.EMPTY : grayRule.trim();
        
        configInfo.setTenant(tenantTmp);
        
        try {
            final ConfigInfoGrayWrapper oldConfigAllInfo4Gray = findConfigInfo4Gray(configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp, grayNameTmp);
            if (oldConfigAllInfo4Gray == null) {
                if (LogUtil.FATAL_LOG.isErrorEnabled()) {
                    LogUtil.FATAL_LOG.error("expected config info[dataid:{}, group:{}, tenent:{}] but not found.",
                            configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant());
                }
            }
            
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_GRAY);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            
            MapperContext context = new MapperContext();
            context.putUpdateParameter(FieldConstant.CONTENT, configInfo.getContent());
            context.putUpdateParameter(FieldConstant.MD5, md5);
            context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
            context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
            context.putUpdateParameter(FieldConstant.GMT_MODIFIED, time);
            context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
            
            context.putWhereParameter(FieldConstant.DATA_ID, configInfo.getDataId());
            context.putWhereParameter(FieldConstant.GROUP_ID, configInfo.getGroup());
            context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
            context.putWhereParameter(FieldConstant.GRAY_NAME, grayNameTmp);
            context.putWhereParameter(FieldConstant.GRAY_RULE, grayRuleTmp);
            context.putWhereParameter(FieldConstant.MD5, configInfo.getMd5());
            
            final MapperResult mapperResult = configInfoGrayMapper.updateConfigInfo4GrayCas(context);
            
            Timestamp now = new Timestamp(System.currentTimeMillis());
            historyConfigInfoPersistService.insertConfigHistoryAtomic(oldConfigAllInfo4Gray.getId(),
                    oldConfigAllInfo4Gray, srcIp, srcUser, now, "U", Constants.GRAY,
                    ConfigExtInfoUtil.getExtInfoFromGrayInfo(oldConfigAllInfo4Gray.getGrayName(),
                            oldConfigAllInfo4Gray.getGrayRule(), oldConfigAllInfo4Gray.getSrcUser()));
            
            EmbeddedStorageContextUtils.onModifyConfigGrayInfo(configInfo, grayNameTmp, grayRuleTmp, srcIp, time);
            EmbeddedStorageContextHolder.addSqlContext(mapperResult.getSql(), mapperResult.getParamList().toArray());
            
            Boolean success = databaseOperate.blockUpdate();
            if (success) {
                return getGrayOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, grayNameTmp);
            } else {
                return new ConfigOperateResult(false);
            }
            
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    @Override
    public ConfigInfoGrayWrapper findConfigInfo4Gray(final String dataId, final String group, final String tenant,
            final String grayName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String grayNameTmp = StringUtils.isBlank(grayName) ? StringUtils.EMPTY : grayName.trim();
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        final String sql = configInfoGrayMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "gray_name", "gray_rule", "app_name", "content",
                        "md5", "gmt_modified", "src_user", "encrypted_data_key"),
                Arrays.asList("data_id", "group_id", "tenant_id", "gray_name"));
        
        return databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp, grayNameTmp},
                CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
    }
    
    @Override
    public int configInfoGrayCount() {
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        String sql = configInfoGrayMapper.count(null);
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
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
        return helper.fetchPageLimit(sqlCountRows, sqlFetchRows.getSql(), sqlFetchRows.getParamList().toArray(), pageNo,
                pageSize, CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
        
    }
    
    @Override
    public List<String> findConfigInfoGrays(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoGrayMapper configInfoGrayMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_GRAY);
        final String sql = configInfoGrayMapper.select(Collections.singletonList("gray_name"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        
        return databaseOperate.queryMany(sql, new Object[] {dataId, group, tenantTmp}, String.class);
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
            return databaseOperate.queryMany(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                    CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
}
