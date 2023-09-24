/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MetricsMonitorTest {
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Before
    public void setUp() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(PrometheusMeterRegistry.class)).thenReturn(null);
        // add simple meterRegistry.
        NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.NAMING_STABLE_REGISTRY)
                .add(new SimpleMeterRegistry());
        
        MetricsMonitor.resetPush();
        MetricsMonitor.getIpCountMonitor().set(0);
    }
    
    @Test
    public void testGetTotalPush() {
        assertEquals(0, MetricsMonitor.getTotalPushMonitor().get());
        assertEquals(1, MetricsMonitor.getTotalPushMonitor().incrementAndGet());
    }
    
    @Test
    public void testGetFailedPush() {
        assertEquals(0, MetricsMonitor.getFailedPushMonitor().get());
        assertEquals(1, MetricsMonitor.getFailedPushMonitor().incrementAndGet());
    }
    
    @Test
    public void testIncrementIpCountWithBatchRegister() {
        BatchInstancePublishInfo test = new BatchInstancePublishInfo();
        List<InstancePublishInfo> instancePublishInfos = new LinkedList<>();
        instancePublishInfos.add(new InstancePublishInfo());
        test.setInstancePublishInfos(instancePublishInfos);
        assertEquals(0, MetricsMonitor.getIpCountMonitor().get());
        MetricsMonitor.incrementIpCountWithBatchRegister(null, test);
        assertEquals(1, MetricsMonitor.getIpCountMonitor().get());
        
        BatchInstancePublishInfo newTest = new BatchInstancePublishInfo();
        List<InstancePublishInfo> newInstances = new LinkedList<>();
        newInstances.add(new InstancePublishInfo());
        newInstances.add(new InstancePublishInfo());
        newTest.setInstancePublishInfos(newInstances);
        MetricsMonitor.incrementIpCountWithBatchRegister(test, newTest);
        assertEquals(2, MetricsMonitor.getIpCountMonitor().get());
        MetricsMonitor.incrementIpCountWithBatchRegister(newTest, test);
        assertEquals(1, MetricsMonitor.getIpCountMonitor().get());
    }
    
    @Test
    public void testIncrementIpCountWithBatchRegisterAfterNormalRegister() {
        // mock normal register
        MetricsMonitor.incrementInstanceCount();
        BatchInstancePublishInfo newTest = new BatchInstancePublishInfo();
        List<InstancePublishInfo> newInstances = new LinkedList<>();
        newInstances.add(new InstancePublishInfo());
        newInstances.add(new InstancePublishInfo());
        newTest.setInstancePublishInfos(newInstances);
        MetricsMonitor.incrementIpCountWithBatchRegister(new InstancePublishInfo(), newTest);
        assertEquals(2, MetricsMonitor.getIpCountMonitor().get());
    }
}
