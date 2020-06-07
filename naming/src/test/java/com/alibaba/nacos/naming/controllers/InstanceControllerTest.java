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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nkorange
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class InstanceControllerTest extends BaseTest {

    @InjectMocks
    private InstanceController instanceController;

    @Mock
    private RaftPeerSet peerSet;

    private MockMvc mockmvc;

    @Before
    public void before() {
        super.before();
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

        Mockito.when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance")
                .param("serviceName", TEST_SERVICE_NAME)
                .param("ip", "1.1.1.1")
                .param("port", "9999");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();

        Assert.assertEquals("ok", actualValue);
    }

    @Test
    public void deregisterInstance() throws Exception {

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.delete(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance")
                .param("serviceName", TEST_SERVICE_NAME)
                .param("ip", "1.1.1.1")
                .param("port", "9999")
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

        Mockito.when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/list")
                .param("serviceName", TEST_SERVICE_NAME);

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
        Mockito.when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(null);

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/list")
                .param("serviceName", TEST_SERVICE_NAME);

        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        JsonNode result = JacksonUtils.toObj(actualValue);

        JsonNode hosts = result.get("hosts");
        Assert.assertEquals(hosts.size(), 0);
    }
}
