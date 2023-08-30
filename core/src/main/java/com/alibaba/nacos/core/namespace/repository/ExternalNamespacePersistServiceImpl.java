/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.repository;

import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.TenantInfoMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.alibaba.nacos.core.namespace.repository.NamespaceRowMapperInjector.TENANT_INFO_ROW_MAPPER;

/**
 * ExternalOtherPersistServiceImpl.
 *
 * @author lixiaoshuang
 */
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Service("externalOtherPersistServiceImpl")
public class ExternalNamespacePersistServiceImpl implements NamespacePersistService {
    
    private final DataSourceService dataSourceService;
    
    protected JdbcTemplate jt;
    
    protected TransactionTemplate tjt;
    
    private final MapperManager mapperManager;
    
    public ExternalNamespacePersistServiceImpl() {
        this.dataSourceService = DynamicDataSource.getInstance().getDataSource();
        this.jt = dataSourceService.getJdbcTemplate();
        this.tjt = dataSourceService.getTransactionTemplate();
        Boolean isDataSourceLogEnable = EnvUtil
                .getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false);
        this.mapperManager = MapperManager.instance(isDataSourceLogEnable);
    }
    
    @Override
    public void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
            String createResource, final long time) {
        try {
            TenantInfoMapper tenantInfoMapper = mapperManager
                    .findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
            jt.update(tenantInfoMapper.insert(Arrays
                    .asList("kp", "tenant_id", "tenant_name", "tenant_desc", "create_source", "gmt_create",
                            "gmt_modified")), kp, tenantId, tenantName, tenantDesc, createResource, time, time);
        } catch (DataAccessException e) {
            Loggers.CLUSTER.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public void removeTenantInfoAtomic(final String kp, final String tenantId) {
        try {
            TenantInfoMapper tenantInfoMapper = mapperManager
                    .findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
            jt.update(tenantInfoMapper.delete(Arrays.asList("kp", "tenant_id")), kp, tenantId);
        } catch (CannotGetJdbcConnectionException e) {
            Loggers.CLUSTER.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc) {
        try {
            TenantInfoMapper tenantInfoMapper = mapperManager
                    .findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
            jt.update(tenantInfoMapper.update(Arrays.asList("tenant_name", "tenant_desc", "gmt_modified"),
                    Arrays.asList("kp", "tenant_id")), tenantName, tenantDesc, System.currentTimeMillis(), kp,
                    tenantId);
        } catch (DataAccessException e) {
            Loggers.CLUSTER.error("[db-error] " + e, e);
            throw e;
        }
    }
    
    @Override
    public List<TenantInfo> findTenantByKp(String kp) {
        TenantInfoMapper tenantInfoMapper = mapperManager
                .findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper
                .select(Arrays.asList("tenant_id", "tenant_name", "tenant_desc"), Collections.singletonList("kp"));
        try {
            return this.jt.query(sql, new Object[] {kp}, TENANT_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            Loggers.CLUSTER.error("[db-error] " + e, e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            Loggers.CLUSTER.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public TenantInfo findTenantByKp(String kp, String tenantId) {
        TenantInfoMapper tenantInfoMapper = mapperManager
                .findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper
                .select(Arrays.asList("tenant_id", "tenant_name", "tenant_desc"), Arrays.asList("kp", "tenant_id"));
        try {
            return jt.queryForObject(sql, new Object[] {kp, tenantId}, TENANT_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            Loggers.CLUSTER.error("[db-error] " + e, e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            Loggers.CLUSTER.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
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
    public boolean isExistTable(String tableName) {
        String sql = String.format("SELECT 1 FROM %s LIMIT 1", tableName);
        try {
            jt.queryForObject(sql, Integer.class);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
    
    @Override
    public int tenantInfoCountByTenantId(String tenantId) {
        if (Objects.isNull(tenantId)) {
            throw new IllegalArgumentException("tenantId can not be null");
        }
        TenantInfoMapper tenantInfoMapper = mapperManager
                .findMapper(dataSourceService.getDataSourceType(), TableConstant.TENANT_INFO);
        String sql = tenantInfoMapper.count(Collections.singletonList("tenant_id"));
        Integer result = this.jt.queryForObject(sql, new String[] {tenantId}, Integer.class);
        if (result == null) {
            return 0;
        }
        return result;
    }
}
