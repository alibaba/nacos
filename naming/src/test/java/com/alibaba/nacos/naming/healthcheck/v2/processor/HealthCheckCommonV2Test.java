package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * HealthCheckCommonV2Test.
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthCheckCommonV2Test {
    
    @Mock
    private SwitchDomain.HealthParams healthParams;
    
    @Mock
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private Service service;
    
    @Mock
    private IpPortBasedClient ipPortBasedClient;
    
    @Mock
    private HealthCheckInstancePublishInfo healthCheckInstancePublishInfo;
    
    private HealthCheckCommonV2 healthCheckCommonV2;
    
    @Before
    public void init() {
        healthCheckCommonV2 = new HealthCheckCommonV2();
        when(healthCheckTaskV2.getClient()).thenReturn(ipPortBasedClient);
        when(ipPortBasedClient.getInstancePublishInfo(service)).thenReturn(healthCheckInstancePublishInfo);
        when(healthCheckInstancePublishInfo.getFailCount()).thenReturn(new AtomicInteger());
        when(healthCheckInstancePublishInfo.isHealthy()).thenReturn(true);
    }
    
    @Test
    public void reEvaluateCheckRT() {
        healthCheckCommonV2.reEvaluateCheckRT(1, healthCheckTaskV2, healthParams);
        
        verify(healthParams, times(2)).getMax();
        verify(healthParams, times(1)).getMin();
        verify(healthParams, times(2)).getFactor();
        
        verify(healthCheckTaskV2).getCheckRtWorst();
        verify(healthCheckTaskV2).getCheckRtBest();
        verify(healthCheckTaskV2).getCheckRtNormalized();
    }
    
    @Test
    public void checkOk() {
        healthCheckCommonV2.checkOk(healthCheckTaskV2, service, "test checkOk");
    }
    
    @Test
    public void checkFail() {
        healthCheckCommonV2.checkFail(healthCheckTaskV2, service, "test checkFail");
    }
    
    @Test
    public void checkFailNow() {
        healthCheckCommonV2.checkFailNow(healthCheckTaskV2, service, "test checkFailNow");
    }
}
