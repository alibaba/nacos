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
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
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
import com.alibaba.nacos.plugin.datasource.mapper.HistoryConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_DETAIL_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_LIST_ROW_MAPPER;

/**
 * ExternalHistoryConfigInfoPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalHistoryConfigInfoPersistServiceImpl")
public class ExternalHistoryConfigInfoPersistServiceImpl implements HistoryConfigInfoPersistService {
    
    private DataSourceService dataSourceService;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private MapperManager mapperManager;
    
    public ExternalHistoryConfigInfoPersistServiceImpl() {
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
    public void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser,
            final Timestamp time, String ops) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String encryptedDataKey = StringUtils.defaultEmptyIfBlank(configInfo.getEncryptedDataKey());
        
        try {
            HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
            jt.update(historyConfigInfoMapper.insert(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip",
                                    "src_user", "gmt_modified", "op_type", "encrypted_data_key")), id, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(), md5Tmp, srcIp, srcUser, time,
                    ops, encryptedDataKey);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, limitSize);
        MapperResult mapperResult = historyConfigInfoMapper.removeConfigHistory(context);
        PaginationHelper<Object> paginationHelper = createPaginationHelper();
        paginationHelper.updateLimit(mapperResult.getSql(), mapperResult.getParamList().toArray());
    }
    
    @Override
    public List<ConfigInfoStateWrapper> findDeletedConfig(final Timestamp startTime, long startId, int pageSize) {
        try {
            HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
            MapperContext context = new MapperContext();
            context.putWhereParameter(FieldConstant.START_TIME, startTime);
            context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
            context.putWhereParameter(FieldConstant.LAST_MAX_ID, startId);
            
            MapperResult mapperResult = historyConfigInfoMapper.findDeletedConfig(context);
            return jt.query(mapperResult.getSql(), mapperResult.getParamList().toArray(),
                    CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
            int pageSize) {
        PaginationHelper<ConfigHistoryInfo> helper = createPaginationHelper();
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        MapperContext context = new MapperContext((pageNo - 1) * pageSize, pageSize);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, group);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
        
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        
        String sqlCountRows = historyConfigInfoMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        MapperResult sqlFetchRows = historyConfigInfoMapper.pageFindConfigHistoryFetchRows(context);
        
        Page<ConfigHistoryInfo> page;
        try {
            page = helper.fetchPage(sqlCountRows, sqlFetchRows.getSql(), sqlFetchRows.getParamList().toArray(), pageNo,
                    pageSize, HISTORY_LIST_ROW_MAPPER);
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[list-config-history] error, dataId:{}, group:{}", new Object[] {dataId, group},
                    e);
            throw e;
        }
        return page;
    }
    
    @Override
    public ConfigHistoryInfo detailConfigHistory(Long nid) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sqlFetchRows = historyConfigInfoMapper.select(
                Arrays.asList("nid", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_user",
                        "src_ip", "op_type", "gmt_create", "gmt_modified", "encrypted_data_key"),
                Collections.singletonList("nid"));
        try {
            ConfigHistoryInfo historyInfo = jt.queryForObject(sqlFetchRows, new Object[] {nid},
                    HISTORY_DETAIL_ROW_MAPPER);
            return historyInfo;
        } catch (EmptyResultDataAccessException emptyResultDataAccessException) {
            return null;
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[detail-config-history] error, nid:{}", new Object[] {nid}, e);
            throw e;
        }
        
    }
    
    @Override
    public ConfigHistoryInfo detailPreviousConfigHistory(Long id) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, id);
        MapperResult sqlFetchRows = historyConfigInfoMapper.detailPreviousConfigHistory(context);
        try {
            ConfigHistoryInfo historyInfo = jt.queryForObject(sqlFetchRows.getSql(),
                    sqlFetchRows.getParamList().toArray(), HISTORY_DETAIL_ROW_MAPPER);
            return historyInfo;
        } catch (EmptyResultDataAccessException emptyResultDataAccessException) {
            return null;
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[detail-previous-config-history] error, id:{}", new Object[] {id}, e);
            throw e;
        }
    }
    
    @Override
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        
        MapperResult mapperResult = historyConfigInfoMapper.findConfigHistoryCountByTime(context);
        Integer result = jt.queryForObject(mapperResult.getSql(), mapperResult.getParamList().toArray(), Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("findConfigHistoryCountByTime error");
        }
        return result;
    }
}
