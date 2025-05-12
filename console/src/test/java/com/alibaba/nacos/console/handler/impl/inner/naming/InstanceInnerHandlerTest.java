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

package com.alibaba.nacos.console.handler.impl.inner.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InstanceInnerHandlerTest {
    
    private static final String TEST_NAMESPACE_ID = "testNamespaceId";
    
    private static final String TEST_GROUP_NAME = "testGroupName";
    
    private static final String TEST_SERVICE_NAME = "testServiceName";
    
    @Mock
    private CatalogServiceV2Impl catalogService;
    
    @Mock
    private InstanceOperatorClientImpl instanceServiceV2;
    
    InstanceInnerHandler instanceInnerHandler;
    
    @BeforeEach
    void setUp() {
        instanceInnerHandler = new InstanceInnerHandler(catalogService, instanceServiceV2);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void listInstances() throws NacosException {
        List<Instance> mockInstances = List.of(new Instance());
        doReturn(mockInstances).when(catalogService)
                .listInstances(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME, Constants.DEFAULT_CLUSTER_NAME);
        Page<? extends Instance> actual = instanceInnerHandler.listInstances(TEST_NAMESPACE_ID, TEST_SERVICE_NAME,
                TEST_GROUP_NAME, Constants.DEFAULT_CLUSTER_NAME, 1, 10);
        assertEquals(mockInstances.size(), actual.getPageItems().size());
        assertEquals(mockInstances.get(0), actual.getPageItems().get(0));
        assertEquals(1, actual.getPageNumber());
        assertEquals(1, actual.getPagesAvailable());
        assertEquals(1, actual.getTotalCount());
    }
    
    @Test
    void updateInstance() throws NacosException {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setNamespaceId(TEST_NAMESPACE_ID);
        instanceForm.setGroupName(TEST_GROUP_NAME);
        instanceForm.setServiceName(TEST_SERVICE_NAME);
        Instance instance = new Instance();
        instanceInnerHandler.updateInstance(instanceForm, instance);
        verify(instanceServiceV2).updateInstance(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME, instance);
    }
}