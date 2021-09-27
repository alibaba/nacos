/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.naming.push.UdpPushService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link HealthOperatorV1Impl} unit tests.
 *
 * @author chenglu
 * @date 2021-08-03 22:19
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthOperatorV1ImplTest {
    
    private HealthOperatorV1Impl healthOperatorV1;
    
    @Mock
    private ServiceManager serviceManager;
    
    @Mock
    private UdpPushService pushService;
    
    @Before
    public void setUp() {
        healthOperatorV1 = new HealthOperatorV1Impl(serviceManager, pushService);
    }
    
    @Test
    public void testUpdateHealthStatusForPersistentInstance() {
        try {
            Service service = new Service();
            Map<String, Cluster> clusterMap = new HashMap<>(2);
            Cluster cluster = Mockito.mock(Cluster.class);
            clusterMap.put("C", cluster);
            service.setClusterMap(clusterMap);
            Instance instance = new Instance();
            instance.setIp("1.1.1.1");
            instance.setPort(8080);
            Mockito.when(cluster.allIPs()).thenReturn(Collections.singletonList(instance));
            Mockito.when(cluster.getHealthChecker()).thenReturn(new AbstractHealthChecker.None());
            
            Mockito.when(serviceManager.getService(Mockito.anyString(), Mockito.anyString())).thenReturn(service);
            
            healthOperatorV1.updateHealthStatusForPersistentInstance("A", "B", "C", "1.1.1.1", 8080, true);
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
}
