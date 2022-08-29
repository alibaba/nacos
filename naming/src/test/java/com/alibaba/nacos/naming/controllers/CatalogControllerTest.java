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

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CatalogControllerTest {
    
    private static final String TEST_CLUSTER_NAME = "test-cluster";
    
    private static final String TEST_SERVICE_NAME = "test-service";
    
    private static final String TEST_GROUP_NAME = "test-group-name";
    
    @Mock
    private CatalogServiceV2Impl catalogServiceV2;
    
    @InjectMocks
    private CatalogController catalogController;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NacosException {
    }
    
    @Test
    public void testServiceDetail() throws Exception {
        Object expected = new Object();
        when(catalogServiceV2.getServiceDetail(Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME))
                .thenReturn(expected);
        Object actual = catalogController.serviceDetail(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME);
        assertEquals(expected, actual);
    }
    
    @Test
    public void testInstanceList() throws NacosException {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(1234);
        instance.setClusterName(TEST_CLUSTER_NAME);
        List list = new ArrayList<>(1);
        list.add(instance);
        when(catalogServiceV2
                .listInstances(Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME, TEST_CLUSTER_NAME))
                .thenReturn(list);
        ObjectNode result = catalogController.instanceList(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME, TEST_CLUSTER_NAME, 1, 10);
        String actual = result.toString();
        assertTrue(actual.contains("\"count\":1"));
        assertTrue(actual.contains("\"list\":["));
        assertTrue(actual.contains("\"clusterName\":\"test-cluster\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"port\":1234"));
    }
    
    @Test
    public void testListDetail() {
        try {
            when(catalogServiceV2
                    .pageListServiceDetail(Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME, 1, 10))
                    .thenReturn(Collections.emptyList());
            Object res = catalogController
                    .listDetail(true, Constants.DEFAULT_NAMESPACE_ID, 1, 10, TEST_SERVICE_NAME, TEST_GROUP_NAME, null,
                            true);
            Assert.assertTrue(res instanceof List);
            Assert.assertEquals(0, ((List) res).size());
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
