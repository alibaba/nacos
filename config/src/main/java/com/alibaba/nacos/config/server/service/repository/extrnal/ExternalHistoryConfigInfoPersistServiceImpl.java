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
import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.HistoryConfigInfoMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.HISTORY_DETAIL_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.HISTORY_LIST_ROW_MAPPER;

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
        Boolean isDataSourceLogEnable = EnvUtil.getProperty(Constants.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class,
                false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new ExternalStoragePaginationHelperImpl<>(jt);
    }
    
    @Override
    public List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list) {
        List<ConfigInfo> configs = new ArrayList<>();
        for (Map<String, Object> map : list) {
            String dataId = (String) map.get("data_id");
            String group = (String) map.get("group_id");
            String tenant = (String) map.get("tenant_id");
            ConfigInfo config = new ConfigInfo();
            config.setDataId(dataId);
            config.setGroup(group);
            config.setTenant(tenant);
            configs.add(config);
        }
        return configs;
    }
    
    @Override
    public void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser,
            final Timestamp time, String ops) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        String encryptedDataKey = StringUtils.isBlank(configInfo.getEncryptedDataKey()) ? StringUtils.EMPTY
                : configInfo.getEncryptedDataKey();
        
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
        String sql = historyConfigInfoMapper.removeConfigHistory();
        PaginationHelper<Object> paginationHelper = createPaginationHelper();
        paginationHelper.updateLimit(sql, new Object[] {startTime, limitSize});
    }
    
    @Override
    public List<ConfigInfo> findDeletedConfig(final Timestamp startTime, final Timestamp endTime) {
        try {
            HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                    dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
            List<Map<String, Object>> list = jt.queryForList(historyConfigInfoMapper.findDeletedConfig(),
                    new Object[] {startTime, endTime});
            return convertDeletedConfig(list);
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
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sqlCountRows = historyConfigInfoMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        String sqlFetchRows = historyConfigInfoMapper.pageFindConfigHistoryFetchRows(pageNo, pageSize);
        
        Page<ConfigHistoryInfo> page = null;
        try {
            page = helper.fetchPage(sqlCountRows, sqlFetchRows, new Object[] {dataId, group, tenantTmp}, pageNo,
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
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[detail-config-history] error, nid:{}", new Object[] {nid}, e);
            throw e;
        }
    }
    
    @Override
    public ConfigHistoryInfo detailPreviousConfigHistory(Long id) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sqlFetchRows = historyConfigInfoMapper.detailPreviousConfigHistory();
        try {
            ConfigHistoryInfo historyInfo = jt.queryForObject(sqlFetchRows, new Object[] {id},
                    HISTORY_DETAIL_ROW_MAPPER);
            return historyInfo;
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[detail-previous-config-history] error, id:{}", new Object[] {id}, e);
            throw e;
        }
    }
    
    @Override
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        HistoryConfigInfoMapper historyConfigInfoMapper = mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.HIS_CONFIG_INFO);
        String sql = historyConfigInfoMapper.findConfigHistoryCountByTime();
        Integer result = jt.queryForObject(sql, Integer.class, new Object[] {startTime});
        if (result == null) {
            throw new IllegalArgumentException("findConfigHistoryCountByTime error");
        }
        return result.intValue();
    }
}
