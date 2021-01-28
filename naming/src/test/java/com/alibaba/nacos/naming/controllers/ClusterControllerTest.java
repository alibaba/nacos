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
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.ClusterOperatorV1Impl;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.CoreMatchers.isA;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ClusterControllerTest extends BaseTest {
    
    @InjectMocks
    private ClusterController clusterController;
    
    @InjectMocks
    private ClusterOperatorV1Impl clusterOperatorV1;
    
    private MockMvc mockmvc;
    
    @Before
    public void before() {
        super.before();
        mockInjectSwitchDomain();
        mockInjectDistroMapper();
        mockmvc = MockMvcBuilders.standaloneSetup(clusterController).build();
        ReflectionTestUtils.setField(clusterController, "upgradeJudgement", upgradeJudgement);
        ReflectionTestUtils.setField(clusterController, "clusterOperatorV1", clusterOperatorV1);
    }
    
    @Test
    public void testUpdate() throws Exception {
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId("test-namespace");
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster").param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", TEST_SERVICE_NAME).param("healthChecker", "{\"type\":\"HTTP\"}")
                .param("checkPort", "1").param("useInstancePort4Check", "true");
        Assert.assertEquals("ok", mockmvc.perform(builder).andReturn().getResponse().getContentAsString());
        
        Cluster expectedCluster = new Cluster(TEST_CLUSTER_NAME, service);
        Cluster actualCluster = service.getClusterMap().get(TEST_CLUSTER_NAME);
        
        Assert.assertEquals(expectedCluster, actualCluster);
        Assert.assertEquals(1, actualCluster.getDefCkport());
        Assert.assertTrue(actualCluster.isUseIPPort4Check());
    }
    
    @Test
    public void testUpdateNoService() throws Exception {
        expectedException.expectCause(isA(NacosException.class));
        expectedException.expectMessage("service not found:test-service-not-found");
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster").param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", "test-service-not-found").param("healthChecker", "{\"type\":\"HTTP\"}")
                .param("checkPort", "1").param("useInstancePort4Check", "true");
        mockmvc.perform(builder);
    }
    
    @Test
    public void testUpdateHealthCheckerType() throws Exception {
        
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster").param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", TEST_SERVICE_NAME).param("healthChecker", "{\"type\":\"123\"}")
                .param("checkPort", "1").param("useInstancePort4Check", "true");
        mockmvc.perform(builder);
        
        Assert.assertEquals("NONE", service.getClusterMap().get(TEST_CLUSTER_NAME).getHealthChecker().getType());
        
        MockHttpServletRequestBuilder builder2 = MockMvcRequestBuilders
                .put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster").param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", TEST_SERVICE_NAME).param("healthChecker", "{\"type\":\"TCP\"}")
                .param("checkPort", "1").param("useInstancePort4Check", "true");
        mockmvc.perform(builder2);
        
        Assert.assertEquals("TCP", service.getClusterMap().get(TEST_CLUSTER_NAME).getHealthChecker().getType());
        
        MockHttpServletRequestBuilder builder3 = MockMvcRequestBuilders
                .put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster").param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", TEST_SERVICE_NAME).param("healthChecker", "{\"type\":\"HTTP\"}")
                .param("checkPort", "1").param("useInstancePort4Check", "true");
        mockmvc.perform(builder3);
        
        Assert.assertEquals("HTTP", service.getClusterMap().get(TEST_CLUSTER_NAME).getHealthChecker().getType());
        
        MockHttpServletRequestBuilder builder4 = MockMvcRequestBuilders
                .put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/cluster").param("clusterName", TEST_CLUSTER_NAME)
                .param("serviceName", TEST_SERVICE_NAME).param("healthChecker", "{\"type\":\"MYSQL\"}")
                .param("checkPort", "1").param("useInstancePort4Check", "true");
        mockmvc.perform(builder4);
        
        Assert.assertEquals("MYSQL", service.getClusterMap().get(TEST_CLUSTER_NAME).getHealthChecker().getType());
        
    }
    
}
