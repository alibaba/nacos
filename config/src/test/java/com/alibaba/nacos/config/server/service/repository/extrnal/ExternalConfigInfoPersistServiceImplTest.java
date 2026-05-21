/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.sql.ExternalStorageUtils;
import com.alibaba.nacos.config.server.utils.ConfigExtInfoUtil;
import com.alibaba.nacos.config.server.utils.TestCaseUtils;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ADVANCE_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ALL_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ExternalConfigInfoPersistServiceImplTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ExternalStorageUtils> externalStorageUtilsMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @MockitoBean
    DynamicDataSource dynamicDataSource;
    
    private ExternalConfigInfoPersistServiceImpl externalConfigInfoPersistService;
    
    @MockitoBean
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @MockitoBean
    private DataSourceService dataSourceService;
    
    @MockitoBean
    private JdbcTemplate jdbcTemplate;
    
    private TransactionTemplate transactionTemplate = TestCaseUtils.createMockTransactionTemplate();
    
    @BeforeEach
    void before() {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        externalStorageUtilsMockedStatic = Mockito.mockStatic(ExternalStorageUtils.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getTransactionTemplate()).thenReturn(transactionTemplate);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        /*when(EnvUtil.getProperty(anyString(), eq(Boolean.class),
                eq(false))).thenReturn(false);*/
        envUtilMockedStatic
            .when(() -> EnvUtil.getProperty(anyString(), eq(Boolean.class), eq(false)))
            .thenReturn(false);
        externalConfigInfoPersistService =
            new ExternalConfigInfoPersistServiceImpl(historyConfigInfoPersistService);
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        externalStorageUtilsMockedStatic.close();
    }
    
    @Test
    void testInsertOrUpdateOfInsertConfigSuccess() {
        
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String appName = "appNameNew";
        String content = "content132456";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        long insertConfigIndoId = 12345678765L;
        GeneratedKeyHolder generatedKeyHolder =
            TestCaseUtils.createGeneratedKeyHolder(insertConfigIndoId);
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(generatedKeyHolder);
        //mock get config state
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(null, new ConfigInfoStateWrapper());
        //mock insert config info
        Mockito
            .when(jdbcTemplate.update(any(PreparedStatementCreator.class), eq(generatedKeyHolder)))
            .thenReturn(1);
        Mockito.when(jdbcTemplate.update(
            eq(externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag1"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant)))
            .thenReturn(1);
        Mockito.when(jdbcTemplate.update(
            eq(externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag2"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant)))
            .thenReturn(1);
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        //mock insert config info
        Mockito.doNothing().when(historyConfigInfoPersistService)
            .insertConfigHistoryAtomic(eq(0), eq(configInfo), eq(srcIp), eq(srcUser),
                any(Timestamp.class), eq("I"),
                eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(configAdvanceInfo, srcUser)));
        
        externalConfigInfoPersistService.insertOrUpdate(srcIp, srcUser, configInfo,
            configAdvanceInfo);
        //expect insert config info
        Mockito.verify(jdbcTemplate, times(1)).update(any(PreparedStatementCreator.class),
            eq(generatedKeyHolder));
        //expect insert config tags
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag1"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag2"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        
        //expect insert history info
        Mockito.verify(historyConfigInfoPersistService, times(1))
            .insertConfigHistoryAtomic(eq(0L), eq(configInfo), eq(srcIp), eq(srcUser),
                any(Timestamp.class),
                eq("I"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(configAdvanceInfo, srcUser)));
        
    }
    
    @Test
    void testInsertOrUpdateCasOfInsertConfigSuccess() {
        
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String appName = "appName";
        String content = "content132456";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        long insertConfigIndoId = 12345678765L;
        GeneratedKeyHolder generatedKeyHolder =
            TestCaseUtils.createGeneratedKeyHolder(insertConfigIndoId);
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(generatedKeyHolder);
        //mock get config state
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(null, new ConfigInfoStateWrapper());
        //mock insert config info
        Mockito
            .when(jdbcTemplate.update(any(PreparedStatementCreator.class), eq(generatedKeyHolder)))
            .thenReturn(1);
        Mockito.when(jdbcTemplate.update(
            eq(externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag1"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant)))
            .thenReturn(1);
        Mockito.when(jdbcTemplate.update(
            eq(externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag2"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant)))
            .thenReturn(1);
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        //mock insert config info
        Mockito.doNothing().when(historyConfigInfoPersistService)
            .insertConfigHistoryAtomic(eq(0), eq(configInfo), eq(srcIp), eq(srcUser),
                any(Timestamp.class), eq("I"),
                eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(configAdvanceInfo, srcUser)));
        
        externalConfigInfoPersistService.insertOrUpdateCas(srcIp, srcUser, configInfo,
            configAdvanceInfo);
        //expect insert config info
        Mockito.verify(jdbcTemplate, times(1)).update(any(PreparedStatementCreator.class),
            eq(generatedKeyHolder));
        //expect insert config tags
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag1"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(insertConfigIndoId), eq("tag2"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        
        //expect insert history info
        Mockito.verify(historyConfigInfoPersistService, times(1))
            .insertConfigHistoryAtomic(eq(0L), eq(configInfo), eq(srcIp), eq(srcUser),
                any(Timestamp.class),
                eq("I"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(configAdvanceInfo, srcUser)));
        
    }
    
    @Test
    void testInsertOrUpdateOfException() {
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        //mock get config state
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(null);
        //mock insert config throw exception
        long insertConfigIndoId = 12345678765L;
        GeneratedKeyHolder generatedKeyHolder =
            TestCaseUtils.createGeneratedKeyHolder(insertConfigIndoId);
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(generatedKeyHolder);
        Mockito.when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, null, "content");
        try {
            externalConfigInfoPersistService.insertOrUpdate("srcIp", "srcUser", configInfo,
                configAdvanceInfo);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("mock fail", e.getMessage());
        }
        
    }
    
    @Test
    void testInsertOrUpdateOfUpdateConfigSuccess() {
        
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        configAdvanceInfo.put("desc", "desc11");
        configAdvanceInfo.put("use", "use2233");
        configAdvanceInfo.put("effect", "effect222");
        configAdvanceInfo.put("type", "type3");
        configAdvanceInfo.put("schema", "schema");
        
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String content = "content132456";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, null, content);
        String encryptedDataKey = "key34567";
        configInfo.setEncryptedDataKey(encryptedDataKey);
        //mock get config state,first and second is not null
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(new ConfigInfoStateWrapper(), new ConfigInfoStateWrapper());
        
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setAppName("old_app");
        configAllInfo.setMd5("old_md5");
        configAllInfo.setId(12345678765L);
        //mock get all config info
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(configAllInfo);
        
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        //mock update config info
        Mockito.when(jdbcTemplate.update(
            eq(externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_INFO)
                .update(
                    Arrays.asList("content", "md5", "src_ip", "src_user", "gmt_modified@NOW()",
                        "app_name",
                        "c_desc", "c_use", "effect", "type", "c_schema", "encrypted_data_key"),
                    Arrays.asList("data_id", "group_id", "tenant_id"))),
            eq(configInfo.getContent()),
            eq(configInfo.getMd5()), eq(srcIp), eq(srcUser), eq(configAllInfo.getAppName()),
            eq(configAdvanceInfo.get("desc")), eq(configAdvanceInfo.get("use")),
            eq(configAdvanceInfo.get("effect")), eq(configAdvanceInfo.get("type")),
            eq(configAdvanceInfo.get("schema")), eq(encryptedDataKey), eq(configInfo.getDataId()),
            eq(configInfo.getGroup()), eq(tenant))).thenReturn(1);
        
        //mock insert config tags.
        Mockito.when(jdbcTemplate.update(
            eq(externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(12345678765L), anyString(), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant))).thenReturn(1);
        
        //mock insert his config info
        Mockito.doNothing().when(historyConfigInfoPersistService)
            .insertConfigHistoryAtomic(eq(configAllInfo.getId()), eq(configInfo), eq(srcIp),
                eq(srcUser),
                any(Timestamp.class), eq("I"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo)));
        
        externalConfigInfoPersistService.insertOrUpdate(srcIp, srcUser, configInfo,
            configAdvanceInfo);
        
        //expect update config tags
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(configAllInfo.getId()), eq("tag1"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(configAllInfo.getId()), eq("tag2"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        
        //expect insert history info
        Mockito.verify(historyConfigInfoPersistService, times(1))
            .insertConfigHistoryAtomic(eq(configAllInfo.getId()), any(ConfigInfo.class), eq(srcIp),
                eq(srcUser),
                any(Timestamp.class), eq("U"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo)));
        
    }
    
    @Test
    void testUpdateConfigInfoReturnsFalseWhenOldConfigMissing() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "tenant", "app", "content");
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", "tenant"}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(null);
        
        ConfigOperateResult result =
            externalConfigInfoPersistService.updateConfigInfo(configInfo, "srcIp", "srcUser",
                null);
        
        assertFalse(result.isSuccess());
    }
    
    @Test
    void testInsertOrUpdateCasOfUpdateConfigSuccess() {
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        configAdvanceInfo.put("desc", "desc11");
        configAdvanceInfo.put("use", "use2233");
        configAdvanceInfo.put("effect", "effect222");
        configAdvanceInfo.put("type", "type3");
        configAdvanceInfo.put("schema", "schema");
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String content = "content132456";
        String encryptedDataKey = "key34567";
        String casMd5 = "casMd5..";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, null, content);
        configInfo.setMd5(casMd5);
        configInfo.setEncryptedDataKey(encryptedDataKey);
        
        //mock get config state,first and second is not null
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(new ConfigInfoStateWrapper(), new ConfigInfoStateWrapper());
        
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setAppName("old_app");
        configAllInfo.setMd5("old_md5");
        configAllInfo.setId(12345678765L);
        //mock get all config info
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(configAllInfo);
        
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        //mock update config info cas
        Mockito.when(
            jdbcTemplate.update(anyString(), eq(content),
                eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)),
                eq(srcIp), eq(srcUser), eq(configAllInfo.getAppName()),
                eq(configAdvanceInfo.get("desc")),
                eq(configAdvanceInfo.get("use")), eq(configAdvanceInfo.get("effect")),
                eq(configAdvanceInfo.get("type")), eq(configAdvanceInfo.get("schema")),
                eq(encryptedDataKey),
                eq(dataId), eq(group), eq(tenant), eq(casMd5)))
            .thenReturn(1);
        
        //mock insert config tags.
        Mockito.when(jdbcTemplate.update(
            eq(externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(configAllInfo.getId()), anyString(), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant)))
            .thenReturn(1);
        
        //mock insert his config info
        Mockito.doNothing().when(historyConfigInfoPersistService)
            .insertConfigHistoryAtomic(eq(configAllInfo.getId()), eq(configInfo), eq(srcIp),
                eq(srcUser),
                any(Timestamp.class), eq("I"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo)));
        
        externalConfigInfoPersistService.insertOrUpdateCas(srcIp, srcUser, configInfo,
            configAdvanceInfo);
        //expect update config cas
        Mockito.verify(jdbcTemplate, times(1))
            .update(anyString(), eq(content),
                eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp),
                eq(srcUser), eq(configAllInfo.getAppName()), eq(configAdvanceInfo.get("desc")),
                eq(configAdvanceInfo.get("use")), eq(configAdvanceInfo.get("effect")),
                eq(configAdvanceInfo.get("type")), eq(configAdvanceInfo.get("schema")),
                eq(encryptedDataKey),
                eq(dataId), eq(group), eq(tenant), eq(casMd5));
        
        //expect update config tags
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(configAllInfo.getId()), eq("tag1"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        Mockito.verify(jdbcTemplate, times(1)).update(eq(
            externalConfigInfoPersistService.mapperManager
                .findMapper(dataSourceService.getDataSourceType(),
                    TableConstant.CONFIG_TAGS_RELATION)
                .insert(Arrays.asList("id", "tag_name", "tag_type", "data_id", "group_id",
                    "tenant_id"))),
            eq(configAllInfo.getId()), eq("tag2"), eq(StringUtils.EMPTY), eq(dataId), eq(group),
            eq(tenant));
        
        //expect insert history info
        Mockito.verify(historyConfigInfoPersistService, times(1))
            .insertConfigHistoryAtomic(eq(configAllInfo.getId()), any(ConfigInfo.class), eq(srcIp),
                eq(srcUser),
                any(Timestamp.class), eq("U"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo)));
        
    }
    
    @Test
    void testCreatePsForInsertConfigInfo() throws SQLException {
        
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        configAdvanceInfo.put("desc", "desc11");
        configAdvanceInfo.put("use", "use2233");
        configAdvanceInfo.put("effect", "effect222");
        configAdvanceInfo.put("type", "type3");
        configAdvanceInfo.put("schema", "schema");
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String content = "content132456";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, null, content);
        Connection mockConnection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        
        ConfigInfoMapper configInfoMapper =
            externalConfigInfoPersistService.mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        
        Mockito.when(mockConnection.prepareStatement(anyString(), any(String[].class)))
            .thenReturn(preparedStatement);
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        externalConfigInfoPersistService.createPsForInsertConfigInfo(srcIp, srcUser, configInfo,
            configAdvanceInfo,
            mockConnection, configInfoMapper);
        Mockito.verify(preparedStatement, times(14)).setString(anyInt(), anyString());
    }
    
    @Test
    void testCreatePsForInsertConfigInfoWithoutAdvanceInfo() throws SQLException {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "tenant", "app", "content");
        Connection mockConnection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ConfigInfoMapper configInfoMapper =
            externalConfigInfoPersistService.mapperManager.findMapper(
                dataSourceService.getDataSourceType(), TableConstant.CONFIG_INFO);
        Mockito.when(mockConnection.prepareStatement(anyString(), any(String[].class)))
            .thenReturn(preparedStatement);
        
        externalConfigInfoPersistService.createPsForInsertConfigInfo("srcIp", "srcUser",
            configInfo, null, mockConnection, configInfoMapper);
        
        Mockito.verify(preparedStatement, times(14)).setString(anyInt(), nullable(String.class));
    }
    
    @Test
    void testAddConfigInfoReturnsFalseWhenInsertedStateMissing() {
        GeneratedKeyHolder generatedKeyHolder = TestCaseUtils.createGeneratedKeyHolder(1L);
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(generatedKeyHolder);
        Mockito.when(jdbcTemplate.update(any(PreparedStatementCreator.class),
            eq(generatedKeyHolder))).thenReturn(1);
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "tenant", "app", "content");
        
        ConfigOperateResult result =
            externalConfigInfoPersistService.addConfigInfo("srcIp", "srcUser", configInfo, null);
        
        assertFalse(result.isSuccess());
    }
    
    @Test
    void testAddConfigInfoAtomicThrowsExceptionWhenGeneratedKeyMissing() {
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(new GeneratedKeyHolder());
        Mockito.when(jdbcTemplate.update(any(PreparedStatementCreator.class),
            any(KeyHolder.class))).thenReturn(1);
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "tenant", "app", "content");
        
        assertThrows(IllegalArgumentException.class,
            () -> externalConfigInfoPersistService.addConfigInfoAtomic(-1, "srcIp", "srcUser",
                configInfo, null));
    }
    
    @Test
    void testAddConfigInfoAtomicExecutesPreparedStatementCreator() throws SQLException {
        GeneratedKeyHolder generatedKeyHolder = TestCaseUtils.createGeneratedKeyHolder(7L);
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(generatedKeyHolder);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        Mockito.when(connection.prepareStatement(anyString(), any(String[].class)))
            .thenReturn(preparedStatement);
        Mockito.doAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), eq(generatedKeyHolder));
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", "app", "content");
        configInfo.setEncryptedDataKey("secretKey");
        
        long id = externalConfigInfoPersistService.addConfigInfoAtomic(1L, "srcIp", "srcUser",
            configInfo, null);
        
        assertEquals(7L, id);
        Mockito.verify(preparedStatement).setString(eq(14), eq("secretKey"));
    }
    
    @Test
    void testRemoveConfigInfo() {
        String dataId = "dataId4567";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        //mock exist all config info
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setAppName("old_app");
        configAllInfo.setMd5("old_md5");
        configAllInfo.setId(12345678765L);
        
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(configAllInfo);
        
        String srcIp = "srcIp1234";
        String srcUser = "srcUser";
        externalConfigInfoPersistService.removeConfigInfo(dataId, group, tenant, srcIp, srcUser);
        
        //expect delete to be invoked
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(dataId), eq(group),
            eq(tenant));
        //expect delete tags to be invoked
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(configAllInfo.getId()));
        //expect insert delete history
        Mockito.verify(historyConfigInfoPersistService, times(1))
            .insertConfigHistoryAtomic(eq(configAllInfo.getId()), eq(configAllInfo), eq(srcIp),
                eq(srcUser), any(),
                eq("D"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo)));
        
    }
    
    @Test
    void testRemoveConfigInfoByIds() {
        
        //mock exist config info
        final List<ConfigAllInfo> configAllInfos = new ArrayList<>();
        final ConfigAllInfo configAllInfo1 = new ConfigAllInfo();
        final ConfigAllInfo configAllInfo2 = new ConfigAllInfo();
        configAllInfo1.setDataId("dataId1");
        configAllInfo1.setGroup("group1");
        configAllInfo1.setTenant("tenant1");
        configAllInfo1.setAppName("app1");
        configAllInfo2.setDataId("dataId2");
        configAllInfo2.setGroup("group2");
        configAllInfo2.setTenant("tenant2");
        configAllInfo2.setAppName("app2");
        configAllInfos.add(configAllInfo1);
        configAllInfos.add(configAllInfo2);
        List<Long> deleteIds = Arrays.asList(12344L, 3456789L);
        configAllInfos.get(0).setId(12344L);
        configAllInfos.get(1).setId(3456789L);
        Mockito
            .when(jdbcTemplate.query(anyString(), eq(deleteIds.toArray()),
                eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(configAllInfos);
        String srcIp = "srcIp1234";
        String srcUser = "srcUser";
        externalConfigInfoPersistService.removeConfigInfoByIds(deleteIds, srcIp, srcUser);
        
        //expect delete to be invoked
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(deleteIds.get(0)),
            eq(deleteIds.get(1)));
        //expect delete tags to be invoked
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(deleteIds.get(0)));
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(deleteIds.get(1)));
        //expect insert delete history
        Mockito.verify(historyConfigInfoPersistService, times(1))
            .insertConfigHistoryAtomic(eq(configAllInfos.get(0).getId()), eq(configAllInfos.get(0)),
                eq(srcIp),
                eq(srcUser), any(), eq("D"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfos.get(0))));
        Mockito.verify(historyConfigInfoPersistService, times(1))
            .insertConfigHistoryAtomic(eq(configAllInfos.get(1).getId()), eq(configAllInfos.get(1)),
                eq(srcIp),
                eq(srcUser), any(), eq("D"), eq("formal"), eq(null),
                eq(ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfos.get(0))));
        
    }
    
    @Test
    void testRemoveConfigInfoByIdsReturnsNullWhenIdsEmpty() {
        assertNull(externalConfigInfoPersistService.removeConfigInfoByIds(new ArrayList<>(),
            "srcIp", "srcUser"));
    }
    
    @Test
    void testRemoveConfigInfoRethrowsWhenFindOldConfigFails() {
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.removeConfigInfo("dataId", "group", "",
                "srcIp", "srcUser"));
    }
    
    @Test
    void testRemoveConfigInfoByIdsRethrowsWhenFindOldConfigsFails() {
        Mockito.when(jdbcTemplate.query(anyString(), Mockito.<Object[]>any(),
            eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.removeConfigInfoByIds(
                new ArrayList<>(Arrays.asList(1L, 2L)), "srcIp", "srcUser"));
    }
    
    @Test
    void testRemoveConfigInfoAtomicWithBlankTenant() {
        externalConfigInfoPersistService.removeConfigInfoAtomic("dataId", "group", null, "srcIp",
            "srcUser");
        
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq("dataId"), eq("group"),
            eq(StringUtils.EMPTY));
    }
    
    @Test
    void testRemoveConfigInfoByIdsAtomicWithBlankIds() {
        externalConfigInfoPersistService.removeConfigInfoByIdsAtomic("");
        
        Mockito.verify(jdbcTemplate, times(0)).update(anyString(), Mockito.<Object[]>any());
    }
    
    @Test
    void testBatchInsertOrUpdateOverwrite() throws NacosException {
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        //insert direct
        configInfoList.add(createMockConfigAllInfo(0));
        //exist config and overwrite
        configInfoList.add(createMockConfigAllInfo(1));
        //insert direct
        configInfoList.add(createMockConfigAllInfo(2));
        String srcUser = "srcUser1324";
        String srcIp = "srcIp1243";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        //mock transactionTemplate and replace
        TransactionTemplate transactionTemplateCurrent = Mockito.mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(externalConfigInfoPersistService, "tjt",
            transactionTemplateCurrent);
        //mock add config 1 success,config 2 fail and update success,config 3 success
        Mockito.when(transactionTemplateCurrent.execute(any()))
            .thenReturn(new ConfigOperateResult(true), new ConfigOperateResult(false),
                new ConfigOperateResult(true), new ConfigOperateResult(true));
        
        Map<String, Object> stringObjectMap =
            externalConfigInfoPersistService.batchInsertOrUpdate(configInfoList,
                srcUser, srcIp, configAdvanceInfo, SameConfigPolicy.OVERWRITE);
        assertEquals(3, stringObjectMap.get("succCount"));
        assertEquals(0, stringObjectMap.get("skipCount"));
    }
    
    @Test
    void testBatchInsertOrUpdateSkip() throws NacosException {
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        //insert direct
        configInfoList.add(createMockConfigAllInfo(0));
        //exist config and overwrite
        configInfoList.add(createMockConfigAllInfo(1));
        //insert direct
        configInfoList.add(createMockConfigAllInfo(2));
        String srcUser = "srcUser1324";
        String srcIp = "srcIp1243";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        //mock transactionTemplate and replace
        TransactionTemplate transactionTemplateCurrent = Mockito.mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(externalConfigInfoPersistService, "tjt",
            transactionTemplateCurrent);
        //mock add config 1 success,config 2 fail and skip,config 3 success
        Mockito.when(transactionTemplateCurrent.execute(any()))
            .thenReturn(new ConfigOperateResult(true), new ConfigOperateResult(false),
                new ConfigOperateResult(true));
        
        Map<String, Object> stringObjectMap =
            externalConfigInfoPersistService.batchInsertOrUpdate(configInfoList,
                srcUser, srcIp, configAdvanceInfo, SameConfigPolicy.SKIP);
        assertEquals(2, stringObjectMap.get("succCount"));
        assertEquals(1, stringObjectMap.get("skipCount"));
        assertEquals(configInfoList.get(1).getDataId(),
            ((List<Map<String, String>>) stringObjectMap.get("skipData")).get(0).get("dataId"));
    }
    
    @Test
    void testBatchInsertOrUpdateAbort() throws NacosException {
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        //insert direct
        configInfoList.add(createMockConfigAllInfo(0));
        //exist config and overwrite
        configInfoList.add(createMockConfigAllInfo(1));
        //insert direct
        configInfoList.add(createMockConfigAllInfo(2));
        String srcUser = "srcUser1324";
        String srcIp = "srcIp1243";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        //mock transactionTemplate and replace
        TransactionTemplate transactionTemplateCurrent = Mockito.mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(externalConfigInfoPersistService, "tjt",
            transactionTemplateCurrent);
        //mock add config 1 success,config 2 fail and abort,config 3 not operated
        Mockito.when(transactionTemplateCurrent.execute(any()))
            .thenReturn(new ConfigOperateResult(true), new ConfigOperateResult(false));
        
        Map<String, Object> stringObjectMap =
            externalConfigInfoPersistService.batchInsertOrUpdate(configInfoList,
                srcUser, srcIp, configAdvanceInfo, SameConfigPolicy.ABORT);
        assertEquals(1, stringObjectMap.get("succCount"));
        assertEquals(1, stringObjectMap.get("skipCount"));
        // config 2 failed
        assertEquals(configInfoList.get(1).getDataId(),
            ((List<Map<String, String>>) stringObjectMap.get("failData")).get(0).get("dataId"));
        //skip config 3
        assertEquals(configInfoList.get(2).getDataId(),
            ((List<Map<String, String>>) stringObjectMap.get("skipData")).get(0).get("dataId"));
    }
    
    @Test
    void testBatchInsertOrUpdateWithNullAdvanceInfoAndDefaultType() throws NacosException {
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        ConfigAllInfo configAllInfo = createMockConfigAllInfo(0);
        configAllInfo.setDataId("dataIdWithoutExtension");
        configAllInfo.setEncryptedDataKey("cipherKey");
        configAllInfo.setType(null);
        configInfoList.add(configAllInfo);
        TransactionTemplate transactionTemplateCurrent = Mockito.mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(externalConfigInfoPersistService, "tjt",
            transactionTemplateCurrent);
        Mockito.when(transactionTemplateCurrent.execute(any()))
            .thenReturn(new ConfigOperateResult(true));
        
        Map<String, Object> result = externalConfigInfoPersistService.batchInsertOrUpdate(
            configInfoList, "srcUser", "srcIp", null, SameConfigPolicy.SKIP);
        
        assertEquals(1, result.get("succCount"));
        assertEquals(0, result.get("skipCount"));
    }
    
    @Test
    void testBatchInsertOrUpdateSkipsWhenInsertThrowsDataIntegrityViolationException()
        throws NacosException {
        List<ConfigAllInfo> configInfoList = Collections.singletonList(createMockConfigAllInfo(0));
        TransactionTemplate transactionTemplateCurrent = Mockito.mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(externalConfigInfoPersistService, "tjt",
            transactionTemplateCurrent);
        Mockito.when(transactionTemplateCurrent.execute(any()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));
        
        Map<String, Object> result = externalConfigInfoPersistService.batchInsertOrUpdate(
            configInfoList, "srcUser", "srcIp", new HashMap<>(), SameConfigPolicy.SKIP);
        
        assertEquals(0, result.get("succCount"));
        assertEquals(1, result.get("skipCount"));
    }
    
    @Test
    void testBatchInsertOrUpdateRethrowsInvalidConfig() {
        ConfigAllInfo configAllInfo = createMockConfigAllInfo(0);
        configAllInfo.setDataId("");
        
        assertThrows(NacosException.class,
            () -> externalConfigInfoPersistService.batchInsertOrUpdate(
                Collections.singletonList(configAllInfo), "srcUser", "srcIp",
                new HashMap<>(), SameConfigPolicy.SKIP));
    }
    
    @Test
    void testGetConfigInfoOperateResultReturnsFalseWhenStateMissing() {
        ConfigOperateResult result = ReflectionTestUtils.invokeMethod(
            externalConfigInfoPersistService, "getConfigInfoOperateResult", "dataId", "group",
            "tenant");
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
    }
    
    private ConfigAllInfo createMockConfigAllInfo(long mockId) {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test" + mockId + ".yaml");
        configAllInfo.setGroup("test");
        configAllInfo.setCreateIp("localhost");
        configAllInfo.setCreateUser("test");
        configAllInfo.setContent("23456789000content");
        return configAllInfo;
    }
    
    private ConfigInfoWrapper createMockConfigInfoWrapper(long mockId) {
        ConfigInfoWrapper configAllInfo = new ConfigInfoWrapper();
        configAllInfo.setDataId("test" + mockId + ".yaml");
        configAllInfo.setGroup("test");
        configAllInfo.setContent("23456789000content");
        return configAllInfo;
    }
    
    private ConfigInfoStateWrapper createMockConfigInfoStateWrapper(long mockId) {
        ConfigInfoStateWrapper configAllInfo = new ConfigInfoStateWrapper();
        configAllInfo.setDataId("test" + mockId + ".yaml");
        configAllInfo.setGroup("test");
        configAllInfo.setLastModified(System.currentTimeMillis());
        return configAllInfo;
    }
    
    private ConfigInfo createMockConfigInfo(long mockId) {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId("test" + mockId + ".yaml");
        configInfo.setGroup("test");
        configInfo.setContent("23456789000content");
        
        return configInfo;
    }
    
    @Test
    void testFindConfigMaxId() {
        
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(123456L);
        long configMaxId = externalConfigInfoPersistService.findConfigMaxId();
        assertEquals(123456L, configMaxId);
    }
    
    @Test
    void testFindConfigMaxId0() {
        
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
            .thenThrow(new NullPointerException());
        long configMaxId = externalConfigInfoPersistService.findConfigMaxId();
        assertEquals(0, configMaxId);
    }
    
    @Test
    void testFindConfigInfoById() {
        long id = 1234567890876L;
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(id);
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {id}),
                eq(CONFIG_INFO_ROW_MAPPER)))
            .thenReturn(configInfo);
        ConfigInfo configReturn = externalConfigInfoPersistService.findConfigInfo(id);
        assertEquals(id, configReturn.getId());
    }
    
    @Test
    void testFindConfigInfoByIdNull() {
        long id = 1234567890876L;
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(id);
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {id}),
                eq(CONFIG_INFO_ROW_MAPPER)))
            .thenThrow(new EmptyResultDataAccessException(1));
        ConfigInfo configReturn = externalConfigInfoPersistService.findConfigInfo(id);
        assertNull(configReturn);
    }
    
    @Test
    void testFindConfigInfoByIdGetConFail() {
        long id = 1234567890876L;
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(id);
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {id}),
                eq(CONFIG_INFO_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mocked exp"));
        try {
            ConfigInfo configReturn = externalConfigInfoPersistService.findConfigInfo(id);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
        }
    }
    
    @Test
    void testFindConfigInfoByDataId() {
        String dataId = "dataId4567";
        String group = "group3456789";
        String tenant = "tenant4567890";
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER)))
            .thenReturn(configInfoWrapper);
        ConfigInfo configReturn =
            externalConfigInfoPersistService.findConfigInfo(dataId, group, tenant);
        assertEquals(dataId, configReturn.getDataId());
    }
    
    @Test
    void testFindConfigInfoByDataIdNull() {
        String dataId = "dataId4567";
        String group = "group3456789";
        String tenant = "tenant4567890";
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER)))
            .thenThrow(new EmptyResultDataAccessException(1));
        ConfigInfoWrapper configReturn =
            externalConfigInfoPersistService.findConfigInfo(dataId, group, tenant);
        assertNull(configReturn);
    }
    
    @Test
    void testFindConfigInfoByDataIdGetConFail() {
        String dataId = "dataId4567222";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        Mockito
            .when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mocked exp"));
        try {
            externalConfigInfoPersistService.findConfigInfo(dataId, group, tenant);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
        }
    }
    
    @Test
    void testFindConfigInfo4Page() {
        String dataId = "dataId4567222";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        //mock total count
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {tenant, dataId, group}),
            eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(jdbcTemplate.query(anyString(), eq(new Object[] {tenant, dataId, group}),
            eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        Page<ConfigInfo> configInfo4Page =
            externalConfigInfoPersistService.findConfigInfo4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        assertEquals(result.size(), configInfo4Page.getPageItems().size());
        assertEquals(9, configInfo4Page.getTotalCount());
        
    }
    
    @Test
    void testFindConfigInfo4PageWithTags() {
        String dataId = "dataId4567222";
        String group = "group3456789";
        String tenant = "tenant4567890";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tags1,tags3");
        
        //mock total count
        when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {tenant, dataId, group, "tags1", "tags3"}),
            eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(jdbcTemplate.query(anyString(),
            eq(new Object[] {tenant, dataId, group, "tags1", "tags3"}),
            eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        
        Page<ConfigInfo> configInfo4Page =
            externalConfigInfoPersistService.findConfigInfo4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        assertEquals(result.size(), configInfo4Page.getPageItems().size());
        assertEquals(9, configInfo4Page.getTotalCount());
    }
    
    @Test
    void testFindConfigInfo4PageWithAdvanceFilters() {
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("appName", "appName");
        configAdvanceInfo.put("content", "content");
        ConfigInfo configInfo = createMockConfigInfo(0);
        configInfo.setEncryptedDataKey("");
        List<ConfigInfo> result = Collections.singletonList(configInfo);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<Object[]>any(),
            eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), Mockito.<Object[]>any(),
            eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        
        Page<ConfigInfo> page = externalConfigInfoPersistService.findConfigInfo4Page(1, 3,
            "dataId", "group", "", configAdvanceInfo);
        
        assertEquals(1, page.getPageItems().size());
    }
    
    @Test
    void testFindConfigInfo4PageRethrowsConnectionException() {
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<Object[]>any(),
            eq(Integer.class))).thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.findConfigInfo4Page(1, 3, "dataId", "group",
                "", new HashMap<>()));
    }
    
    @Test
    void testConfigInfoCount() {
        
        //mock total count
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
            .thenReturn(new Integer(9));
        int count = externalConfigInfoPersistService.configInfoCount();
        assertEquals(9, count);
        
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);
        try {
            externalConfigInfoPersistService.configInfoCount();
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        
    }
    
    @Test
    void testConfigInfoCountByTenant() {
        
        String tenant = "tenant124";
        //mock total count
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {tenant}), eq(Integer.class)))
            .thenReturn(
                new Integer(90));
        int count = externalConfigInfoPersistService.configInfoCount(tenant);
        assertEquals(90, count);
        
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {tenant}), eq(Integer.class)))
            .thenReturn(null);
        try {
            externalConfigInfoPersistService.configInfoCount(tenant);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        
    }
    
    @Test
    void testFindConfigInfoLike4Page() {
        String dataId = "dataId4567222*";
        String group = "group3456789*";
        String tenant = "tenant4567890";
        String appName = "appName1234";
        String content = "content123";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("appName", appName);
        configAdvanceInfo.put("content", content);
        //mock total count
        when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"),
                appName,
                content}),
            eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(jdbcTemplate.query(anyString(),
            eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"),
                appName,
                content}),
            eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        
        Page<ConfigInfo> configInfo4Page =
            externalConfigInfoPersistService.findConfigInfoLike4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        assertEquals(result.size(), configInfo4Page.getPageItems().size());
        assertEquals(9, configInfo4Page.getTotalCount());
        
    }
    
    @Test
    void testFindConfigInfoLike4PageWithTags() {
        
        String appName = "appName1234";
        String content = "content123";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("appName", appName);
        configAdvanceInfo.put("content", content);
        configAdvanceInfo.put("config_tags", "tags,tag2");
        String dataId = "dataId4567222*";
        String group = "group3456789*";
        String tenant = "tenant4567890";
        //mock total count
        when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"),
                appName, content,
                "tags", "tag2"}),
            eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(jdbcTemplate.query(anyString(),
            eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"),
                appName, content,
                "tags", "tag2"}),
            eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        
        Page<ConfigInfo> configInfo4Page =
            externalConfigInfoPersistService.findConfigInfoLike4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        assertEquals(result.size(), configInfo4Page.getPageItems().size());
        assertEquals(9, configInfo4Page.getTotalCount());
        
    }
    
    @Test
    void testFindConfigInfoLike4PageWithTypes() {
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("types", "yaml,properties");
        ConfigInfo configInfo = createMockConfigInfo(0);
        configInfo.setEncryptedDataKey("");
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<Object[]>any(),
            eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), Mockito.<Object[]>any(),
            eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(Collections.singletonList(configInfo));
        
        Page<ConfigInfo> page = externalConfigInfoPersistService.findConfigInfoLike4Page(1, 3,
            "dataId*", "group*", "", configAdvanceInfo);
        
        assertEquals(1, page.getPageItems().size());
    }
    
    @Test
    void testFindConfigInfoLike4PageRethrowsConnectionException() {
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<Object[]>any(),
            eq(Integer.class))).thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.findConfigInfoLike4Page(1, 3, "dataId*",
                "group*", "", new HashMap<>()));
    }
    
    @Test
    void testFindChangeConfig() {
        
        //mock page list
        List<ConfigInfoStateWrapper> result = new ArrayList<>();
        result.add(createMockConfigInfoStateWrapper(0));
        result.add(createMockConfigInfoStateWrapper(1));
        result.add(createMockConfigInfoStateWrapper(2));
        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 1000L);
        long lastMaxId = 10000L;
        int pageSize = 30;
        when(jdbcTemplate.query(anyString(), eq(new Object[] {startTime, lastMaxId, pageSize}),
            eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(result);
        
        List<ConfigInfoStateWrapper> configInfo4List =
            externalConfigInfoPersistService.findChangeConfig(startTime,
                lastMaxId, pageSize);
        assertEquals(result.size(), configInfo4List.size());
    }
    
    @Test
    void testFindChangeConfigError() {
        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 1000L);
        long lastMaxId = 10000L;
        int pageSize = 30;
        //mock page list
        when(jdbcTemplate.query(anyString(), eq(new Object[] {startTime, lastMaxId, pageSize}),
            eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenThrow(new CannotAcquireLockException("mock ex"));
        try {
            List<ConfigInfoStateWrapper> configInfo4List =
                externalConfigInfoPersistService.findChangeConfig(startTime,
                    lastMaxId, pageSize);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof CannotAcquireLockException);
        }
    }
    
    @Test
    void testSelectTagByConfig() {
        String dataId = "dataId4567222";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        //mock page list
        List<String> tagStrings = Arrays.asList("", "", "");
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(String.class))).thenReturn(tagStrings);
        List<String> configTags =
            externalConfigInfoPersistService.selectTagByConfig(dataId, group, tenant);
        assertEquals(tagStrings, configTags);
        
        //mock EmptyResultDataAccessException
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(String.class))).thenThrow(new EmptyResultDataAccessException(3));
        List<String> nullResult =
            externalConfigInfoPersistService.selectTagByConfig(dataId, group, tenant);
        assertTrue(nullResult == null);
        //mock IncorrectResultSizeDataAccessException
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(String.class))).thenThrow(new IncorrectResultSizeDataAccessException(3));
        List<String> nullResult2 =
            externalConfigInfoPersistService.selectTagByConfig(dataId, group, tenant);
        assertTrue(nullResult2 == null);
        
        //mock IncorrectResultSizeDataAccessException
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(String.class))).thenThrow(new CannotGetJdbcConnectionException("mock exp"));
        try {
            externalConfigInfoPersistService.selectTagByConfig(dataId, group, tenant);
            assertFalse(true);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
        }
    }
    
    @Test
    void testFindConfigInfosByIds() {
        
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(jdbcTemplate.query(anyString(), eq(new Object[] {123L, 1232345L}),
            eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(
                result);
        String ids = "123,1232345";
        List<ConfigInfo> configInfosByIds =
            externalConfigInfoPersistService.findConfigInfosByIds(ids);
        assertEquals(result.size(), configInfosByIds.size());
        assertEquals(result.get(2).getDataId(), configInfosByIds.get(2).getDataId());
        
        //mock EmptyResultDataAccessException
        when(jdbcTemplate.query(anyString(), eq(new Object[] {123L, 1232345L}),
            eq(CONFIG_INFO_ROW_MAPPER))).thenThrow(
                new EmptyResultDataAccessException(3));
        List<ConfigInfo> nullResult2 = externalConfigInfoPersistService.findConfigInfosByIds(ids);
        assertTrue(nullResult2 == null);
        
        //blank ids.
        List<ConfigInfo> nullResultBlankIds =
            externalConfigInfoPersistService.findConfigInfosByIds("");
        assertTrue(nullResultBlankIds == null);
        
        //mock CannotGetJdbcConnectionException
        when(jdbcTemplate.query(anyString(), eq(new Object[] {123L, 1232345L}),
            eq(CONFIG_INFO_ROW_MAPPER))).thenThrow(
                new CannotGetJdbcConnectionException("mock exp"));
        try {
            externalConfigInfoPersistService.findConfigInfosByIds(ids);
            assertFalse(true);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
        }
    }
    
    @Test
    void testFindConfigAdvanceInfo() {
        
        String dataId = "dataId1324";
        String group = "group23546";
        String tenant = "tenant13245";
        //mock select tags
        List<String> mockTags = Arrays.asList("tag1", "tag2", "tag3");
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(String.class))).thenReturn(mockTags);
        
        String schema = "schema12345654";
        //mock select config advance
        ConfigAdvanceInfo mockedAdvance = new ConfigAdvanceInfo();
        mockedAdvance.setSchema(schema);
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_ADVANCE_INFO_ROW_MAPPER))).thenReturn(mockedAdvance);
        
        //execute return mock obj
        ConfigAdvanceInfo configAdvanceInfo =
            externalConfigInfoPersistService.findConfigAdvanceInfo(dataId, group,
                tenant);
        //expect check schema & tags.
        assertEquals(mockedAdvance.getSchema(), configAdvanceInfo.getSchema());
        assertEquals(String.join(",", mockTags), configAdvanceInfo.getConfigTags());
        
        //mock EmptyResultDataAccessException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_ADVANCE_INFO_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1));
        //expect return null.
        assertNull(externalConfigInfoPersistService.findConfigAdvanceInfo(dataId, group, tenant));
        
        //mock CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_ADVANCE_INFO_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mock exp"));
        //expect throw exception.
        try {
            externalConfigInfoPersistService.findConfigAdvanceInfo(dataId, group, tenant);
            assertFalse(true);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
            assertTrue(e.getMessage().endsWith("mock exp"));
        }
    }
    
    @Test
    void testFindConfigAllInfo() {
        
        String dataId = "dataId1324";
        String group = "group23546";
        String tenant = "tenant13245";
        //mock select tags
        List<String> mockTags = Arrays.asList("tag1", "tag2", "tag3");
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(String.class))).thenReturn(mockTags);
        
        String schema = "schema12345654";
        //mock select config advance
        ConfigAllInfo mockedConfig = new ConfigAllInfo();
        mockedConfig.setSchema(schema);
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(mockedConfig);
        
        //execute return mock obj
        ConfigAllInfo configAllInfo =
            externalConfigInfoPersistService.findConfigAllInfo(dataId, group, tenant);
        //expect check schema & tags.
        assertEquals(mockedConfig.getSchema(), configAllInfo.getSchema());
        assertEquals(String.join(",", mockTags), configAllInfo.getConfigTags());
        
        //mock EmptyResultDataAccessException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1));
        //expect return null.
        assertNull(externalConfigInfoPersistService.findConfigAllInfo(dataId, group, tenant));
        
        //mock CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mock exp"));
        //expect throw exception.
        try {
            externalConfigInfoPersistService.findConfigAllInfo(dataId, group, tenant);
            assertFalse(true);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
            assertTrue(e.getMessage().endsWith("mock exp"));
        }
    }
    
    @Test
    void testFindConfigInfoState() {
        
        String dataId = "dataId1324";
        String group = "group23546";
        String tenant = "tenant13245";
        
        //mock select config state
        ConfigInfoStateWrapper mockedConfig = new ConfigInfoStateWrapper();
        mockedConfig.setLastModified(2345678L);
        mockedConfig.setId(23456789098765L);
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfig);
        
        //execute return mock obj
        ConfigInfoStateWrapper configInfoStateWrapper =
            externalConfigInfoPersistService.findConfigInfoState(dataId,
                group, tenant);
        //expect check schema & tags.
        assertEquals(mockedConfig.getId(), configInfoStateWrapper.getId());
        assertEquals(mockedConfig.getLastModified(), configInfoStateWrapper.getLastModified());
        
        //mock EmptyResultDataAccessException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenThrow(new EmptyResultDataAccessException(1));
        //expect return null.
        assertNull(externalConfigInfoPersistService.findConfigInfoState(dataId, group, tenant));
        
        //mock CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
            eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mock exp"));
        //expect throw exception.
        try {
            externalConfigInfoPersistService.findConfigInfoState(dataId, group, tenant);
            assertFalse(true);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
            assertTrue(e.getMessage().endsWith("mock exp"));
        }
    }
    
    @Test
    void testFindAllConfigInfo4Export() {
        
        //mock select config state
        List<ConfigAllInfo> mockConfigs = new ArrayList<>();
        mockConfigs.add(createMockConfigAllInfo(0));
        mockConfigs.add(createMockConfigAllInfo(1));
        mockConfigs.add(createMockConfigAllInfo(2));
        
        String dataId = "dataId1324";
        String group = "group23546";
        String tenant = "tenant13245";
        String appName = "appName1243";
        List<Long> ids = Arrays.asList(132L, 1343L, 245L);
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {132L, 1343L, 245L}),
            eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(mockConfigs);
        //execute return mock obj
        List<ConfigAllInfo> configAllInfosIds =
            externalConfigInfoPersistService.findAllConfigInfo4Export(dataId, group,
                tenant, appName, ids);
        //expect check
        assertEquals(mockConfigs, configAllInfosIds);
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {tenant, dataId, group, appName}),
            eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(mockConfigs);
        //execute return mock obj
        List<ConfigAllInfo> configAllInfosWithDataId =
            externalConfigInfoPersistService.findAllConfigInfo4Export(dataId,
                group, tenant, appName, null);
        //expect check
        assertEquals(mockConfigs, configAllInfosWithDataId);
        
        //mock CannotGetJdbcConnectionException
        when(jdbcTemplate.query(anyString(), eq(new Object[] {132L, 1343L, 245L}),
            eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mock exp11"));
        //expect throw exception.
        try {
            externalConfigInfoPersistService.findAllConfigInfo4Export(dataId, group, tenant,
                appName, ids);
            assertFalse(true);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
            assertTrue(e.getMessage().endsWith("mock exp11"));
        }
    }
    
    @Test
    void testQueryConfigInfoByNamespace() {
        
        //mock select config state
        List<ConfigInfoWrapper> mockConfigs = new ArrayList<>();
        mockConfigs.add(createMockConfigInfoWrapper(0));
        mockConfigs.add(createMockConfigInfoWrapper(1));
        mockConfigs.add(createMockConfigInfoWrapper(2));
        String tenant = "tenant13245";
        when(jdbcTemplate.query(anyString(), eq(new Object[] {tenant}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(
                mockConfigs);
        //execute return mock obj
        List<ConfigInfoWrapper> configInfoWrappers =
            externalConfigInfoPersistService.queryConfigInfoByNamespace(
                tenant);
        //expect check
        assertEquals(mockConfigs, configInfoWrappers);
        
        //mock CannotGetJdbcConnectionException
        when(jdbcTemplate.query(anyString(), eq(new Object[] {tenant}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenThrow(
                new EmptyResultDataAccessException(2));
        //execute return mock obj
        List<ConfigInfoWrapper> configInfoWrapperNull =
            externalConfigInfoPersistService.queryConfigInfoByNamespace(
                tenant);
        //expect check
        assertEquals(Collections.EMPTY_LIST, configInfoWrapperNull);
        
        //mock CannotGetJdbcConnectionException
        when(jdbcTemplate.query(anyString(), eq(new Object[] {tenant}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenThrow(
                new CannotGetJdbcConnectionException("mock exp1111"));
        //expect throw exception.
        try {
            externalConfigInfoPersistService.queryConfigInfoByNamespace(tenant);
            assertFalse(true);
        } catch (Exception e) {
            assertTrue(e instanceof CannotGetJdbcConnectionException);
            assertTrue(e.getMessage().endsWith("mock exp1111"));
        }
    }
    
    @Test
    void testGetTenantIdList() {
        
        int page = 10;
        int pageSize = 100;
        //mock select config state
        List<String> tenantStrings = Arrays.asList("tenant1", "tenant2", "tenant3");
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {}), eq(String.class)))
            .thenReturn(tenantStrings);
        //execute return mock obj
        List<String> returnTenants =
            externalConfigInfoPersistService.getTenantIdList(page, pageSize);
        
        //expect check
        assertEquals(tenantStrings, returnTenants);
    }
    
    @Test
    void testGetGroupIdList() {
        
        int page = 10;
        int pageSize = 100;
        //mock select config state
        List<String> groupStrings = Arrays.asList("group1", "group2", "group3");
        when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {}), eq(String.class)))
            .thenReturn(groupStrings);
        //execute return mock obj
        List<String> returnGroups = externalConfigInfoPersistService.getGroupIdList(page, pageSize);
        
        //expect check
        assertEquals(groupStrings, returnGroups);
    }
    
    @Test
    void testFindAllConfigInfoFragment() {
        //mock page list
        List<ConfigInfoWrapper> mockConfigs = new ArrayList<>();
        mockConfigs.add(createMockConfigInfoWrapper(0));
        mockConfigs.add(createMockConfigInfoWrapper(1));
        mockConfigs.add(createMockConfigInfoWrapper(2));
        long lastId = 10111L;
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(
                mockConfigs);
        int pageSize = 100;
        //execute return mock obj
        Page<ConfigInfoWrapper> returnConfigPage =
            externalConfigInfoPersistService.findAllConfigInfoFragment(lastId,
                pageSize, true);
        
        //expect check
        assertEquals(mockConfigs, returnConfigPage.getPageItems());
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenThrow(
                new CannotGetJdbcConnectionException("mock fail"));
        try {
            externalConfigInfoPersistService.findAllConfigInfoFragment(lastId, pageSize, true);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("mock fail", e.getMessage());
        }
        
    }
    
    @Test
    void testUpdateConfigInfoMetadataNotFound() {
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {"d", "g", ""}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(null);
        assertThrows(NacosException.class,
            () -> externalConfigInfoPersistService.updateConfigInfoMetadata(
                "d", "g", "", "tags", "desc"));
    }
    
    @Test
    void testUpdateConfigInfoMetadataSuccess() throws NacosException {
        ConfigInfoWrapper wrapper = new ConfigInfoWrapper();
        wrapper.setId(100L);
        wrapper.setDataId("d");
        wrapper.setGroup("g");
        wrapper.setTenant("");
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {"d", "g", ""}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(wrapper);
        ConfigOperateResult result =
            externalConfigInfoPersistService.updateConfigInfoMetadata(
                "d", "g", "", "tag1,tag2", "newdesc");
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }
    
    @Test
    void testUpdateConfigInfoMetadataDescOnly() throws NacosException {
        ConfigInfoWrapper wrapper = new ConfigInfoWrapper();
        wrapper.setId(100L);
        wrapper.setDataId("d");
        wrapper.setGroup("g");
        wrapper.setTenant("");
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {"d", "g", ""}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(wrapper);
        ConfigOperateResult result =
            externalConfigInfoPersistService.updateConfigInfoMetadata(
                "d", "g", "", null, "newdesc");
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }
    
    @Test
    void testUpdateConfigInfoMetadataRethrowsConnectionException() {
        ConfigInfoWrapper wrapper = new ConfigInfoWrapper();
        wrapper.setId(100L);
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {"d", "g", ""}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(wrapper);
        Mockito.when(jdbcTemplate.update(anyString(), eq("newdesc"), eq(100L)))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.updateConfigInfoMetadata(
                "d", "g", "", null, "newdesc"));
    }
    
    @Test
    void testInsertOrUpdateCasRethrowsLookupException() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", "app", "content");
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.insertOrUpdateCas("srcIp", "srcUser",
                configInfo, null));
    }
    
    @Test
    void testUpdateConfigInfoWithTagsReturnsLastState() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", null, "content");
        ConfigAllInfo oldConfig = createMockConfigAllInfo(1);
        oldConfig.setId(100L);
        oldConfig.setDataId("dataId");
        oldConfig.setGroup("group");
        oldConfig.setTenant("");
        oldConfig.setAppName("oldApp");
        ConfigInfoStateWrapper state = new ConfigInfoStateWrapper();
        state.setId(100L);
        state.setLastModified(123L);
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(oldConfig);
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(state);
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any())).thenReturn(1);
        Map<String, Object> advanceInfo = new HashMap<>();
        advanceInfo.put("config_tags", "tag1,tag2");
        
        ConfigOperateResult result = externalConfigInfoPersistService.updateConfigInfo(
            configInfo, "srcIp", "srcUser", advanceInfo);
        
        assertTrue(result.isSuccess());
        assertEquals(100L, result.getId());
        assertEquals(123L, result.getLastModified());
    }
    
    @Test
    void testAddConfigInfoAtomicThrowsWhenGeneratedKeyMissing() {
        KeyHolder keyHolder = Mockito.mock(KeyHolder.class);
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(keyHolder);
        Mockito.when(jdbcTemplate.update(any(PreparedStatementCreator.class), eq(keyHolder)))
            .thenReturn(1);
        
        assertThrows(IllegalArgumentException.class,
            () -> externalConfigInfoPersistService.addConfigInfoAtomic(1L, "srcIp", "srcUser",
                new ConfigInfo("dataId", "group", "", "app", "content"), null));
    }
    
    @Test
    void testAddConfigInfoAtomicRethrowsConnectionException() {
        KeyHolder keyHolder = Mockito.mock(KeyHolder.class);
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
            .thenReturn(keyHolder);
        Mockito.when(jdbcTemplate.update(any(PreparedStatementCreator.class), eq(keyHolder)))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.addConfigInfoAtomic(1L, "srcIp", "srcUser",
                new ConfigInfo("dataId", "group", "", "app", "content"), null));
    }
    
    @Test
    void testAddConfigTagRelationAtomicRethrowsConnectionException() {
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any()))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.addConfigTagRelationAtomic(1L, "tag",
                "dataId", "group", ""));
    }
    
    @Test
    void testRemoveTagByIdAtomicRethrowsConnectionException() {
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any()))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.removeTagByIdAtomic(1L));
    }
    
    @Test
    void testRemoveConfigInfoAtomicRethrowsConnectionException() {
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any()))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.removeConfigInfoAtomic("dataId", "group",
                "", "srcIp", "srcUser"));
    }
    
    @Test
    void testRemoveConfigInfoByIdsAtomicRethrowsConnectionException() {
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any()))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.removeConfigInfoByIdsAtomic("1,2"));
    }
    
    @Test
    void testUpdateConfigInfoAtomicWithNullAdvanceInfoRethrowsConnectionException() {
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any()))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.updateConfigInfoAtomic(
                new ConfigInfo("dataId", "group", "", null, "content"), "srcIp", "srcUser",
                null));
    }
    
    @Test
    void testUpdateConfigInfoRethrowsConnectionExceptionFromAtomicUpdate() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", null, "content");
        ConfigAllInfo oldConfig = createMockConfigAllInfo(1);
        oldConfig.setAppName("oldApp");
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(oldConfig);
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any()))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.updateConfigInfo(configInfo, "srcIp",
                "srcUser", null));
    }
    
    @Test
    void testUpdateConfigInfoCasReturnsFalseWhenRowsZero() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", null, "content");
        ConfigAllInfo oldConfig = createMockConfigAllInfo(1);
        oldConfig.setAppName("oldApp");
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(oldConfig);
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any())).thenReturn(0);
        
        ConfigOperateResult result =
            externalConfigInfoPersistService.updateConfigInfoCas(configInfo, "srcIp", "srcUser",
                null);
        
        assertFalse(result.isSuccess());
    }
    
    @Test
    void testUpdateConfigInfoCasReturnsFalseWhenOldConfigMissing() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", null, "content");
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(null);
        
        ConfigOperateResult result =
            externalConfigInfoPersistService.updateConfigInfoCas(configInfo, "srcIp", "srcUser",
                null);
        
        assertFalse(result.isSuccess());
    }
    
    @Test
    void testUpdateConfigInfoCasReturnsFalseWhenUpdatedStateMissing() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", null, "content");
        ConfigAllInfo oldConfig = createMockConfigAllInfo(1);
        oldConfig.setAppName("oldApp");
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(oldConfig);
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any())).thenReturn(1);
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(null);
        
        ConfigOperateResult result =
            externalConfigInfoPersistService.updateConfigInfoCas(configInfo, "srcIp", "srcUser",
                null);
        
        assertFalse(result.isSuccess());
    }
    
    @Test
    void testUpdateConfigInfoCasWithTagsReturnsLastState() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", null, "content");
        ConfigAllInfo oldConfig = createMockConfigAllInfo(1);
        oldConfig.setId(100L);
        oldConfig.setDataId("dataId");
        oldConfig.setGroup("group");
        oldConfig.setTenant("");
        oldConfig.setAppName("oldApp");
        ConfigInfoStateWrapper state = new ConfigInfoStateWrapper();
        state.setId(101L);
        state.setLastModified(234L);
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(oldConfig);
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
            .thenReturn(state);
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any())).thenReturn(1);
        Map<String, Object> advanceInfo = new HashMap<>();
        advanceInfo.put("config_tags", "tag1,tag2");
        
        ConfigOperateResult result = externalConfigInfoPersistService.updateConfigInfoCas(
            configInfo, "srcIp", "srcUser", advanceInfo);
        
        assertTrue(result.isSuccess());
        assertEquals(101L, result.getId());
        assertEquals(234L, result.getLastModified());
    }
    
    @Test
    void testUpdateConfigInfoCasRethrowsConnectionExceptionFromAtomicUpdate() {
        ConfigInfo configInfo = new ConfigInfo("dataId", "group", "", null, "content");
        ConfigAllInfo oldConfig = createMockConfigAllInfo(1);
        oldConfig.setAppName("oldApp");
        Mockito.when(jdbcTemplate.queryForObject(anyString(),
            eq(new Object[] {"dataId", "group", ""}), eq(CONFIG_ALL_INFO_ROW_MAPPER)))
            .thenReturn(oldConfig);
        Mockito.when(jdbcTemplate.update(anyString(), Mockito.<Object[]>any()))
            .thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalConfigInfoPersistService.updateConfigInfoCas(
                configInfo, "srcIp", "srcUser", null));
    }
    
    @Test
    void testFindAllConfigInfo4ExportFillsConfigTags() {
        ConfigAllInfo configAllInfo = createMockConfigAllInfo(1);
        configAllInfo.setDataId("dataId");
        configAllInfo.setGroup("group");
        configAllInfo.setTenant("");
        Mockito.when(jdbcTemplate.query(anyString(), Mockito.<Object[]>any(),
            eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(Collections.singletonList(configAllInfo));
        Mockito.when(jdbcTemplate.queryForList(anyString(), Mockito.<Object[]>any(),
            eq(String.class))).thenReturn(Arrays.asList("tag1", "tag2"));
        
        List<ConfigAllInfo> result = externalConfigInfoPersistService.findAllConfigInfo4Export(
            "dataId", "group", "", null, null);
        
        assertEquals("tag1,tag2", result.get(0).getConfigTags());
    }
    
    @Test
    void testFindAllConfigInfo4ExportReturnsEmptyResult() {
        Mockito.when(jdbcTemplate.query(anyString(), Mockito.<Object[]>any(),
            eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(Collections.emptyList());
        
        List<ConfigAllInfo> result = externalConfigInfoPersistService.findAllConfigInfo4Export(
            "dataId", "group", "", null, null);
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testQueryConfigInfoByNamespaceRejectsNullTenant() {
        assertThrows(IllegalArgumentException.class,
            () -> externalConfigInfoPersistService.queryConfigInfoByNamespace(null));
    }
    
    @Test
    void testQueryConfigInfoByNamespaceUsesEmptyTenant() {
        Mockito.when(jdbcTemplate.query(anyString(), eq(new Object[] {StringUtils.EMPTY}),
            eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(Collections.emptyList());
        
        List<ConfigInfoWrapper> result =
            externalConfigInfoPersistService.queryConfigInfoByNamespace("");
        
        assertEquals(Collections.emptyList(), result);
    }
    
    @Test
    void testBuildFindConfigInfoStateSql() {
        MapperManager mapperManager = MapperManager.instance(false);
        ConfigInfoMapper configInfoMapper =
            mapperManager.findMapper(dataSourceService.getDataSourceType(),
                TableConstant.CONFIG_INFO);
        String select = configInfoMapper.select(
            Arrays.asList("id", "data_id", "group_id", "tenant_id", "gmt_modified"),
            Arrays.asList("data_id", "group_id", "tenant_id"));
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,gmt_modified FROM config_info WHERE data_id = ? AND group_id = ? AND tenant_id = ?",
            select);
    }
    
    @Test
    void testGenerateLikeArgumentWithUnderscore() {
        String result = externalConfigInfoPersistService.generateLikeArgument(
            "data_id*");
        assertEquals("data\\_id%", result);
    }
    
    @Test
    void testGenerateLikeArgumentWithoutWildcard() {
        String result = externalConfigInfoPersistService.generateLikeArgument(
            "plain_text");
        assertEquals("plain\\_text", result);
    }
    
    @Test
    void testGenerateLikeArgumentWithWildcardOnly() {
        String result = externalConfigInfoPersistService.generateLikeArgument(
            "data*");
        assertEquals("data%", result);
    }
    
}
