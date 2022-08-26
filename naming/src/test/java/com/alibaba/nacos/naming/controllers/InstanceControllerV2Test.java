/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(MockitoJUnitRunner.class)
public class InstanceControllerV2Test extends BaseTest {
    
    @InjectMocks
    private InstanceControllerV2 instanceControllerV2;
    
    @Mock
    private InstanceOperatorClientImpl instanceServiceV2;
    
    private MockMvc mockmvc;
    
    @Before
    public void before() {
        super.before();
        ReflectionTestUtils.setField(instanceControllerV2, "instanceServiceV2", instanceServiceV2);
        mockmvc = MockMvcBuilders.standaloneSetup(instanceControllerV2).build();
    }
    
    @Test
    public void registerInstance() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
                .param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME).param("ip", TEST_IP)
                .param("cluster", TEST_CLUSTER_NAME).param("port", "9999").param("healthy", "true").param("weight", "1")
                .param("enabled", "true").param("metadata", TEST_METADATA).param("ephemeral", "true");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        Assert.assertEquals("ok", actualValue);
    }
    
    @Test
    public void deregisterInstance() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
                .param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME).param("ip", TEST_IP)
                .param("cluster", TEST_CLUSTER_NAME).param("port", "9999").param("healthy", "true").param("weight", "1")
                .param("enabled", "true").param("metadata", TEST_METADATA).param("ephemeral", "true");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        Assert.assertEquals("ok", actualValue);
    }
    
    @Test
    public void updateInstance() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
                .param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME).param("ip", TEST_IP)
                .param("cluster", TEST_CLUSTER_NAME).param("port", "9999").param("healthy", "true")
                .param("weight", "2.0").param("enabled", "true").param("metadata", TEST_METADATA)
                .param("ephemeral", "false");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        Assert.assertEquals("ok", actualValue);
    }
    
    @Test
    public void batchUpdateInstanceMetadata() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT
                                + "/metadata/batch").param("namespaceId", TEST_NAMESPACE)
                .param("serviceName", TEST_SERVICE_NAME).param("consistencyType", "ephemeral")
                .param("instances", TEST_INSTANCE_INFO_LIST).param("metadata", TEST_METADATA);
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        JsonNode result = JacksonUtils.toObj(actualValue);
        Assert.assertNotNull(result);
        Assert.assertEquals(result.size(), 1);
    }
    
    @Test
    public void patch() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.patch(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
                .param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME).param("ip", TEST_IP)
                .param("cluster", TEST_CLUSTER_NAME).param("port", "9999").param("healthy", "true")
                .param("weight", "2.0").param("enabled", "true").param("metadata", TEST_METADATA)
                .param("ephemeral", "false");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertEquals("ok", actualValue);
    }
    
    @Test
    public void listInstance() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT
                                + "/list").param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME)
                .param("clientIP", TEST_IP).param("udpPort", "9870").param("healthyOnly", "true")
                .param("app", "appName");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertNotNull(actualValue);
    }
    
    @Test
    public void detail() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
                .param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME).param("ip", TEST_IP)
                .param("clusterName", "clusterName");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertNotNull(actualValue);
    }
    
    @Test
    public void beat() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT
                                + "/beat").param("namespaceId", TEST_NAMESPACE).param("serviceName", TEST_SERVICE_NAME)
                .param("ip", TEST_IP).param("clusterName", "clusterName").param("port", "0").param("beat", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertNotNull(actualValue);
    }
    
    @Test
    public void listWithHealthStatus() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT
                        + "/statuses").param("key", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertNotNull(actualValue);
    }
}
