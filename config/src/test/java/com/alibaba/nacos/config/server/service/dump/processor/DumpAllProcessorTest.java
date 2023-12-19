package com.alibaba.nacos.config.server.service.dump.processor;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.ExternalDumpService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DumpAllProcessorTest extends TestCase {

    @Mock
    DynamicDataSource dynamicDataSource;

    @Mock
    DataSourceService dataSourceService;

    @Mock
    ConfigInfoBetaPersistService configInfoBetaPersistService;

    @Mock
    ConfigInfoTagPersistService configInfoTagPersistService;

    DumpAllProcessor dumpAllProcessor;

    ExternalDumpService dumpService;

    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;

    @Mock
    ConfigInfoPersistService configInfoPersistService;

    MockedStatic<EnvUtil> envUtilMockedStatic;

    DumpProcessor dumpProcessor;

    @Before
    public void init() throws Exception {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        dumpAllProcessor = new DumpAllProcessor(configInfoPersistService);
        when(EnvUtil.getNacosHome()).thenReturn(System.getProperty("user.home"));
        when(EnvUtil.getProperty(eq(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG), eq(Boolean.class),
                eq(false))).thenReturn(false);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dynamicDataSource);

        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);

        dumpService = new ExternalDumpService(configInfoPersistService, null, null, null, configInfoBetaPersistService,
                configInfoTagPersistService, null);

        dumpAllProcessor = new DumpAllProcessor(configInfoPersistService);
    }

    private static int newConfigCount = 1;

    private ConfigInfoWrapper createNewConfig(int id) {
        String dataId = "dataIdTime" + newConfigCount;
        String group = "groupTime" + newConfigCount;
        String tenant = "tenantTime" + newConfigCount;
        String content = "content " + newConfigCount;
        newConfigCount++;
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        configInfoWrapper.setContent(content);
        configInfoWrapper.setId(id);
        return configInfoWrapper;
    }
    @Test
    public void testDumpAll() throws IOException {
        ConfigInfoWrapper configInfoWrapper1 = createNewConfig(1);
        ConfigInfoWrapper configInfoWrapper2 = createNewConfig(2);


        long timestamp = System.currentTimeMillis();
        long latterTimestamp = timestamp + 999;
        long earlierTimestamp = timestamp - 999;
        String encryptedDataKey = "encryptedDataKey";
        configInfoWrapper1.setLastModified(timestamp);
        configInfoWrapper2.setLastModified(timestamp);
        Page<ConfigInfoWrapper> page = new Page<>();
        page.setTotalCount(2);
        page.setPagesAvailable(2);
        page.setPageNumber(1);
        ArrayList<ConfigInfoWrapper> list = Stream.of(configInfoWrapper1, configInfoWrapper2)
                .collect(Collectors.toCollection(ArrayList::new));
        page.setPageItems(list);

        Mockito.when(configInfoPersistService.findConfigMaxId()).thenReturn(2l);
        Mockito.when(configInfoPersistService.findAllConfigInfoFragment(0, 1000))
                .thenReturn(page);

        String groupKey1 = GroupKey2.getKey(configInfoWrapper1.getDataId(), configInfoWrapper1.getGroup(), configInfoWrapper1.getTenant());
        String groupKey2 = GroupKey2.getKey(configInfoWrapper2.getDataId(), configInfoWrapper2.getGroup(), configInfoWrapper2.getTenant());
        // For config 1, assign a latter time, to make sure that it would be updated.
        // For config 2, assign an earlier time, to make sure that it is not be updated.
        String md5_1 = MD5Utils.md5Hex(configInfoWrapper1.getContent(), "UTF-8");
        String md5_2 = MD5Utils.md5Hex(configInfoWrapper2.getContent(), "UTF-8");
        ConfigCacheService.updateMd5(groupKey1, md5_1, latterTimestamp, encryptedDataKey);
        ConfigCacheService.updateMd5(groupKey2, md5_2, earlierTimestamp, encryptedDataKey);

        DumpAllTask dumpAllTask = new DumpAllTask();
        boolean process = dumpAllProcessor.process(dumpAllTask);
        Assert.assertTrue(process);

        //Check cache
        CacheItem contentCache1 = ConfigCacheService.getContentCache(GroupKey2.getKey(
                configInfoWrapper1.getDataId(),
                configInfoWrapper1.getGroup(),
                configInfoWrapper1.getTenant()));
        Assert.assertEquals(md5_1, contentCache1.getConfigCache().getMd5Utf8());
        // check if config1 is updated
        Assert.assertTrue(timestamp < contentCache1.getConfigCache().getLastModifiedTs());
        //check disk
        String contentFromDisk1 = ConfigDiskServiceFactory.getInstance().getContent(
                configInfoWrapper1.getDataId(),
                configInfoWrapper1.getGroup(),
                configInfoWrapper1.getTenant());
        Assert.assertEquals(configInfoWrapper1.getContent(), contentFromDisk1);

        //Check cache
        CacheItem contentCache2 = ConfigCacheService.getContentCache(GroupKey2.getKey(
                configInfoWrapper2.getDataId(),
                configInfoWrapper2.getGroup(),
                configInfoWrapper2.getTenant()));
        Assert.assertEquals(MD5Utils.md5Hex(configInfoWrapper2.getContent(), "UTF-8"), contentCache2.getConfigCache().getMd5Utf8());
        // check if config2 is updated
        Assert.assertEquals(timestamp, contentCache2.getConfigCache().getLastModifiedTs());
        //check disk
        String contentFromDisk2 = ConfigDiskServiceFactory.getInstance().getContent(
                configInfoWrapper2.getDataId(),
                configInfoWrapper2.getGroup(),
                configInfoWrapper2.getTenant());
        Assert.assertEquals(configInfoWrapper2.getContent(), contentFromDisk2);
    }
}