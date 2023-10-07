package com.alibaba.nacos.client.naming.backups.datasource;

import com.alibaba.nacos.client.naming.backups.FailoverData;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import junit.framework.TestCase;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class DiskFailoverDataSourceTest extends TestCase {

    @Test
    public void testGetSwitch() {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource(holder);
        diskFailoverDataSource.getSwitch();
    }

    @Test
    public void testGetFailoverData() {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource(holder);
        diskFailoverDataSource.getFailoverData();
    }

    @Test
    public void testSaveFailoverData() {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource(holder);
        Map failoverDataMap = new HashMap<String, FailoverData>();
        diskFailoverDataSource.saveFailoverData(failoverDataMap);
    }
}