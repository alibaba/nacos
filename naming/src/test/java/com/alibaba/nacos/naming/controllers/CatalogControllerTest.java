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
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

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
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME)).thenReturn(service);
    }
    
    @Test
    public void testServiceDetail() throws Exception {
        ObjectNode result = catalogController.serviceDetail(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME);
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
    
    private static final String TEST_CLUSTER_NAME_TWO = "test-cluster2";
    
    private static final String TEST_SERVICE_NAME = "test-service";
    
    private static final String TEST_GROUP_NAME = "test-group-name";
    
    @Test
    public void testInstanceList() throws NacosException {
        Instance instance = new Instance("1.1.1.1", 1234, TEST_CLUSTER_NAME);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("xxx", "xxx");
        metadata.put("yyy", "yyy");
        instance.setMetadata(metadata);
        
        Instance instance2 = new Instance("2.2.2.2", 5678, TEST_CLUSTER_NAME_TWO);
        Map<String, String> metadata2 = new HashMap<>();
        metadata2.put("xxx", "xxx");
        metadata2.put("zzz", "zzz");
        instance2.setMetadata(metadata2);
        
        cluster.updateIps(Lists.newArrayList(instance, instance2), false);
        ObjectNode result = catalogController.instanceList(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME, TEST_CLUSTER_NAME, "xxx=xxx", 1,
                10);
        
        String actual = result.toString();
        assertTrue(actual.contains("\"count\":2"));
        assertTrue(actual.contains("\"list\":["));
        
        assertTrue(actual.contains("\"clusterName\":\"test-cluster\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"port\":1234"));
        assertTrue(actual.contains("\"metadata\":{\"yyy\":\"yyy\",\"xxx\":\"xxx\"}"));
        
        assertTrue(actual.contains("\"clusterName\":\"test-cluster2\""));
        assertTrue(actual.contains("\"ip\":\"2.2.2.2\""));
        assertTrue(actual.contains("\"port\":5678"));
        assertTrue(actual.contains("\"metadata\":{\"xxx\":\"xxx\",\"zzz\":\"zzz\"}"));
        
        ObjectNode result2 = catalogController.instanceList(Constants.DEFAULT_NAMESPACE_ID,
                TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME, TEST_CLUSTER_NAME,
                "xxx=xxx,yyy=yyy", 1, 10);
        String actual2 = result2.toString();
        
        assertTrue(actual2.contains("\"count\":1"));
        assertTrue(actual2.contains("\"list\":["));
        
        assertTrue(actual2.contains("\"clusterName\":\"test-cluster\""));
        assertTrue(actual2.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual2.contains("\"port\":1234"));
        assertTrue(actual2.contains("\"metadata\":{\"yyy\":\"yyy\",\"xxx\":\"xxx\"}"));
        
        assertFalse(actual2.contains("\"clusterName\":\"test-cluster2\""));
        assertFalse(actual2.contains("\"ip\":\"2.2.2.2\""));
        assertFalse(actual2.contains("\"port\":5678"));
        assertFalse(actual2.contains("\"metadata\":{\"xxx\":\"xxx\",\"zzz\":\"zzz\"}"));
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
