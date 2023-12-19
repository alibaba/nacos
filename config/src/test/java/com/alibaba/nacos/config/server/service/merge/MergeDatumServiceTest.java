package com.alibaba.nacos.config.server.service.merge;

import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.service.dump.ExternalDumpService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
public class MergeDatumServiceTest {
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    ConfigInfoAggrPersistService configInfoAggrPersistService;

    @Mock
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private MergeDatumService mergeDatumService;
    @Before
    public void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "localDataSourceService", dataSourceService);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "basicDataSourceService", dataSourceService);
        mergeDatumService=new MergeDatumService(configInfoPersistService,configInfoAggrPersistService,configInfoTagPersistService);
    }
    
    @After
    public void after() {
        envUtilMockedStatic.close();
    }
    @Test
    public void executeMergeConfigTask() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.retention.days"))).thenReturn("10");
        List<ConfigInfoChanged> configInfoList = new ArrayList<>();
        ConfigInfoChanged hasDatum = new ConfigInfoChanged();
        hasDatum.setDataId("hasDatumdataId1");
        hasDatum.setTenant("tenant1");
        hasDatum.setGroup("group1");
        ConfigInfoChanged noDatum = new ConfigInfoChanged();
        noDatum.setDataId("dataId1");
        noDatum.setTenant("tenant1");
        noDatum.setGroup("group1");
        configInfoList.add(hasDatum);
        configInfoList.add(noDatum);
        
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(hasDatum.getDataId()), eq(hasDatum.getGroup()),
                eq(hasDatum.getTenant()))).thenReturn(2);
        Page<ConfigInfoAggr> datumPage = new Page<>();
        ConfigInfoAggr configInfoAggr1 = new ConfigInfoAggr();
        configInfoAggr1.setContent("12344");
        ConfigInfoAggr configInfoAggr2 = new ConfigInfoAggr();
        configInfoAggr2.setContent("12345666");
        datumPage.getPageItems().add(configInfoAggr1);
        datumPage.getPageItems().add(configInfoAggr2);
        
        when(configInfoAggrPersistService.findConfigInfoAggrByPage(eq(hasDatum.getDataId()), eq(hasDatum.getGroup()),
                eq(hasDatum.getTenant()), anyInt(), anyInt())).thenReturn(datumPage);
        
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(noDatum.getDataId()), eq(noDatum.getGroup()),
                eq(noDatum.getTenant()))).thenReturn(0);
    
        mergeDatumService.executeMergeConfigTask(configInfoList, 1000);
    }
}
