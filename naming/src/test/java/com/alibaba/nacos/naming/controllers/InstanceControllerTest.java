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
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.InstanceOperatorServiceImpl;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteEventListener;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.InstanceOperationInfo;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class InstanceControllerTest extends BaseTest {
    
    @InjectMocks
    private InstanceController instanceController;
    
    @InjectMocks
    private InstanceOperatorServiceImpl instanceOperatorService;
    
    @Mock
    private DoubleWriteEventListener doubleWriteEventListener;
    
    @Mock
    private RaftPeerSet peerSet;
    
    private MockMvc mockmvc;
    
    @Before
    public void before() {
        super.before();
        mockInjectPushServer();
        ReflectionTestUtils.setField(instanceController, "upgradeJudgement", upgradeJudgement);
        ReflectionTestUtils.setField(instanceController, "instanceServiceV1", instanceOperatorService);
        when(context.getBean(DoubleWriteEventListener.class)).thenReturn(doubleWriteEventListener);
        mockmvc = MockMvcBuilders.standaloneSetup(instanceController).build();
    }
    
    @Test
    public void registerInstance() throws Exception {
        
        Service service = new Service();
        service.setName(TEST_SERVICE_NAME);
        
        Cluster cluster = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster);
        
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(9999);
        List<Instance> ipList = new ArrayList<>();
        ipList.add(instance);
        service.updateIPs(ipList, false);
        
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance").param("serviceName", TEST_SERVICE_NAME)
                .param("ip", "1.1.1.1").param("port", "9999");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        Assert.assertEquals("ok", actualValue);
    }
    
    @Test
    public void deregisterInstance() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .delete(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance").param("serviceName", TEST_SERVICE_NAME)
                .param("ip", "1.1.1.1").param("port", "9999")
                .param("clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        Assert.assertEquals("ok", actualValue);
    }
    
    @Test
    public void getInstances() throws Exception {
        
        Service service = new Service();
        service.setName(TEST_SERVICE_NAME);
        
        Cluster cluster = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster);
        
        Instance instance = new Instance();
        instance.setIp("10.10.10.10");
        instance.setPort(8888);
        instance.setWeight(2.0);
        instance.setServiceName(TEST_SERVICE_NAME);
        List<Instance> ipList = new ArrayList<>();
        ipList.add(instance);
        service.updateIPs(ipList, false);
        
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/list").param("serviceName", TEST_SERVICE_NAME)
                .header(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server:v1");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        JsonNode result = JacksonUtils.toObj(actualValue);
        
        Assert.assertEquals(TEST_SERVICE_NAME, result.get("name").asText());
        JsonNode hosts = result.get("hosts");
        Assert.assertNotNull(hosts);
        Assert.assertEquals(hosts.size(), 1);
        
        JsonNode host = hosts.get(0);
        Assert.assertNotNull(host);
        Assert.assertEquals("10.10.10.10", host.get("ip").asText());
        Assert.assertEquals(8888, host.get("port").asInt());
        Assert.assertEquals(2.0, host.get("weight").asDouble(), 0.001);
    }
    
    @Test
    public void getNullServiceInstances() throws Exception {
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(null);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/list").param("serviceName", TEST_SERVICE_NAME)
                .header(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server:v1");
        
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        JsonNode result = JacksonUtils.toObj(actualValue);
        
        JsonNode hosts = result.get("hosts");
        Assert.assertEquals(hosts.size(), 0);
    }
    
    @Test
    public void batchUpdateMetadata() throws Exception {
        Instance instance = new Instance("1.1.1.1", 8080, TEST_CLUSTER_NAME);
        instance.setServiceName(TEST_SERVICE_NAME);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        instance.setMetadata(metadata);
        
        Instance instance2 = new Instance("2.2.2.2", 8080, TEST_CLUSTER_NAME);
        instance2.setServiceName(TEST_SERVICE_NAME);
        
        List<Instance> instanceList = new LinkedList<>();
        instanceList.add(instance);
        instanceList.add(instance2);
        
        when(serviceManager
                .batchOperate(ArgumentMatchers.anyString(), ArgumentMatchers.any(InstanceOperationInfo.class),
                        ArgumentMatchers.any(Function.class))).thenReturn(instanceList);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/metadata/batch").param("namespace", "public")
                .param("serviceName", TEST_SERVICE_NAME).param("instances",
                        "[{\"ip\":\"1.1.1.1\",\"port\": \"8080\",\"ephemeral\":\"true\",\"clusterName\":\"test-cluster\"},"
                                + "{\"ip\":\"2.2.2.2\",\"port\":\"8080\",\"ephemeral\":\"true\",\"clusterName\":\"test-cluster\"}]")
                .param("metadata", "{\"age\":\"20\",\"name\":\"horizon\"}");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        JsonNode result = JacksonUtils.toObj(actualValue);
        
        JsonNode updated = result.get("updated");
        
        Assert.assertEquals(updated.size(), 2);
        
        Assert.assertTrue(updated.get(0).asText().contains("1.1.1.1"));
        Assert.assertTrue(updated.get(0).asText().contains("8080"));
        Assert.assertTrue(updated.get(0).asText().contains(TEST_CLUSTER_NAME));
        Assert.assertTrue(updated.get(0).asText().contains("ephemeral"));
        
        Assert.assertTrue(updated.get(1).asText().contains("2.2.2.2"));
        Assert.assertTrue(updated.get(1).asText().contains("8080"));
        Assert.assertTrue(updated.get(1).asText().contains(TEST_CLUSTER_NAME));
        Assert.assertTrue(updated.get(1).asText().contains("ephemeral"));
    }
    
    @Test
    public void batchDeleteMetadata() throws Exception {
        Instance instance = new Instance("1.1.1.1", 8080, TEST_CLUSTER_NAME);
        instance.setServiceName(TEST_SERVICE_NAME);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        instance.setMetadata(metadata);
        
        Instance instance2 = new Instance("2.2.2.2", 8080, TEST_CLUSTER_NAME);
        instance2.setServiceName(TEST_SERVICE_NAME);
        
        List<Instance> instanceList = new LinkedList<>();
        instanceList.add(instance);
        instanceList.add(instance2);
        
        when(serviceManager
                .batchOperate(ArgumentMatchers.anyString(), ArgumentMatchers.any(InstanceOperationInfo.class),
                        ArgumentMatchers.any(Function.class))).thenReturn(instanceList);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .delete(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/metadata/batch").param("namespace", "public")
                .param("serviceName", TEST_SERVICE_NAME).param("instances",
                        "[{\"ip\":\"1.1.1.1\",\"port\": \"8080\",\"ephemeral\":\"true\",\"clusterName\":\"test-cluster\"},"
                                + "{\"ip\":\"2.2.2.2\",\"port\":\"8080\",\"ephemeral\":\"true\",\"clusterName\":\"test-cluster\"}]")
                .param("metadata", "{\"age\":\"20\",\"name\":\"horizon\"}");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        JsonNode result = JacksonUtils.toObj(actualValue);
        
        JsonNode updated = result.get("updated");
        
        Assert.assertEquals(updated.size(), 2);
        
        Assert.assertTrue(updated.get(0).asText().contains("1.1.1.1"));
        Assert.assertTrue(updated.get(0).asText().contains("8080"));
        Assert.assertTrue(updated.get(0).asText().contains(TEST_CLUSTER_NAME));
        Assert.assertTrue(updated.get(0).asText().contains("ephemeral"));
        
        Assert.assertTrue(updated.get(1).asText().contains("2.2.2.2"));
        Assert.assertTrue(updated.get(1).asText().contains("8080"));
        Assert.assertTrue(updated.get(1).asText().contains(TEST_CLUSTER_NAME));
        Assert.assertTrue(updated.get(1).asText().contains("ephemeral"));
    }
}
