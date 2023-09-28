package com.alibaba.nacos.client.naming.backups.datasource;

import com.alibaba.nacos.client.naming.backups.FailoverData;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import junit.framework.TestCase;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class DiskFailoverDataSourceTest extends TestCase {

    public void testGetSwitch() {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource(holder, "/tmp");
        diskFailoverDataSource.getSwitch();
    }

    public void testGetFailoverData() {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource(holder, "/tmp");
        diskFailoverDataSource.getFailoverData();
    }

    public void testSaveFailoverData() {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource(holder, "/tmp");
        Map failoverDataMap = new HashMap<String, FailoverData>();
        diskFailoverDataSource.saveFailoverData(failoverDataMap);
    }
}