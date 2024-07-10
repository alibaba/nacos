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
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
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
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoTagMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER;

/**
 * ExternalConfigInfoTagPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalConfigInfoTagPersistServiceImpl")
public class ExternalConfigInfoTagPersistServiceImpl implements ConfigInfoTagPersistService {
    
    private DataSourceService dataSourceService;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private MapperManager mapperManager;
    
    public ExternalConfigInfoTagPersistServiceImpl() {
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
    public ConfigInfoStateWrapper findConfigInfo4TagState(final String dataId, final String group, final String tenant,
            String tag) {
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_TAG);
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            return this.jt.queryForObject(
                    configInfoTagMapper.select(Arrays.asList("id", "data_id", "group_id", "tenant_id", "gmt_modified"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "tag_id")),
                    new Object[] {dataId, group, tenantTmp, tag}, CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    private ConfigOperateResult getTagOperateResult(String dataId, String group, String tenant, String tag) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        ConfigInfoStateWrapper configInfo4Tag = this.findConfigInfo4TagState(dataId, group, tenantTmp, tag);
        if (configInfo4Tag == null) {
            return new ConfigOperateResult(false);
        }
        return new ConfigOperateResult(configInfo4Tag.getId(), configInfo4Tag.getLastModified());
        
    }
    
    @Override
    public ConfigOperateResult addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        try {
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            
            jt.update(configInfoTagMapper.insert(
                            Arrays.asList("data_id", "group_id", "tenant_id", "tag_id", "app_name", "content", "md5", "src_ip",
                                    "src_user", "gmt_create", "gmt_modified")), configInfo.getDataId(), configInfo.getGroup(),
                    tenantTmp, tagTmp, appNameTmp, configInfo.getContent(), md5, srcIp, srcUser, time, time);
            return getTagOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser) {
        if (findConfigInfo4TagState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), tag)
                == null) {
            return addConfigInfo4Tag(configInfo, tag, srcIp, srcUser);
        } else {
            return updateConfigInfo4Tag(configInfo, tag, srcIp, srcUser);
        }
    }
    
    @Override
    public ConfigOperateResult insertOrUpdateTagCas(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser) {
        if (findConfigInfo4TagState(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), tag)
                == null) {
            return addConfigInfo4Tag(configInfo, tag, srcIp, srcUser);
        } else {
            return updateConfigInfo4TagCas(configInfo, tag, srcIp, srcUser);
        }
    }
    
    @Override
    public void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
            final String srcIp, final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag;
        try {
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            jt.update(configInfoTagMapper.delete(Arrays.asList("data_id", "group_id", "tenant_id", "tag_id")), dataId,
                    group, tenantTmp, tagTmp);
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            Timestamp time = new Timestamp(System.currentTimeMillis());
            jt.update(configInfoTagMapper.update(
                            Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified", "app_name"),
                            Arrays.asList("data_id", "group_id", "tenant_id", "tag_id")), configInfo.getContent(), md5, srcIp,
                    srcUser, time, appNameTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp);
            return getTagOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigOperateResult updateConfigInfo4TagCas(ConfigInfo configInfo, String tag, String srcIp,
            String srcUser) {
        String appNameTmp = StringUtils.defaultEmptyIfBlank(configInfo.getAppName());
        String tenantTmp = StringUtils.defaultEmptyIfBlank(configInfo.getTenant());
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
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
            context.putWhereParameter(FieldConstant.TAG_ID, tagTmp);
            context.putWhereParameter(FieldConstant.MD5, configInfo.getMd5());
            
            final MapperResult mapperResult = configInfoTagMapper.updateConfigInfo4TagCas(context);
            
            boolean success = jt.update(mapperResult.getSql(), mapperResult.getParamList().toArray()) > 0;
            if (success) {
                return getTagOperateResult(configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp);
            } else {
                return new ConfigOperateResult(false);
            }
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public ConfigInfoTagWrapper findConfigInfo4Tag(final String dataId, final String group, final String tenant,
            final String tag) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO_TAG);
            return this.jt.queryForObject(configInfoTagMapper.select(
                            Arrays.asList("id", "data_id", "group_id", "tenant_id", "tag_id", "app_name", "content",
                                    "gmt_modified"), Arrays.asList("data_id", "group_id", "tenant_id", "tag_id")),
                    new Object[] {dataId, group, tenantTmp, tagTmp}, CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public int configInfoTagCount() {
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_TAG);
        String sql = configInfoTagMapper.count(null);
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoTagCount error");
        }
        return result;
    }
    
    @Override
    public Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo, final int pageSize) {
        final int startRow = (pageNo - 1) * pageSize;
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_TAG);
        String sqlCountRows = configInfoTagMapper.count(null);
        MapperResult sqlFetchRows = configInfoTagMapper.findAllConfigInfoTagForDumpAllFetchRows(
                new MapperContext(startRow, pageSize));
        
        PaginationHelper<ConfigInfoTagWrapper> helper = createPaginationHelper();
        
        try {
            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows.getSql(), sqlFetchRows.getParamList().toArray(),
                    pageNo, pageSize, CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
            
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public List<String> findConfigInfoTags(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfoTagMapper configInfoTagMapper = mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO_TAG);
        String selectSql = configInfoTagMapper.select(Collections.singletonList("tag_id"),
                Arrays.asList("data_id", "group_id", "tenant_id"));
        return jt.queryForList(selectSql, new Object[] {dataId, group, tenantTmp}, String.class);
    }
    
}
