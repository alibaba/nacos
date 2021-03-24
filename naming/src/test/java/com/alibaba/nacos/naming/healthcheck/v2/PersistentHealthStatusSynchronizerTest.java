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

package com.alibaba.nacos.naming.healthcheck.v2;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PersistentHealthStatusSynchronizerTest {
    
    @Mock
    private PersistentClientOperationServiceImpl persistentClientOperationService;
    
    @Mock
    private Client client;
    
    @Test
    public void testInstanceHealthStatusChange() {
        Service service = Service.newService("public", "DEFAULT", "nacos", true);
        InstancePublishInfo instancePublishInfo = new InstancePublishInfo("127.0.0.1", 8080);
        PersistentHealthStatusSynchronizer persistentHealthStatusSynchronizer = new PersistentHealthStatusSynchronizer(
                persistentClientOperationService);
        persistentHealthStatusSynchronizer.instanceHealthStatusChange(true, client, service, instancePublishInfo);
        
        Instance updateInstance = InstanceUtil.parseToApiInstance(service, instancePublishInfo);
        updateInstance.setHealthy(true);
        
        verify(client).getClientId();
        verify(persistentClientOperationService).registerInstance(service, updateInstance, client.getClientId());
    }
}
