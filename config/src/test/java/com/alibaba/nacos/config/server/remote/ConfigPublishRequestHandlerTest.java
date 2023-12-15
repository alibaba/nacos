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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalConfigInfoBetaPersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalConfigInfoPersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalConfigInfoTagPersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalHistoryConfigInfoPersistServiceImpl;
import com.alibaba.nacos.config.server.service.sql.ExternalStorageUtils;
import com.alibaba.nacos.config.server.utils.TestCaseUtils;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigPublishRequestHandlerTest {
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DataSourceService dataSourceService;
    
    @Mock
    JdbcTemplate jdbcTemplate;
    
    TransactionTemplate transactionTemplate;
    
    private ConfigPublishRequestHandler configPublishRequestHandler;
    
    HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    ConfigInfoPersistService configInfoPersistService;
    
    ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ExternalStorageUtils> externalStorageUtilsMockedStatic;
    
    @Before
    public void setUp() {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        externalStorageUtilsMockedStatic = Mockito.mockStatic(ExternalStorageUtils.class);
        when(EnvUtil.getProperty(eq(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG), eq(Boolean.class),
                eq(false))).thenReturn(false);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dynamicDataSource);
        
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        transactionTemplate = TestCaseUtils.createMockTransactionTemplate();
        when(dataSourceService.getTransactionTemplate()).thenReturn(transactionTemplate);
        historyConfigInfoPersistService = new ExternalHistoryConfigInfoPersistServiceImpl();
        configInfoPersistService = new ExternalConfigInfoPersistServiceImpl(historyConfigInfoPersistService);
        configInfoBetaPersistService = new ExternalConfigInfoBetaPersistServiceImpl();
        configInfoTagPersistService = new ExternalConfigInfoTagPersistServiceImpl();
        configPublishRequestHandler = new ConfigPublishRequestHandler(configInfoPersistService,
                configInfoTagPersistService, configInfoBetaPersistService);
        EnvUtil.setEnvironment(new StandardEnvironment());
        ReflectionTestUtils.setField(historyConfigInfoPersistService, "jt", jdbcTemplate);
        ReflectionTestUtils.setField(configInfoPersistService, "jt", jdbcTemplate);
        ReflectionTestUtils.setField(configInfoBetaPersistService, "jt", jdbcTemplate);
        ReflectionTestUtils.setField(configInfoTagPersistService, "jt", jdbcTemplate);
        DatasourceConfiguration.setEmbeddedStorage(false);
    }
    
    @After
    public void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        externalStorageUtilsMockedStatic.close();
    }
    
    /**
     *
     * @throws NacosException
     * @throws InterruptedException
     */
    @Test
    public void testNormalPublish() throws NacosException, InterruptedException {
        String dataId = "testNormalPublish";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
                .thenReturn(createMockGeneratedKeyHolder(12345678));
        //jt.queryForObject
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setId(12345678);
        long timeStamp = System.currentTimeMillis();
        configInfoStateWrapper.setLastModified(timeStamp);
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(configInfoStateWrapper);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(1000L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timeStamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertFalse(reference.get().isBeta);
        
    }
    
    @Test
    public void testBetaPublish() throws NacosException, InterruptedException {
        String dataId = "testBetaPublish";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("betaIps", "127.0.0.1");
        configPublishRequest.setAdditionMap(keyMap);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
                .thenReturn(createMockGeneratedKeyHolder(12345678));
        //jt.queryForObject
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setId(12345678);
        long timeStamp = System.currentTimeMillis();
        configInfoStateWrapper.setLastModified(timeStamp);
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(configInfoStateWrapper);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(1000L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timeStamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertTrue(reference.get().isBeta);
        
    }
    
    @Test
    public void testTagPublish() throws NacosException, InterruptedException {
        String dataId = "dataId";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        Map<String, String> keyMap = new HashMap<>();
        String tag = "testTag";
        keyMap.put("tag", "testTag");
        configPublishRequest.setAdditionMap(keyMap);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        externalStorageUtilsMockedStatic.when(ExternalStorageUtils::createKeyHolder)
                .thenReturn(createMockGeneratedKeyHolder(12345678));
        //jt.queryForObject
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setId(12345678);
        long timeStamp = System.currentTimeMillis();
        configInfoStateWrapper.setLastModified(timeStamp);
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(configInfoStateWrapper);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                reference.set((ConfigDataChangeEvent) event);
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(1000L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timeStamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertFalse(reference.get().isBeta);
        Assert.assertEquals(tag, reference.get().tag);
        
    }
    
    GeneratedKeyHolder createMockGeneratedKeyHolder(long id) {
        GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
        Map<String, Object> keyMap = new HashMap<>();
        AtomicLong atomicLong = new AtomicLong(id);
        keyMap.put("whatever is ok", atomicLong);
        generatedKeyHolder.getKeyList().add(keyMap);
        return generatedKeyHolder;
    }
}