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

package com.alibaba.nacos.config.server.service.repository.embedded;

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
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ADVANCE_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_ALL_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.persistence.repository.RowMapperManager.MAP_ROW_MAPPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * EmbeddedConfigInfoPersistServiceImplTest.
 *
 * @author shiyiyue
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class EmbeddedConfigInfoPersistServiceImplTest {
    
    private EmbeddedConfigInfoPersistServiceImpl embeddedConfigInfoPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @Mock
    IdGeneratorManager idGeneratorManager;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    @Before
    public void before() {
        embeddedStorageContextHolderMockedStatic = Mockito.mockStatic(EmbeddedStorageContextHolder.class);
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), eq(Boolean.class), eq(false)))
                .thenReturn(false);
        embeddedConfigInfoPersistService = new EmbeddedConfigInfoPersistServiceImpl(databaseOperate, idGeneratorManager,
                historyConfigInfoPersistService);
    }
    
    @After
    public void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
    }
    
    @Test
    public void testInsertOrUpdateOfInsertConfigSuccess() {
        
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String appName = "appNameNew";
        String content = "content132456";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        String desc = "testdesc";
        String use = "testuse";
        String effect = "testeffect";
        String type = "testtype";
        String schema = "testschema";
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        configAdvanceInfo.put("desc", desc);
        configAdvanceInfo.put("use", use);
        configAdvanceInfo.put("effect", effect);
        configAdvanceInfo.put("type", type);
        configAdvanceInfo.put("schema", schema);
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        String encryptedDataKey = "key1234";
        configInfo.setEncryptedDataKey(encryptedDataKey);
        long insertConfigIndoId = 12345678765L;
        ConfigInfoStateWrapper configInfoStateWrapperFinalSelect = new ConfigInfoStateWrapper();
        configInfoStateWrapperFinalSelect.setId(insertConfigIndoId);
        configInfoStateWrapperFinalSelect.setLastModified(System.currentTimeMillis());
        //mock get config state
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null, configInfoStateWrapperFinalSelect);
        
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        //mock insert config info
        Mockito.doNothing().when(historyConfigInfoPersistService)
                .insertConfigHistoryAtomic(eq(0), eq(configInfo), eq(srcIp), eq(srcUser), any(Timestamp.class),
                        eq("I"));
        
        ConfigOperateResult configOperateResult = embeddedConfigInfoPersistService.insertOrUpdate(srcIp, srcUser,
                configInfo, configAdvanceInfo);
        Assert.assertEquals(configInfoStateWrapperFinalSelect.getId(), configOperateResult.getId());
        Assert.assertEquals(configInfoStateWrapperFinalSelect.getLastModified(), configOperateResult.getLastModified());
        
        //expect insert config info invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq(dataId), eq(group),
                        eq(tenant), eq(appName), eq(content), eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)),
                        eq(srcIp), eq(srcUser), any(Timestamp.class), any(Timestamp.class), eq(desc), eq(use),
                        eq(effect), eq(type), eq(schema), eq(encryptedDataKey)), times(1));
        //expect insert config tags
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag1"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag2"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        
        //expect insert history info
        Mockito.verify(historyConfigInfoPersistService, times(1))
                .insertConfigHistoryAtomic(eq(0L), eq(configInfo), eq(srcIp), eq(srcUser), any(Timestamp.class),
                        eq("I"));
        
    }
    
    @Test
    public void testInsertOrUpdateCasOfInsertConfigSuccess() {
        
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        String desc = "testdesc";
        String use = "testuse";
        String effect = "testeffect";
        String type = "testtype";
        String schema = "testschema";
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        configAdvanceInfo.put("desc", desc);
        configAdvanceInfo.put("use", use);
        configAdvanceInfo.put("effect", effect);
        configAdvanceInfo.put("type", type);
        configAdvanceInfo.put("schema", schema);
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String appName = "appName";
        String content = "content132456";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        String encryptedDatakey = "key456";
        configInfo.setEncryptedDataKey(encryptedDatakey);
        long insertConfigIndoId = 12345678765L;
        ConfigInfoStateWrapper configInfoStateWrapperFinalSelect = new ConfigInfoStateWrapper();
        configInfoStateWrapperFinalSelect.setId(insertConfigIndoId);
        configInfoStateWrapperFinalSelect.setLastModified(System.currentTimeMillis());
        //mock get config state
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null, configInfoStateWrapperFinalSelect);
        String srcIp = "iptest";
        String srcUser = "users";
        ConfigOperateResult configOperateResult = embeddedConfigInfoPersistService.insertOrUpdateCas(srcIp, srcUser,
                configInfo, configAdvanceInfo);
        Assert.assertEquals(configInfoStateWrapperFinalSelect.getId(), configOperateResult.getId());
        Assert.assertEquals(configInfoStateWrapperFinalSelect.getLastModified(), configOperateResult.getLastModified());
        //expect insert config info invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq(dataId), eq(group),
                        eq(tenant), eq(appName), eq(content), eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)),
                        eq(srcIp), eq(srcUser), any(Timestamp.class), any(Timestamp.class), eq(desc), eq(use),
                        eq(effect), eq(type), eq(schema), eq(encryptedDatakey)), times(1));
        //expect insert config tags
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag1"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag2"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        
        //expect insert history info
        Mockito.verify(historyConfigInfoPersistService, times(1))
                .insertConfigHistoryAtomic(eq(0L), eq(configInfo), eq(srcIp), eq(srcUser), any(Timestamp.class),
                        eq("I"));
    }
    
    @Test
    public void testInsertOrUpdateOfUpdateConfigSuccess() {
        
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        String desc = "testdesc";
        String use = "testuse";
        String effect = "testeffect";
        String type = "testtype";
        String schema = "testschema";
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        configAdvanceInfo.put("desc", desc);
        configAdvanceInfo.put("use", use);
        configAdvanceInfo.put("effect", effect);
        configAdvanceInfo.put("type", type);
        configAdvanceInfo.put("schema", schema);
        
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String content = "content132456";
        String appName = "app1233";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        String encryptedDataKey = "key34567";
        configInfo.setEncryptedDataKey(encryptedDataKey);
        //mock get config state,first and second is not null
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                        eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(new ConfigInfoStateWrapper(), new ConfigInfoStateWrapper());
        
        //mock select config info before update
        ConfigInfoWrapper configInfoWrapperOld = new ConfigInfoWrapper();
        configInfoWrapperOld.setDataId(dataId);
        configInfoWrapperOld.setGroup(group);
        configInfoWrapperOld.setTenant(tenant);
        configInfoWrapperOld.setAppName("old_app");
        configInfoWrapperOld.setMd5("old_md5");
        configInfoWrapperOld.setId(12345678765L);
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(configInfoWrapperOld);
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        embeddedConfigInfoPersistService.insertOrUpdate(srcIp, srcUser, configInfo, configAdvanceInfo);
        
        //expect update config info invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(content),
                        eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser),
                        any(Timestamp.class), eq(appName), eq(desc), eq(use), eq(effect), eq(type), eq(schema),
                        eq(encryptedDataKey), eq(dataId), eq(group), eq(tenant)), times(1));
        
        //expect insert config tags
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag1"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag2"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        
        //expect insert history info of U
        Mockito.verify(historyConfigInfoPersistService, times(1))
                .insertConfigHistoryAtomic(eq(configInfoWrapperOld.getId()), any(ConfigInfo.class), eq(srcIp),
                        eq(srcUser), any(Timestamp.class), eq("U"));
        
    }
    
    @Test
    public void testInsertOrUpdateCasOfUpdateConfigSuccess() {
        
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        String desc = "testdesc11";
        String use = "testuse11";
        String effect = "testeffe1ct";
        String type = "testt1ype";
        String schema = "testsch1ema";
        configAdvanceInfo.put("config_tags", "tag1,tag2");
        configAdvanceInfo.put("desc", desc);
        configAdvanceInfo.put("use", use);
        configAdvanceInfo.put("effect", effect);
        configAdvanceInfo.put("type", type);
        configAdvanceInfo.put("schema", schema);
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String content = "content132456";
        String encryptedDataKey = "key34567";
        String casMd5 = "casMd5..";
        String appName = "app12345";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setMd5(casMd5);
        configInfo.setEncryptedDataKey(encryptedDataKey);
        
        //mock get config state,first and second is not null
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                        eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(new ConfigInfoStateWrapper(), new ConfigInfoStateWrapper());
        
        //mock select config info before update
        ConfigInfoWrapper configInfoWrapperOld = new ConfigInfoWrapper();
        configInfoWrapperOld.setDataId(dataId);
        configInfoWrapperOld.setGroup(group);
        configInfoWrapperOld.setTenant(tenant);
        configInfoWrapperOld.setAppName("old_app11");
        configInfoWrapperOld.setMd5("old_md5");
        configInfoWrapperOld.setId(123456799L);
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(configInfoWrapperOld);
        String srcIp = "srcIp";
        String srcUser = "srcUser";
        
        embeddedConfigInfoPersistService.insertOrUpdateCas(srcIp, srcUser, configInfo, configAdvanceInfo);
        //expect update config info invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(eq(Boolean.TRUE), anyString(), eq(content),
                        eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser),
                        any(Timestamp.class), eq(appName), eq(desc), eq(use), eq(effect), eq(type), eq(schema),
                        eq(encryptedDataKey), eq(dataId), eq(group), eq(tenant), eq(casMd5)), times(1));
        
        //expect insert config tags
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag1"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), anyLong(), eq("tag2"),
                        eq(StringUtils.EMPTY), eq(dataId), eq(group), eq(tenant)), times(1));
        
        //expect insert history info of U
        Mockito.verify(historyConfigInfoPersistService, times(1))
                .insertConfigHistoryAtomic(eq(configInfoWrapperOld.getId()), any(ConfigInfo.class), eq(srcIp),
                        eq(srcUser), any(Timestamp.class), eq("U"));
        
    }
    
    @Test
    public void testRemoveConfigInfo() {
        String dataId = "dataId4567";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        //mock exist config info
        ConfigInfoWrapper configInfoWrapperOld = new ConfigInfoWrapper();
        configInfoWrapperOld.setDataId(dataId);
        configInfoWrapperOld.setGroup(group);
        configInfoWrapperOld.setTenant(tenant);
        configInfoWrapperOld.setAppName("old_app");
        configInfoWrapperOld.setContent("old content");
        configInfoWrapperOld.setMd5("old_md5");
        configInfoWrapperOld.setId(12345678765L);
        configInfoWrapperOld.setEncryptedDataKey("key3456");
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(configInfoWrapperOld);
        String srcIp = "srcIp1234";
        String srcUser = "srcUser";
        Mockito.when(databaseOperate.update(any())).thenReturn(true);
        embeddedConfigInfoPersistService.removeConfigInfo(dataId, group, tenant, srcIp, srcUser);
        
        //expect delete config to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant)),
                times(1));
        //expect delete config tag to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(configInfoWrapperOld.getId())),
                times(1));
        //expect insert delete history
        Mockito.verify(historyConfigInfoPersistService, times(1))
                .insertConfigHistoryAtomic(eq(configInfoWrapperOld.getId()), eq(configInfoWrapperOld), eq(srcIp),
                        eq(srcUser), any(), eq("D"));
        
    }
    
    @Test
    public void testRemoveConfigInfoByIds() {
        
        //mock exist config info
        List<ConfigInfo> configInfos = new ArrayList<>();
        configInfos.add(new ConfigInfo("data1", "group", "tenant", "app", "content"));
        configInfos.add(new ConfigInfo("data2", "grou2", "tenan2", "app2", "content2"));
        List<Long> deleteIds = Arrays.asList(12344L, 3456789L);
        configInfos.get(0).setId(12344L);
        configInfos.get(1).setId(3456789L);
        Mockito.when(databaseOperate.queryMany(anyString(), eq(deleteIds.toArray()), eq(CONFIG_INFO_ROW_MAPPER)))
                .thenReturn(configInfos);
        String srcIp = "srcIp1234";
        String srcUser = "srcUser";
        Mockito.when(databaseOperate.update(any())).thenReturn(true);
        embeddedConfigInfoPersistService.removeConfigInfoByIds(deleteIds, srcIp, srcUser);
        
        long deleteId0 = deleteIds.get(0);
        long deleteId1 = deleteIds.get(1);
        
        //expect delete config to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(deleteId0), eq(deleteId1)), times(1));
        //expect delete config tag to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(deleteId0)), times(1));
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(deleteId1)), times(1));
        //expect insert delete history
        Mockito.verify(historyConfigInfoPersistService, times(1))
                .insertConfigHistoryAtomic(eq(configInfos.get(0).getId()), eq(configInfos.get(0)), eq(srcIp),
                        eq(srcUser), any(), eq("D"));
        Mockito.verify(historyConfigInfoPersistService, times(1))
                .insertConfigHistoryAtomic(eq(configInfos.get(1).getId()), eq(configInfos.get(1)), eq(srcIp),
                        eq(srcUser), any(), eq("D"));
        
    }
    
    @Test
    public void testBatchInsertOrUpdateOverwrite() throws NacosException {
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
        
        //mock add config 1 success,config 2 fail and skip,config 3 success
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(0).getDataId(), configInfoList.get(0).getGroup(),
                                configInfoList.get(0).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null);
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(1).getDataId(), configInfoList.get(1).getGroup(),
                                configInfoList.get(1).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(new ConfigInfoStateWrapper());
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(2).getDataId(), configInfoList.get(2).getGroup(),
                                configInfoList.get(1).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null);
        //mock query config info during update
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(1).getDataId(), configInfoList.get(1).getGroup(),
                                configInfoList.get(1).getTenant()}), eq(CONFIG_INFO_WRAPPER_ROW_MAPPER)))
                .thenReturn(configInfoWrapper);
        
        Map<String, Object> stringObjectMap = embeddedConfigInfoPersistService.batchInsertOrUpdate(configInfoList,
                srcUser, srcIp, configAdvanceInfo, SameConfigPolicy.OVERWRITE);
        Assert.assertEquals(3, stringObjectMap.get("succCount"));
        Assert.assertEquals(0, stringObjectMap.get("skipCount"));
    }
    
    @Test
    public void testBatchInsertOrUpdateSkip() throws NacosException {
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        //insert direct
        configInfoList.add(createMockConfigAllInfo(0));
        //exist config and skip
        configInfoList.add(createMockConfigAllInfo(1));
        //insert direct
        configInfoList.add(createMockConfigAllInfo(2));
        String srcUser = "srcUser1324";
        String srcIp = "srcIp1243";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        
        //mock add config 1 success,config 2 fail and skip,config 3 success
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(0).getDataId(), configInfoList.get(0).getGroup(),
                                configInfoList.get(0).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null);
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(1).getDataId(), configInfoList.get(1).getGroup(),
                                configInfoList.get(1).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(new ConfigInfoStateWrapper());
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(2).getDataId(), configInfoList.get(2).getGroup(),
                                configInfoList.get(1).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null);
        
        Map<String, Object> stringObjectMap = embeddedConfigInfoPersistService.batchInsertOrUpdate(configInfoList,
                srcUser, srcIp, configAdvanceInfo, SameConfigPolicy.SKIP);
        Assert.assertEquals(2, stringObjectMap.get("succCount"));
        Assert.assertEquals(1, stringObjectMap.get("skipCount"));
        Assert.assertEquals(configInfoList.get(1).getDataId(),
                ((List<Map<String, String>>) stringObjectMap.get("skipData")).get(0).get("dataId"));
    }
    
    @Test
    public void testBatchInsertOrUpdateAbort() throws NacosException {
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
        
        //mock add config 1 success,config 2 fail and abort,config 3 not operated
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(0).getDataId(), configInfoList.get(0).getGroup(),
                                configInfoList.get(0).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null);
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(1).getDataId(), configInfoList.get(1).getGroup(),
                                configInfoList.get(1).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(new ConfigInfoStateWrapper());
        Mockito.when(databaseOperate.queryOne(anyString(),
                        eq(new Object[] {configInfoList.get(2).getDataId(), configInfoList.get(2).getGroup(),
                                configInfoList.get(1).getTenant()}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null);
        
        Map<String, Object> stringObjectMap = embeddedConfigInfoPersistService.batchInsertOrUpdate(configInfoList,
                srcUser, srcIp, configAdvanceInfo, SameConfigPolicy.ABORT);
        Assert.assertEquals(1, stringObjectMap.get("succCount"));
        Assert.assertEquals(1, stringObjectMap.get("skipCount"));
        // config 2 failed
        Assert.assertEquals(configInfoList.get(1).getDataId(),
                ((List<Map<String, String>>) stringObjectMap.get("failData")).get(0).get("dataId"));
        //skip config 3
        Assert.assertEquals(configInfoList.get(2).getDataId(),
                ((List<Map<String, String>>) stringObjectMap.get("skipData")).get(0).get("dataId"));
    }
    
    private ConfigAllInfo createMockConfigAllInfo(long mockId) {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test" + mockId + ".yaml");
        configAllInfo.setGroup("test");
        configAllInfo.setTenant("tenantTest");
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
    public void testFindConfigMaxId() {
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Long.class))).thenReturn(123456L);
        long configMaxId = embeddedConfigInfoPersistService.findConfigMaxId();
        Assert.assertEquals(123456L, configMaxId);
    }
    
    @Test
    public void testFindConfigMaxId0() {
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Long.class))).thenReturn(0L);
        long configMaxId = embeddedConfigInfoPersistService.findConfigMaxId();
        Assert.assertEquals(0, configMaxId);
    }
    
    @Test
    public void testFindConfigInfoById() {
        long id = 1234567890876L;
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(id);
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {id}), eq(CONFIG_INFO_ROW_MAPPER)))
                .thenReturn(configInfo);
        ConfigInfo configReturn = embeddedConfigInfoPersistService.findConfigInfo(id);
        Assert.assertEquals(id, configReturn.getId());
    }
    
    @Test
    public void testFindConfigInfoByDataId() {
        String dataId = "dataId4567";
        String group = "group3456789";
        String tenant = "tenant4567890";
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(configInfoWrapper);
        ConfigInfo configReturn = embeddedConfigInfoPersistService.findConfigInfo(dataId, group, tenant);
        Assert.assertEquals(dataId, configReturn.getDataId());
    }
    
    @Test
    public void testFindConfigInfo4Page() {
        String dataId = "dataId4567222";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        //mock total count
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {tenant, dataId, group}),
                eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {tenant, dataId, group}),
                eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        Page<ConfigInfo> configInfo4Page = embeddedConfigInfoPersistService.findConfigInfo4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        Assert.assertEquals(result.size(), configInfo4Page.getPageItems().size());
        Assert.assertEquals(9, configInfo4Page.getTotalCount());
        
    }
    
    @Test
    public void testFindConfigInfo4PageWithTags() {
        String dataId = "dataId4567222";
        String group = "group3456789";
        String tenant = "tenant4567890";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "tags1,tags3");
        
        //mock total count
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {tenant, dataId, group, "tags1", "tags3"}),
                eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {tenant, dataId, group, "tags1", "tags3"}),
                eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        
        Page<ConfigInfo> configInfo4Page = embeddedConfigInfoPersistService.findConfigInfo4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        Assert.assertEquals(result.size(), configInfo4Page.getPageItems().size());
        Assert.assertEquals(9, configInfo4Page.getTotalCount());
    }
    
    @Test
    public void testConfigInfoCount() {
        
        //mock total count
        when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(new Integer(9));
        int count = embeddedConfigInfoPersistService.configInfoCount();
        Assert.assertEquals(9, count);
        
        when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(null);
        try {
            embeddedConfigInfoPersistService.configInfoCount();
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
        
    }
    
    @Test
    public void testConfigInfoCountByTenant() {
        
        String tenant = "tenant124";
        //mock total count
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {tenant}), eq(Integer.class))).thenReturn(
                new Integer(90));
        int count = embeddedConfigInfoPersistService.configInfoCount(tenant);
        Assert.assertEquals(90, count);
        
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {tenant}), eq(Integer.class))).thenReturn(null);
        try {
            embeddedConfigInfoPersistService.configInfoCount(tenant);
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
        
    }
    
    @Test
    public void testFindConfigInfoLike4Page() {
        String dataId = "dataId4567222*";
        String group = "group3456789*";
        String tenant = "tenant4567890";
        String appName = "appName1234";
        String content = "content123";
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("appName", appName);
        configAdvanceInfo.put("content", content);
        //mock total count
        when(databaseOperate.queryOne(anyString(),
                eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"), appName,
                        content}), eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(databaseOperate.queryMany(anyString(),
                eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"), appName,
                        content}), eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        
        Page<ConfigInfo> configInfo4Page = embeddedConfigInfoPersistService.findConfigInfoLike4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        Assert.assertEquals(result.size(), configInfo4Page.getPageItems().size());
        Assert.assertEquals(9, configInfo4Page.getTotalCount());
        
    }
    
    @Test
    public void testFindConfigInfoLike4PageWithTags() {
        
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
        when(databaseOperate.queryOne(anyString(),
                eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"), appName, content,
                        "tags", "tag2"}), eq(Integer.class))).thenReturn(new Integer(9));
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(databaseOperate.queryMany(anyString(),
                eq(new Object[] {tenant, dataId.replaceAll("\\*", "%"), group.replaceAll("\\*", "%"), appName, content,
                        "tags", "tag2"}), eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        
        Page<ConfigInfo> configInfo4Page = embeddedConfigInfoPersistService.findConfigInfoLike4Page(1, 3, dataId, group,
                tenant, configAdvanceInfo);
        Assert.assertEquals(result.size(), configInfo4Page.getPageItems().size());
        Assert.assertEquals(9, configInfo4Page.getTotalCount());
        
    }
    
    @Test
    public void testFindChangeConfig() {
        
        //mock page list
        List<ConfigInfoStateWrapper> result = new ArrayList<>();
        result.add(createMockConfigInfoStateWrapper(0));
        result.add(createMockConfigInfoStateWrapper(1));
        result.add(createMockConfigInfoStateWrapper(2));
        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 1000L);
        long lastMaxId = 10000L;
        int pageSize = 30;
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {startTime, lastMaxId, pageSize}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(result);
        
        List<ConfigInfoStateWrapper> configInfo4List = embeddedConfigInfoPersistService.findChangeConfig(startTime,
                lastMaxId, pageSize);
        Assert.assertEquals(result.size(), configInfo4List.size());
    }
    
    @Test
    public void testSelectTagByConfig() {
        String dataId = "dataId4567222";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        //mock page list
        List<String> tagStrings = Arrays.asList("", "", "");
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(String.class))).thenReturn(tagStrings);
        List<String> configTags = embeddedConfigInfoPersistService.selectTagByConfig(dataId, group, tenant);
        Assert.assertEquals(tagStrings, configTags);
    }
    
    @Test
    public void testFindConfigInfosByIds() {
        
        //mock page list
        List<ConfigInfo> result = new ArrayList<>();
        result.add(createMockConfigInfo(0));
        result.add(createMockConfigInfo(1));
        result.add(createMockConfigInfo(2));
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {123L, 1232345L}),
                eq(CONFIG_INFO_ROW_MAPPER))).thenReturn(result);
        String ids = "123,1232345";
        List<ConfigInfo> configInfosByIds = embeddedConfigInfoPersistService.findConfigInfosByIds(ids);
        Assert.assertEquals(result.size(), configInfosByIds.size());
        Assert.assertEquals(result.get(2).getDataId(), configInfosByIds.get(2).getDataId());
        
        //blank ids.
        List<ConfigInfo> nullResultBlankIds = embeddedConfigInfoPersistService.findConfigInfosByIds("");
        Assert.assertTrue(nullResultBlankIds == null);
        
    }
    
    @Test
    public void testFindConfigAdvanceInfo() {
        
        String dataId = "dataId1324";
        String group = "group23546";
        String tenant = "tenant13245";
        //mock select tags
        List<String> mockTags = Arrays.asList("tag1", "tag2", "tag3");
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(String.class))).thenReturn(mockTags);
        
        String schema = "schema12345654";
        //mock select config advance
        ConfigAdvanceInfo mockedAdvance = new ConfigAdvanceInfo();
        mockedAdvance.setSchema(schema);
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_ADVANCE_INFO_ROW_MAPPER))).thenReturn(mockedAdvance);
        
        //execute return mock obj
        ConfigAdvanceInfo configAdvanceInfo = embeddedConfigInfoPersistService.findConfigAdvanceInfo(dataId, group,
                tenant);
        //expect check schema & tags.
        Assert.assertEquals(mockedAdvance.getSchema(), configAdvanceInfo.getSchema());
        Assert.assertEquals(String.join(",", mockTags), configAdvanceInfo.getConfigTags());
    }
    
    @Test
    public void testFindConfigAllInfo() {
        
        String dataId = "dataId1324";
        String group = "group23546";
        String tenant = "tenant13245";
        //mock select tags
        List<String> mockTags = Arrays.asList("tag1", "tag2", "tag3");
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(String.class))).thenReturn(mockTags);
        
        String schema = "schema12345654";
        //mock select config advance
        ConfigAllInfo mockedConfig = new ConfigAllInfo();
        mockedConfig.setSchema(schema);
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(mockedConfig);
        
        //execute return mock obj
        ConfigAllInfo configAllInfo = embeddedConfigInfoPersistService.findConfigAllInfo(dataId, group, tenant);
        //expect check schema & tags.
        Assert.assertEquals(mockedConfig.getSchema(), configAllInfo.getSchema());
        Assert.assertEquals(String.join(",", mockTags), configAllInfo.getConfigTags());
        
    }
    
    @Test
    public void testFindConfigInfoState() {
        
        String dataId = "dataId1324";
        String group = "group23546";
        String tenant = "tenant13245";
        
        //mock select config state
        ConfigInfoStateWrapper mockedConfig = new ConfigInfoStateWrapper();
        mockedConfig.setLastModified(2345678L);
        mockedConfig.setId(23456789098765L);
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfig);
        
        //execute return mock obj
        ConfigInfoStateWrapper configInfoStateWrapper = embeddedConfigInfoPersistService.findConfigInfoState(dataId,
                group, tenant);
        //expect check schema & tags.
        Assert.assertEquals(mockedConfig.getId(), configInfoStateWrapper.getId());
        Assert.assertEquals(mockedConfig.getLastModified(), configInfoStateWrapper.getLastModified());
    }
    
    @Test
    public void testFindAllConfigInfo4Export() {
        
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
        
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {132L, 1343L, 245L}),
                eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(mockConfigs);
        //execute return mock obj
        List<ConfigAllInfo> configAllInfosIds = embeddedConfigInfoPersistService.findAllConfigInfo4Export(dataId, group,
                tenant, appName, ids);
        //expect check
        Assert.assertEquals(mockConfigs, configAllInfosIds);
        
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {tenant, dataId, group, appName}),
                eq(CONFIG_ALL_INFO_ROW_MAPPER))).thenReturn(mockConfigs);
        //execute return mock obj
        List<ConfigAllInfo> configAllInfosWithDataId = embeddedConfigInfoPersistService.findAllConfigInfo4Export(dataId,
                group, tenant, appName, null);
        //expect check
        Assert.assertEquals(mockConfigs, configAllInfosWithDataId);
        
    }
    
    @Test
    public void testQueryConfigInfoByNamespace() {
        
        //mock select config state
        List<ConfigInfoWrapper> mockConfigs = new ArrayList<>();
        mockConfigs.add(createMockConfigInfoWrapper(0));
        mockConfigs.add(createMockConfigInfoWrapper(1));
        mockConfigs.add(createMockConfigInfoWrapper(2));
        String tenant = "tenant13245";
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(mockConfigs);
        //execute return mock obj
        List<ConfigInfoWrapper> configInfoWrappers = embeddedConfigInfoPersistService.queryConfigInfoByNamespace(
                tenant);
        //expect check
        Assert.assertEquals(mockConfigs, configInfoWrappers);
    }
    
    @Test
    public void testGetTenantIdList() {
        
        //mock select config state
        List<String> tenantStrings = Arrays.asList("tenant1", "tenant2", "tenant3");
        Map<String, Object> g1 = new HashMap<>();
        g1.put("TENANT_ID", tenantStrings.get(0));
        Map<String, Object> g2 = new HashMap<>();
        g2.put("TENANT_ID", tenantStrings.get(1));
        Map<String, Object> g3 = new HashMap<>();
        g3.put("TENANT_ID", tenantStrings.get(2));
        List<Map<String, Object>> params = new ArrayList<>();
        params.addAll(Arrays.asList(g1, g2, g3));
        
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {}), eq(MAP_ROW_MAPPER))).thenReturn(params);
        int page = 10;
        int pageSize = 100;
        //execute return mock obj
        List<String> returnTenants = embeddedConfigInfoPersistService.getTenantIdList(page, pageSize);
        //expect check
        Assert.assertEquals(tenantStrings, returnTenants);
    }
    
    @Test
    public void testGetGroupIdList() {
        
        //mock select config state
        List<String> groupStrings = Arrays.asList("group1", "group2", "group3");
        
        Map<String, Object> g1 = new HashMap<>();
        g1.put("GROUP_ID", groupStrings.get(0));
        Map<String, Object> g2 = new HashMap<>();
        g2.put("GROUP_ID", groupStrings.get(1));
        Map<String, Object> g3 = new HashMap<>();
        g3.put("GROUP_ID", groupStrings.get(2));
        List<Map<String, Object>> params = new ArrayList<>();
        params.addAll(Arrays.asList(g1, g2, g3));
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {}), eq(MAP_ROW_MAPPER))).thenReturn(params);
        int page = 10;
        int pageSize = 100;
        //execute return mock obj
        List<String> returnGroups = embeddedConfigInfoPersistService.getGroupIdList(page, pageSize);
        
        //expect check
        Assert.assertEquals(groupStrings, returnGroups);
    }
    
    @Test
    public void testFindAllConfigInfoFragment() {
        //mock page list
        List<ConfigInfoWrapper> mockConfigs = new ArrayList<>();
        mockConfigs.add(createMockConfigInfoWrapper(0));
        mockConfigs.add(createMockConfigInfoWrapper(1));
        mockConfigs.add(createMockConfigInfoWrapper(2));
        long lastId = 10111L;
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {lastId}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(mockConfigs);
        int pageSize = 100;
        //execute return mock obj
        Page<ConfigInfoWrapper> returnConfigPage = embeddedConfigInfoPersistService.findAllConfigInfoFragment(lastId,
                pageSize, true);
        //expect check
        Assert.assertEquals(mockConfigs, returnConfigPage.getPageItems());
        
    }
}