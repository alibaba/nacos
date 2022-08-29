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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class CatalogControllerTest {
    
    private CatalogController catalogController;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NacosException {
        catalogController = new CatalogController();
    }
    
    @Test
    public void testServiceDetail() throws Exception {
        Object result = catalogController.serviceDetail(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME);
        String actual = result.toString();
        assertTrue(actual.contains("\"service\":{"));
        assertTrue(actual.contains("\"groupName\":\"test-group-name\""));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"name\":\"test-service\""));
        assertTrue(actual.contains("\"selector\":{\"type\":\"none\",\"contextType\":\"NONE\"}"));
        assertTrue(actual.contains("\"protectThreshold\":12.34"));
        assertTrue(actual.contains("\"clusters\":[{"));
        assertTrue(actual.contains("\"defaultCheckPort\":80"));
        assertTrue(actual.contains("\"defaultPort\":1"));
        assertTrue(actual.contains("\"healthChecker\":{\"type\":\"TCP\"}"));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"name\":\"test-cluster\""));
        assertTrue(actual.contains("\"serviceName\":\"test-service\""));
        assertTrue(actual.contains("\"useIPPort4Check\":true"));
    }
    
    @Test(expected = NacosException.class)
    public void testServiceDetailNotFound() throws Exception {
        catalogController.serviceDetail(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME);
    }
    
    private static final String TEST_CLUSTER_NAME = "test-cluster";
    
    private static final String TEST_SERVICE_NAME = "test-service";
    
    private static final String TEST_GROUP_NAME = "test-group-name";
    
    @Test
    public void testInstanceList() throws NacosException {
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
            Object res = catalogController.listDetail(true, Constants.DEFAULT_NAMESPACE_ID, 1, 10,
                    TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME, TEST_GROUP_NAME, null, true);
            Assert.assertTrue(res instanceof List);
            Assert.assertEquals(0, ((List) res).size());
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
