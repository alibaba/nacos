/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ServiceOperatorV1ImplTest extends BaseTest {
    
    private ServiceOperatorV1Impl serviceOperatorV1Impl;
    
    @InjectMocks
    private ServiceManager serviceManager;
    
    @InjectMocks
    private DistroMapper distroMapper;
    
    @Mock
    private ConsistencyService consistencyService;
    
    @Before
    public void setUp() {
        ReflectionTestUtils.setField(serviceManager, "consistencyService", consistencyService);
        serviceOperatorV1Impl = new ServiceOperatorV1Impl(serviceManager, distroMapper);
    }
    
    @Test
    public void testUpdate() throws NacosException {
        String serviceName = "DEFAULT_GROUP@@order-service";
        serviceManager.createEmptyService(TEST_NAMESPACE, serviceName, true);
        com.alibaba.nacos.naming.core.v2.pojo.Service service = Service
                .newService(TEST_NAMESPACE, NamingUtils.getGroupName(serviceName),
                        NamingUtils.getServiceName(serviceName));
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setProtectThreshold(0.1F);
        serviceOperatorV1Impl.update(service, metadata);
        assertThat(serviceManager.getService(TEST_NAMESPACE, serviceName).getProtectThreshold(), is(0.1F));
    }
}
