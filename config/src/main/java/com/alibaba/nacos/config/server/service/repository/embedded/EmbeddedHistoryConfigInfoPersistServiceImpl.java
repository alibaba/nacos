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
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExtInfoUtil;
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
import com.alibaba.nacos.plugin.datasource.mapper.HistoryConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_DETAIL_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_LIST_ROW_MAPPER;

/**
 * EmbeddedHistoryConfigInfoPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings({"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service("embeddedHistoryConfigInfoPersistServiceImpl")
public class EmbeddedHistoryConfigInfoPersistServiceImpl implements HistoryConfigInfoPersistService {
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    private MapperManager mapperManager;
    
    /**
     * The constructor sets the dependency injection order.
     *
     * @param databaseOperate databaseOperate.
     */
    public EmbeddedHistoryConfigInfoPersistServiceImpl(DatabaseOperate databaseOperate) {
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
    public void insertConfigHistoryAtomic(long configHistoryId, ConfigInfo configInfo, String srcIp, String srcUser,
            final Timestamp time, String ops, String publishType, String extInfo) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String publishTypeTmp = StringUtils.defaultEmptyIfBlank(publishType);
        String encryptedDataKey = StringUtils.defaultEmptyIfBlank(configInfo.getEncryptedDataKey());
        
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        final String sql = historyConfigInfoMapper.insert(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_ip",
                        "src_user", "gmt_modified", "op_type", "publish_type", "ext_info", "encrypted_data_key"));
        final Object[] args = new Object[] {configHistoryId, configInfo.getDataId(), configInfo.getGroup(), tenantTmp,
                appNameTmp, configInfo.getContent(), md5Tmp, srcIp, srcUser, time, ops, publishTypeTmp, extInfo, encryptedDataKey};
        
        EmbeddedStorageContextHolder.addSqlContext(sql, args);
    }
    
    @Override
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, limitSize);
        MapperResult mapperResult = historyConfigInfoMapper.removeConfigHistory(context);
        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
        helper.updateLimit(mapperResult.getSql(), mapperResult.getParamList().toArray());
    }
    
    @Override
    public List<ConfigInfoStateWrapper> findDeletedConfig(final Timestamp startTime, long lastMaxId,
            final int pageSize, String publishType) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        context.putWhereParameter(FieldConstant.PUBLISH_TYPE, publishType);
        
        MapperResult mapperResult = historyConfigInfoMapper.findDeletedConfig(context);
        List<ConfigHistoryInfo> configHistoryInfos = databaseOperate.queryMany(mapperResult.getSql(),
                mapperResult.getParamList().toArray(), HISTORY_DETAIL_ROW_MAPPER);
        
        List<ConfigInfoStateWrapper> configInfoStateWrappers = new ArrayList<>();
        for (ConfigHistoryInfo configHistoryInfo : configHistoryInfos) {
            ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
            configInfoStateWrapper.setId(configHistoryInfo.getId());
            configInfoStateWrapper.setDataId(configHistoryInfo.getDataId());
            configInfoStateWrapper.setGroup(configHistoryInfo.getGroup());
            configInfoStateWrapper.setTenant(configHistoryInfo.getTenant());
            configInfoStateWrapper.setMd5(configHistoryInfo.getMd5());
            configInfoStateWrapper.setLastModified(configHistoryInfo.getLastModifiedTime().getTime());
            configInfoStateWrapper.setGrayName(ConfigExtInfoUtil.extractGrayName(configHistoryInfo.getExtInfo()));
            configInfoStateWrappers.add(configInfoStateWrapper);
        }
        return configInfoStateWrappers;
    }
    
    @Override
    public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
            int pageSize) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        
        MapperContext context = new MapperContext((pageNo - 1) * pageSize, pageSize);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, group);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantTmp);
        
        String sqlCountRows = historyConfigInfoMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        MapperResult sqlFetchRows = historyConfigInfoMapper.pageFindConfigHistoryFetchRows(context);
        
        PaginationHelper<ConfigHistoryInfo> helper = createPaginationHelper();
        return helper.fetchPage(sqlCountRows, sqlFetchRows.getSql(), sqlFetchRows.getParamList().toArray(), pageNo,
                pageSize, HISTORY_LIST_ROW_MAPPER);
    }
    
    @Override
    public ConfigHistoryInfo detailConfigHistory(Long nid) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sqlFetchRows = historyConfigInfoMapper.select(
                Arrays.asList("nid", "data_id", "group_id", "tenant_id", "app_name", "content", "md5", "src_user",
                        "src_ip", "op_type", "publish_type", "ext_info", "gmt_create", "gmt_modified", "encrypted_data_key"),
                Collections.singletonList("nid"));
        return databaseOperate.queryOne(sqlFetchRows, new Object[] {nid}, HISTORY_DETAIL_ROW_MAPPER);
    }
    
    @Override
    public ConfigHistoryInfo detailPreviousConfigHistory(Long id) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.ID, id);
        MapperResult sqlFetchRows = historyConfigInfoMapper.detailPreviousConfigHistory(context);
        return databaseOperate.queryOne(sqlFetchRows.getSql(), sqlFetchRows.getParamList().toArray(),
                HISTORY_DETAIL_ROW_MAPPER);
    }
    
    @Override
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        MapperResult sqlFetchRows = historyConfigInfoMapper.findConfigHistoryCountByTime(context);
        Integer result = databaseOperate.queryOne(sqlFetchRows.getSql(), sqlFetchRows.getParamList().toArray(),
                Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("findConfigHistoryCountByTime error");
        }
        return result;
    }
}
