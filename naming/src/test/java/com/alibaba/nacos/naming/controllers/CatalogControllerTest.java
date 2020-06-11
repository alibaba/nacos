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
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author jifengnan 2019-04-29
 */
@RunWith(MockitoJUnitRunner.class)
public class CatalogControllerTest {

    @Mock
    private ServiceManager serviceManager;

    private CatalogController catalogController;

    private Service service;

    private Cluster cluster;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        catalogController = new CatalogController();
        Field field = catalogController.getClass().getDeclaredField("serviceManager");
        field.setAccessible(true);
        field.set(catalogController, serviceManager);
        service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        service.setProtectThreshold(12.34f);
        service.setGroupName(TEST_GROUP_NAME);
        cluster = new Cluster(TEST_CLUSTER_NAME, service);
        cluster.setDefaultPort(1);
        service.addCluster(cluster);
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME)).thenReturn(service);
    }

    @Test
    public void testServiceDetail() throws Exception {
        ObjectNode result = catalogController.serviceDetail(Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME);
        String actual = result.toString();
        assertTrue(actual.contains("\"service\":{"));
        assertTrue(actual.contains("\"groupName\":\"test-group-name\""));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"name\":\"test-service\""));
        assertTrue(actual.contains("\"selector\":{\"type\":\"none\"}"));
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
        Instance instance = new Instance("1.1.1.1", 1234, TEST_CLUSTER_NAME);
        cluster.updateIPs(Collections.singletonList(instance), false);
        ObjectNode result = catalogController.instanceList(Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME,
            TEST_CLUSTER_NAME, 1, 10);
        String actual = result.toString();
        assertTrue(actual.contains("\"count\":1"));
        assertTrue(actual.contains("\"list\":["));
        assertTrue(actual.contains("\"clusterName\":\"test-cluster\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"port\":1234"));
    }

    @Test
    public void testListDetail() {
        // TODO
    }

    @Test
    public void testRt4Service() {
        // TODO
    }
}
