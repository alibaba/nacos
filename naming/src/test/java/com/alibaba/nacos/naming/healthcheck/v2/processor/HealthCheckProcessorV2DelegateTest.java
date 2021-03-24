package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.extend.HealthCheckExtendProvider;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HealthCheckProcessorV2DelegateTest.
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthCheckProcessorV2DelegateTest {
    
    @Mock
    private HealthCheckExtendProvider healthCheckExtendProvider;
    
    @Mock
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private Service service;
    
    @Mock
    private ClusterMetadata clusterMetadata;
    
    private HealthCheckProcessorV2Delegate healthCheckProcessorV2Delegate;
    
    @Before
    public void initBean() {
        healthCheckProcessorV2Delegate = new HealthCheckProcessorV2Delegate(healthCheckExtendProvider);
        verify(healthCheckExtendProvider).init();
    }
    
    @Test
    public void testAddProcessor() {
        List<HealthCheckProcessorV2> list = new ArrayList<>();
        list.add(new TcpHealthCheckProcessor(null, null));
        healthCheckProcessorV2Delegate.addProcessor(list);
    }
    
    @Test
    public void testProcess() {
        testAddProcessor();
        when(clusterMetadata.getHealthyCheckType()).thenReturn(HealthCheckType.TCP.name());
        when(healthCheckTaskV2.getClient()).thenReturn(new IpPortBasedClient("127.0.0.1:80#true", true));
        
        healthCheckProcessorV2Delegate.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(clusterMetadata).getHealthyCheckType();
        verify(healthCheckTaskV2).getClient();
    }
    
    @Test
    public void testGetType() {
        Assert.assertNull(healthCheckProcessorV2Delegate.getType());
    }
}
