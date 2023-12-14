package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRawDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRocksDbDiskService;
import com.alibaba.nacos.config.server.service.dump.processor.DumpProcessor;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalConfigInfoBetaPersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalConfigInfoPersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalConfigInfoTagPersistServiceImpl;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalHistoryConfigInfoPersistServiceImpl;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.checkerframework.checker.units.qual.C;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DumpProcessorTest {
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DataSourceService dataSourceService;
    
    @Mock
    JdbcTemplate jdbcTemplate;
    
    HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    ConfigInfoPersistService configInfoPersistService;
    
    ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    ExternalDumpService dumpService;
    
    DumpProcessor dumpProcessor;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @Before
    public void init() throws Exception {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        
        when(EnvUtil.getProperty(eq(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG), eq(Boolean.class),
                eq(false))).thenReturn(false);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dynamicDataSource);
        
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        
        historyConfigInfoPersistService = new ExternalHistoryConfigInfoPersistServiceImpl();
        configInfoPersistService = new ExternalConfigInfoPersistServiceImpl(historyConfigInfoPersistService);
        configInfoBetaPersistService = new ExternalConfigInfoBetaPersistServiceImpl();
        configInfoTagPersistService = new ExternalConfigInfoTagPersistServiceImpl();
        ReflectionTestUtils.setField(historyConfigInfoPersistService, "jt", jdbcTemplate);
        ReflectionTestUtils.setField(configInfoPersistService, "jt", jdbcTemplate);
        ReflectionTestUtils.setField(configInfoBetaPersistService, "jt", jdbcTemplate);
        ReflectionTestUtils.setField(configInfoTagPersistService, "jt", jdbcTemplate);
        
        dumpService = new ExternalDumpService(configInfoPersistService, null, null, null, configInfoBetaPersistService,
                configInfoTagPersistService, null);
        dumpProcessor = new DumpProcessor(dumpService);
        ReflectionTestUtils.setField(historyConfigInfoPersistService, "jt", jdbcTemplate);
        Field[] declaredFields = ConfigDiskServiceFactory.class.getDeclaredFields();
        for (Field filed : declaredFields) {
            if (filed.getName().equals("configDiskService")) {
                filed.setAccessible(true);
                filed.set(null, createDiskService());
            }
        }
    }
    
    protected ConfigDiskService createDiskService(){
        return new ConfigRocksDbDiskService();
    }
    
    @After
    public void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
    }
    
    @Test
    public void testDumpNormalAndRemove() throws IOException {
        String dataId = "testDataId";
        String group = "testGroup";
        String tenant = "testTenant";
        String content = "testContent你好" + System.currentTimeMillis();
        long time = System.currentTimeMillis();
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        configInfoWrapper.setContent(content);
        configInfoWrapper.setLastModified(time);
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(configInfoWrapper);
        
        String handlerIp = "127.0.0.1";
        long lastModified = System.currentTimeMillis();
        DumpTask dumpTask = new DumpTask(GroupKey2.getKey(dataId, group, tenant), false, false, false, null,
                lastModified, handlerIp);
        boolean process = dumpProcessor.process(dumpTask);
        Assert.assertTrue(process);
        
        //Check cache
        CacheItem contentCache = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        Assert.assertEquals(MD5Utils.md5Hex(content, "UTF-8"), contentCache.getConfigCache().getMd5Utf8());
        Assert.assertEquals(time, contentCache.getConfigCache().getLastModifiedTs());
        //check disk
        String contentFromDisk = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
        Assert.assertEquals(content, contentFromDisk);
        
        // remove
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_WRAPPER_ROW_MAPPER))).thenReturn(null);
        boolean processRemove = dumpProcessor.process(dumpTask);
        Assert.assertTrue(processRemove);
        
        //Check cache
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        Assert.assertTrue(contentCacheAfterRemove == null);
        //check disk
        String contentFromDiskAfterRemove = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
        Assert.assertNull(contentFromDiskAfterRemove);
        
    }
    
    @Test
    public void testDumpBetaAndRemove() throws IOException {
        String dataId = "testDataIdBeta";
        String group = "testGroup";
        String tenant = "testTenant";
        String content = "testContentBeta你好" + System.currentTimeMillis();
        long time = System.currentTimeMillis();
        ConfigInfoBetaWrapper configInfoWrapper = new ConfigInfoBetaWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        configInfoWrapper.setContent(content);
        configInfoWrapper.setLastModified(time);
        String betaIps = "127.0.0.1123,127.0.0.11";
        configInfoWrapper.setBetaIps(betaIps);
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER))).thenReturn(configInfoWrapper);
        
        String handlerIp = "127.0.0.1";
        long lastModified = System.currentTimeMillis();
        DumpTask dumpTask = new DumpTask(GroupKey2.getKey(dataId, group, tenant), true, false, false, null,
                lastModified, handlerIp);
        boolean process = dumpProcessor.process(dumpTask);
        Assert.assertTrue(process);
        
        //Check cache
        CacheItem contentCache = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        Assert.assertEquals(MD5Utils.md5Hex(content, "UTF-8"), contentCache.getConfigCacheBeta().getMd5Utf8());
        Assert.assertEquals(time, contentCache.getConfigCacheBeta().getLastModifiedTs());
        Assert.assertTrue(contentCache.ips4Beta.containsAll(Arrays.asList(betaIps.split(","))));
        //check disk
        String contentFromDisk = ConfigDiskServiceFactory.getInstance().getBetaContent(dataId, group, tenant);
        Assert.assertEquals(content, contentFromDisk);
        
        // remove
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER))).thenReturn(null);
        boolean processRemove = dumpProcessor.process(dumpTask);
        Assert.assertTrue(processRemove);
        
        //Check cache
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        Assert.assertTrue(contentCacheAfterRemove == null || contentCacheAfterRemove.getConfigCacheBeta() == null);
        //check disk
        String contentFromDiskAfterRemove = ConfigDiskServiceFactory.getInstance()
                .getBetaContent(dataId, group, tenant);
        Assert.assertNull(contentFromDiskAfterRemove);
        
    }
    
    @Test
    public void testDumpTagAndRemove() throws IOException {
        String dataId = "testDataIdBeta";
        String group = "testGroup";
        String tenant = "testTenant";
        String tag = "testTag111";
        String content = "testContentBeta你好" + System.currentTimeMillis();
        long time = System.currentTimeMillis();
        ConfigInfoTagWrapper configInfoWrapper = new ConfigInfoTagWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        configInfoWrapper.setContent(content);
        configInfoWrapper.setLastModified(time);
        configInfoWrapper.setTag(tag);
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant,tag}),
                eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER))).thenReturn(configInfoWrapper);
        
        String handlerIp = "127.0.0.1";
        long lastModified = System.currentTimeMillis();
        DumpTask dumpTask = new DumpTask(GroupKey2.getKey(dataId, group, tenant), false, false, true, tag, lastModified,
                handlerIp);
        boolean process = dumpProcessor.process(dumpTask);
        Assert.assertTrue(process);
        
        //Check cache
        CacheItem contentCache = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        Assert.assertEquals(MD5Utils.md5Hex(content, "UTF-8"), contentCache.getConfigCacheTags().get(tag).getMd5Utf8());
        Assert.assertEquals(time, contentCache.getConfigCacheTags().get(tag).getLastModifiedTs());
        //check disk
        String contentFromDisk = ConfigDiskServiceFactory.getInstance().getTagContent(dataId, group, tenant, tag);
        Assert.assertEquals(content, contentFromDisk);
        
        // remove
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant,tag}),
                eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER))).thenReturn(null);
        boolean processRemove = dumpProcessor.process(dumpTask);
        Assert.assertTrue(processRemove);
        
        //Check cache
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        Assert.assertTrue(contentCacheAfterRemove == null || contentCache.getConfigCacheTags() == null
                || contentCache.getConfigCacheTags().get(tag) == null);
        //check disk
        String contentFromDiskAfterRemove = ConfigDiskServiceFactory.getInstance()
                .getTagContent(dataId, group, tenant,tag);
        Assert.assertNull(contentFromDiskAfterRemove);
        
    }
}
