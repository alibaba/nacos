/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.proxy.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class InstanceProxyTest {
    
    @Mock
    private InstanceHandler instanceHandler;
    
    private InstanceProxy instanceProxy;
    
    @BeforeEach
    public void setUp() {
        instanceProxy = new InstanceProxy(instanceHandler);
    }
    
    @Test
    public void updateInstance() throws NacosException {
        InstanceForm instanceForm = new InstanceForm();
        Instance instance = new Instance();
        
        doNothing().when(instanceHandler).updateInstance(instanceForm, instance);
        
        assertDoesNotThrow(() -> instanceProxy.updateInstance(instanceForm, instance));
    }
    
    @Test
    public void listInstances() throws NacosException {
        String namespaceId = "testNamespace";
        String serviceNameWithoutGroup = "testService";
        String groupName = "testGroup";
        String clusterName = "testCluster";
        int page = 1;
        int pageSize = 10;
        
        Page<Instance> expectedPage = new Page<>();
        doReturn(expectedPage).when(instanceHandler)
                .listInstances(namespaceId, serviceNameWithoutGroup, groupName, clusterName, page, pageSize);
        
        Page<? extends Instance> result = instanceProxy.listInstances(namespaceId, serviceNameWithoutGroup, groupName,
                clusterName, page, pageSize);
        
        assertNotNull(result);
        assertEquals(expectedPage, result);
        verify(instanceHandler, times(1)).listInstances(namespaceId, serviceNameWithoutGroup, groupName, clusterName,
                page, pageSize);
    }
    
}
