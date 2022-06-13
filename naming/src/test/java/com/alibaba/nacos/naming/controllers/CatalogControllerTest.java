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
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.naming.core.CatalogServiceV1Impl;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CatalogControllerTest {
    
    @Mock
    private ServiceManager serviceManager;
    
    @Mock
    protected UpgradeJudgement upgradeJudgement;
    
    private CatalogServiceV1Impl catalogServiceV1;
    
    private CatalogController catalogController;
    
    private Service service;
    
    private Cluster cluster;
    
    @Mock
    private HealthCheckTask healthCheckTask;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NacosException {
        catalogController = new CatalogController();
        catalogServiceV1 = new CatalogServiceV1Impl(serviceManager);
        ReflectionTestUtils.setField(catalogController, "serviceManager", serviceManager);
        ReflectionTestUtils.setField(catalogController, "upgradeJudgement", upgradeJudgement);
        ReflectionTestUtils.setField(catalogController, "catalogServiceV1", catalogServiceV1);
        service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        service.setProtectThreshold(12.34f);
        service.setGroupName(TEST_GROUP_NAME);
        cluster = new Cluster(TEST_CLUSTER_NAME, service);
        cluster.setDefaultPort(1);
        Mockito.when(healthCheckTask.getCheckRtBest()).thenReturn(1L);
        Mockito.when(healthCheckTask.getCheckRtWorst()).thenReturn(1L);
        ReflectionTestUtils.setField(cluster, "checkTask", healthCheckTask);
        service.addCluster(cluster);
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME)).thenReturn(service);
        doCallRealMethod().when(serviceManager).checkServiceIsNull(eq(null), anyString(), anyString());
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
        Instance instance = new Instance("1.1.1.1", 1234, TEST_CLUSTER_NAME);
        cluster.updateIps(Collections.singletonList(instance), false);
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
    
    @Test
    public void testRt4Service() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(CommonParams.SERVICE_NAME, TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME);
        try {
            ObjectNode objectNode = catalogController.rt4Service(request);
            String result = objectNode.toString();
            assertTrue(result.contains("\"checkRTWorst\":1"));
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
