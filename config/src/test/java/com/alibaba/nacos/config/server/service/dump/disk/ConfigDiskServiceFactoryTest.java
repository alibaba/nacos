package com.alibaba.nacos.config.server.service.dump.disk;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
public class ConfigDiskServiceFactoryTest {
    
    @Before
    public void before() throws Exception {
        clearDiskInstance();
    }
    
    @After
    public void after() {
    
    }
    
    @Test
    public void getRawDiskInstance() {
        System.setProperty("config_disk_type", "rawdisk");
        ConfigDiskService instance = ConfigDiskServiceFactory.getInstance();
        Assert.assertTrue(instance instanceof ConfigRawDiskService);
    }
    
    @Test
    public void getRockDbDiskInstance() {
        System.setProperty("config_disk_type", "rocksdb");
        ConfigDiskService instance = ConfigDiskServiceFactory.getInstance();
        Assert.assertTrue(instance instanceof ConfigRocksDbDiskService);
    }
    
    @Test
    public void getDefaultRawDiskInstance() {
        System.setProperty("config_disk_type", "123");
        ConfigDiskService instance = ConfigDiskServiceFactory.getInstance();
        Assert.assertTrue(instance instanceof ConfigRawDiskService);
    }
    
    private void clearDiskInstance() throws Exception {
        Field configDiskService = ConfigDiskServiceFactory.class.getDeclaredField("configDiskService");
        configDiskService.setAccessible(true);
        configDiskService.set(null, null);
    }
}
