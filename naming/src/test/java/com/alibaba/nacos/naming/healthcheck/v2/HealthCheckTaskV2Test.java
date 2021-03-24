package com.alibaba.nacos.naming.healthcheck.v2;

import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * HealthCheckTaskV2Test.
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthCheckTaskV2Test {
    
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private IpPortBasedClient ipPortBasedClient;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Before
    public void initHealthCheckTaskV2() {
        ApplicationUtils.injectContext(context);
        when(ApplicationUtils.getBean(SwitchDomain.class)).thenReturn(new SwitchDomain());
        when(ApplicationUtils.getBean(NamingMetadataManager.class)).thenReturn(new NamingMetadataManager());
        if (Objects.isNull(healthCheckTaskV2)) {
            healthCheckTaskV2 = new HealthCheckTaskV2(ipPortBasedClient);
        }
    }
    
    @Test
    public void testDoHealthCheck() {
        when(ipPortBasedClient.getAllPublishedService()).thenReturn(returnService());
        
        healthCheckTaskV2.setCheckRtWorst(1);
        healthCheckTaskV2.setCheckRtLastLast(1);
        Assert.assertEquals(1, healthCheckTaskV2.getCheckRtWorst());
        Assert.assertEquals(1, healthCheckTaskV2.getCheckRtLastLast());
        
        healthCheckTaskV2.run();
        healthCheckTaskV2.passIntercept();
        healthCheckTaskV2.doHealthCheck();
    }
    
    private List<Service> returnService() {
        List<Service> serviceList = new ArrayList<>();
        Service service = Service.newService("public", "DEFAULT", "nacos", true);
        serviceList.add(service);
        return serviceList;
    }
    
    @Test
    public void testGetClient() {
        Assert.assertNotNull(healthCheckTaskV2.getClient());
    }
    
    @Test
    public void testGetAndSet() {
        healthCheckTaskV2.setCheckRtBest(1);
        healthCheckTaskV2.setCheckRtNormalized(1);
        healthCheckTaskV2.setCheckRtLast(1);
        healthCheckTaskV2.setCancelled(true);
        healthCheckTaskV2.setStartTime(1615796485783L);
        
        Assert.assertEquals(1, healthCheckTaskV2.getCheckRtBest());
        Assert.assertEquals(1, healthCheckTaskV2.getCheckRtNormalized());
        Assert.assertEquals(1, healthCheckTaskV2.getCheckRtLast());
        Assert.assertTrue(healthCheckTaskV2.isCancelled());
        Assert.assertEquals(1615796485783L, healthCheckTaskV2.getStartTime());
    }
    
    @Test
    public void testAfterIntercept() {
        healthCheckTaskV2.afterIntercept();
    }
}
