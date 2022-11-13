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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoAggrMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_AGGR_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO_CHANGED_ROW_MAPPER;

/**
 * ExternalConfigInfoAggrPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalConfigInfoAggrPersistServiceImpl")
public class ExternalConfigInfoAggrPersistServiceImpl implements ConfigInfoAggrPersistService {
    
    private DataSourceService dataSourceService;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private MapperManager mapperManager;
    
    public ExternalConfigInfoAggrPersistServiceImpl() {
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
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String select = configInfoAggrMapper.select(Collections.singletonList("content"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        String insert = configInfoAggrMapper.insert(
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content", "gmt_modified"));
        String update = configInfoAggrMapper.update(Arrays.asList("content", "gmt_modified"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        try {
            try {
                String dbContent = jt.queryForObject(select, new Object[] {dataId, group, tenantTmp, datumId},
                        String.class);
                
                if (dbContent != null && dbContent.equals(content)) {
                    return true;
                } else {
                    return jt.update(update, content, now, dataId, group, tenantTmp, datumId) > 0;
                }
            } catch (EmptyResultDataAccessException ex) { // no data, insert
                return jt.update(insert, dataId, group, tenantTmp, datumId, appNameTmp, content, now) > 0;
            }
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public boolean batchPublishAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        try {
            Boolean isPublishOk = tjt.execute(status -> {
                for (Map.Entry<String, String> entry : datumMap.entrySet()) {
                    try {
                        if (!addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName, entry.getValue())) {
                            throw new TransactionSystemException("error in batchPublishAggr");
                        }
                    } catch (Throwable e) {
                        throw new TransactionSystemException("error in batchPublishAggr");
                    }
                }
                return Boolean.TRUE;
            });
            if (isPublishOk == null) {
                return false;
            }
            return isPublishOk;
        } catch (TransactionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            return false;
        }
    }
    
    @Override
    public boolean replaceAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        try {
            ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_AGGR);
            Boolean isReplaceOk = tjt.execute(status -> {
                try {
                    String appNameTmp = appName == null ? "" : appName;
                    removeAggrConfigInfo(dataId, group, tenant);
                    String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
                    String sql = configInfoAggrMapper.insert(
                            Arrays.asList("data_id", "group_id", "tenant_id", "datum_id", "app_name", "content",
                                    "gmt_modified"));
                    for (Map.Entry<String, String> datumEntry : datumMap.entrySet()) {
                        jt.update(sql, dataId, group, tenantTmp, datumEntry.getKey(), appNameTmp, datumEntry.getValue(),
                                new Timestamp(System.currentTimeMillis()));
                    }
                } catch (Throwable e) {
                    throw new TransactionSystemException("error in addAggrConfigInfo");
                }
                return Boolean.TRUE;
            });
            if (isReplaceOk == null) {
                return false;
            }
            return isReplaceOk;
        } catch (TransactionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            return false;
        }
        
    }
    
    @Override
    public void removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant,
            final String datumId) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        try {
            this.jt.update(sql, ps -> {
                int index = 1;
                ps.setString(index++, dataId);
                ps.setString(index++, group);
                ps.setString(index++, tenantTmp);
                ps.setString(index, datumId);
            });
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id"));
        
        try {
            this.jt.update(sql, ps -> {
                int index = 1;
                ps.setString(index++, dataId);
                ps.setString(index++, group);
                ps.setString(index, tenantTmp);
            });
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public boolean batchRemoveAggr(final String dataId, final String group, final String tenant,
            final List<String> datumList) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final StringBuilder datumString = new StringBuilder();
        for (String datum : datumList) {
            datumString.append('\'').append(datum).append("',");
        }
        datumString.deleteCharAt(datumString.length() - 1);
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        final String sql = configInfoAggrMapper.batchRemoveAggr(datumList);
        try {
            jt.update(sql, dataId, group, tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            return false;
        }
        return true;
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.count(Arrays.asList("data_id", "group_id", "tenant_id"));
        Integer result = jt.queryForObject(sql, Integer.class, new Object[] {dataId, group, tenantTmp});
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
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
        Integer result = jt.queryForObject(sql, Integer.class, objectList.toArray());
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.select(
                Arrays.asList("id", "data_id", "group_id", "tenant_id", "datum_id", "app_name", "content"),
                Arrays.asList("data_id", "group_id", "tenant_id", "datum_id"));
        
        try {
            return this.jt.queryForObject(sql, new Object[] {dataId, group, tenantTmp, datumId},
                    CONFIG_INFO_AGGR_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            // EmptyResultDataAccessException, indicating that the data does not exist, returns null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findConfigInfoAggrIsOrdered();
        
        try {
            return this.jt.query(sql, new Object[] {dataId, group, tenantTmp}, CONFIG_INFO_AGGR_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
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
        PaginationHelper<ConfigInfoAggr> helper = this.createPaginationHelper();
        try {
            return helper.fetchPageLimit(sqlCountRows, new Object[] {dataId, group, tenantTmp}, sqlFetchRows,
                    new Object[] {dataId, group, tenantTmp}, pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
            boolean blacklist) {
        
        String sqlCountRows = "SELECT count(*) FROM config_info_aggr WHERE ";
        String sqlFetchRows = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE ";
        StringBuilder where = new StringBuilder(" 1=1 ");
        // Whitelist, please leave the synchronization condition empty, there is no configuration that meets the conditions
        if (configKeys.length == 0 && blacklist == false) {
            Page<ConfigInfoAggr> page = new Page<>();
            page.setTotalCount(0);
            return page;
        }
        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
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
        
        try {
            Page<ConfigInfoAggr> result = helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(),
                    pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
            return result;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public List<ConfigInfoChanged> findAllAggrGroup() {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.findAllAggrGroupByDistinct();
        
        try {
            return jt.query(sql, new Object[] {}, CONFIG_INFO_CHANGED_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public List<String> findDatumIdByContent(String dataId, String groupId, String content) {
        ConfigInfoAggrMapper configInfoAggrMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_AGGR);
        String sql = configInfoAggrMapper.select(Collections.singletonList("datum_id"),
                Arrays.asList("data_id", "group_id", "content"));
        
        try {
            return this.jt.queryForList(sql, new Object[] {dataId, groupId, content}, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
}
