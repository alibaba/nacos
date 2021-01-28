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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.DistroConsistencyServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.List;

public class DomainsManagerTest extends BaseTest {
    
    @Spy
    @InjectMocks
    private ServiceManager manager;
    
    @Mock
    private DistroConsistencyServiceImpl consistencyService;
    
    @Test
    public void easyRemoveDom() throws Exception {
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(TEST_NAMESPACE);
        manager.putService(service);
        manager.easyRemoveService(TEST_NAMESPACE, TEST_SERVICE_NAME);
    }
    
    @Test
    public void easyRemoveDomNotExist() throws Exception {
        expectedException.expect(NacosException.class);
        expectedException.expectMessage("specified service not exist, serviceName : " + TEST_SERVICE_NAME);
        manager.easyRemoveService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME);
    }
    
    @Test
    public void searchDom() {
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(TEST_NAMESPACE);
        manager.putService(service);
        
        List<Service> list = manager.searchServices(TEST_NAMESPACE, "(.*)test(.*)");
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals(TEST_SERVICE_NAME, list.get(0).getName());
    }
}
