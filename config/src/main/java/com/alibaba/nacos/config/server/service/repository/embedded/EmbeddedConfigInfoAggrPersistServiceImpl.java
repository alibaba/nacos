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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.event.DerbyImportEvent;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoAggrMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_AGGR_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_CHANGED_ROW_MAPPER;

/**
 * EmbeddedConfigInfoAggrPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings({"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Service("embeddedConfigInfoAggrPersistServiceImpl")
public class EmbeddedConfigInfoAggrPersistServiceImpl implements ConfigInfoAggrPersistService {
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    private MapperManager mapperManager;
    
    /**
     * The constructor sets the dependency injection order.
     *
     * @param databaseOperate {@link EmbeddedStoragePersistServiceImpl}
     */
    public EmbeddedConfigInfoAggrPersistServiceImpl(DatabaseOperate databaseOperate) {
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
    public String generateLikeArgument(String s) {
        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }
    
    @Override
    public boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
            String appName, final String content) {
        String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String contentTmp = StringUtils.isBlank(content) ? StringUtils.EMPTY : content;
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String select = configInfoAggrMapper.select(Collections.singletonList("content"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        final String insert = configInfoAggrMapper.insert(
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content", "gmt_modified"));
        final String update = configInfoAggrMapper.update(Arrays.asList("content", "gmt_modified"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        String dbContent = databaseOperate.queryOne(select, new Object[] {dataId, group, tenantTmp, datumId},
                String.class);
        
        if (Objects.isNull(dbContent)) {
            final Object[] args = new Object[] {dataId, group, tenantTmp, datumId, appNameTmp, contentTmp, now};
            EmbeddedStorageContextUtils.addSqlContext(insert, args);
        } else if (!dbContent.equals(content)) {
            final Object[] args = new Object[] {contentTmp, now, dataId, group, tenantTmp, datumId};
            EmbeddedStorageContextUtils.addSqlContext(update, args);
        }
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[Merge] Configuration release failed");
            }
            return true;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public boolean batchPublishAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        try {
            Boolean isPublishOk = false;
            for (Map.Entry<String, String> entry : datumMap.entrySet()) {
                addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName, entry.getValue());
            }
            
            isPublishOk = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            
            if (isPublishOk == null) {
                return false;
            }
            return isPublishOk;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public boolean replaceAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        Boolean isReplaceOk = false;
        String appNameTmp = appName == null ? "" : appName;
        
        removeAggrConfigInfo(dataId, group, tenant);
        
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.insert(
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content", "gmt_modified"));
        for (Map.Entry<String, String> datumEntry : datumMap.entrySet()) {
            final Object[] args = new Object[] {dataId, group, tenantTmp, datumEntry.getKey(), appNameTmp,
                    datumEntry.getValue(), new Timestamp(System.currentTimeMillis())};
            EmbeddedStorageContextUtils.addSqlContext(sql, args);
        }
        try {
            isReplaceOk = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            
            if (isReplaceOk == null) {
                return false;
            }
            return isReplaceOk;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
        
    }
    
    @Override
    public void removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant,
            final String datumId) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp, datumId};
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[aggregation with single] Configuration deletion failed");
            }
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
        final Object[] args = new Object[] {dataId, group, tenantTmp};
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[aggregation with all] Configuration deletion failed");
            }
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public boolean batchRemoveAggr(final String dataId, final String group, final String tenant,
            final List<String> datumList) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.batchRemoveAggr(datumList.size());
        final Object[] args = new Object[3 + datumList.size()];
        args[0] = dataId;
        args[1] = group;
        args[2] = tenantTmp;
        System.arraycopy(datumList.toArray(), 0, args, 3, datumList.size());
        EmbeddedStorageContextUtils.addSqlContext(sql, args);
        
        try {
            boolean result = databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("[aggregation] Failed to configure batch deletion");
            }
            return true;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        Integer result = databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result;
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds, boolean isIn) {
        if (datumIds == null || datumIds.isEmpty()) {
            return 0;
        }
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.aggrConfigInfoCount(datumIds.size(), isIn);
        
        List<Object> objectList = com.alibaba.nacos.common.utils.CollectionUtils.list(dataId, group, tenantTmp);
        objectList.addAll(datumIds);
        
        Integer result = databaseOperate.queryOne(sql, objectList.toArray(), Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result;
    }
    
    @Override
    public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "datum_id", "app_name", "content"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        return databaseOperate.queryOne(sql, new Object[] {dataId, group, tenantTmp, datumId},
                CONFIG_INFO_AGGR_ROW_MAPPER);
        
    }
    
    @Override
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findConfigInfoAggrIsOrdered();
        
        return databaseOperate.queryMany(sql, new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_AGGR_ROW_MAPPER);
        
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
            final int pageSize) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final int startRow = (pageNo - 1) * pageSize;
        String sqlCountRows = configInfoAggrMapper.select(Arrays.asList("count(*)"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        String sqlFetchRows = configInfoAggrMapper.findConfigInfoAggrByPageFetchRows(startRow, pageSize);
        
        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
        return helper.fetchPageLimit(sqlCountRows, new Object[] {dataId, group, tenantTmp}, sqlFetchRows,
                new Object[] {dataId, group, tenantTmp}, pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
            boolean blacklist) {
        
        String sqlCountRows = "SELECT count(*) FROM config_info_aggr WHERE ";
        String sqlFetchRows = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE ";
        StringBuilder where = new StringBuilder(" 1=1 ");
        // White list, please synchronize the condition is empty, there is no qualified configuration
        if (configKeys.length == 0 && blacklist == false) {
            Page<ConfigInfoAggr> page = new Page<>();
            page.setTotalCount(0);
            return page;
        }
        List<String> params = new ArrayList<>();
        boolean isFirst = true;
        
        for (ConfigKey configInfoAggr : configKeys) {
            String dataId = configInfoAggr.getDataId();
            String group = configInfoAggr.getGroup();
            String appName = configInfoAggr.getAppName();
            if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group) && StringUtils.isBlank(appName)) {
                break;
            }
            if (blacklist) {
                if (isFirst) {
                    isFirst = false;
                    where.append(" AND ");
                } else {
                    where.append(" AND ");
                }
                
                where.append('(');
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where.append(" data_id NOT LIKE ? ");
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where.append(" OR ");
                    }
                    where.append(" group_id NOT LIKE ? ");
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where.append(" OR ");
                    }
                    where.append(" app_name != ? ");
                    params.add(appName);
                    isFirstSub = false;
                }
                where.append(") ");
            } else {
                if (isFirst) {
                    isFirst = false;
                    where.append(" AND ");
                } else {
                    where.append(" OR ");
                }
                where.append('(');
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where.append(" data_id LIKE ? ");
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where.append(" AND ");
                    }
                    where.append(" group_id LIKE ? ");
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where.append(" AND ");
                    }
                    where.append(" app_name = ? ");
                    params.add(appName);
                    isFirstSub = false;
                }
                where.append(") ");
            }
        }
        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
        return helper.fetchPage(sqlCountRows + where.toString(), sqlFetchRows + where.toString(), params.toArray(),
                pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
        
    }
    
    @Override
    public List<ConfigInfoChanged> findAllAggrGroup() {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findAllAggrGroupByDistinct();
        
        return databaseOperate.queryMany(sql, EMPTY_ARRAY, CONFIG_INFO_CHANGED_ROW_MAPPER);
        
    }
    
    @Override
    public List<String> findDatumIdByContent(String dataId, String groupId, String content) {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.select(Collections.singletonList("datum_id"),
                Arrays.asList("data_id", "group_id", "content"));
        return databaseOperate.queryMany(sql, new Object[] {dataId, groupId, content}, String.class);
        
    }
}
