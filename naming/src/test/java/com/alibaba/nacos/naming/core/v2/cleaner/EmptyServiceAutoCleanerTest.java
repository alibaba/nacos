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

package com.alibaba.nacos.naming.core.v2.cleaner;

import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
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
 * {@link EmptyServiceAutoCleaner} unit test.
 *
 * @author chenglu
 * @date 2021-07-21 12:31
 */
@RunWith(MockitoJUnitRunner.class)
public class EmptyServiceAutoCleanerTest {
    
    @Mock
    private ServiceManager serviceManager;
    
    @Mock
    private DistroMapper distroMapper;
    
    private EmptyServiceAutoCleaner emptyServiceAutoCleaner;
    
    @Before
    public void setUp() {
        emptyServiceAutoCleaner = new EmptyServiceAutoCleaner(serviceManager, distroMapper);
    }
    
    @Test
    public void testRun() {
        try {
            Mockito.when(serviceManager.getAllNamespaces()).thenReturn(Collections.singleton("test"));
            
            Map<String, Service> serviceMap = new HashMap<>(2);
            Service service = new Service();
            serviceMap.put("test", service);
            Mockito.when(serviceManager.chooseServiceMap(Mockito.anyString())).thenReturn(serviceMap);
            
            Mockito.when(distroMapper.responsible(Mockito.anyString())).thenReturn(true);
            
            emptyServiceAutoCleaner.run();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
