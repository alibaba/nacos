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
public class DumpProcessorUserRwaDiskTest extends DumpProcessorTest{
    
    @Before
    public void init() throws Exception {
        super.init();
        when(EnvUtil.getNacosHome()).thenReturn("./");
    }
    
    @Override
    protected ConfigDiskService createDiskService() {
        return new ConfigRawDiskService();
    }
    
    @After
    public void after() {
        super.after();
        ConfigDiskServiceFactory.getInstance().clearAll();
    }
    
    @Test
    public void testDumpNormalAndRemove() throws IOException {
        super.testDumpNormalAndRemove();
        
    }
    
    @Test
    public void testDumpBetaAndRemove() throws IOException {
        super.testDumpBetaAndRemove();
    }
    
    @Test
    public void testDumpTagAndRemove() throws IOException {
        super.testDumpTagAndRemove();
    }
}
