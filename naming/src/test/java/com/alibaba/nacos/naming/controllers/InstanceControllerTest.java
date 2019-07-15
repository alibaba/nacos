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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.InstanceManager;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
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
import org.springframework.web.util.NestedServletException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nkorange
 * @author jifengnan 2019-07-14
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class InstanceControllerTest extends BaseTest {

    @InjectMocks
    private InstanceController instanceController;

    @Mock
    private InstanceManager instanceManager;

    @Mock
    private RaftPeerSet peerSet;

    private MockMvc mockmvc;

    @Before
    public void before() {
        super.before();
        mockmvc = MockMvcBuilders.standaloneSetup(instanceController).build();
    }

    @Test
    public void testUpdate() throws Exception {

        Service service = new Service(TEST_SERVICE_NAME);

        Cluster cluster = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster);

        Instance instance = new Instance("1.1.1.1", 9999, cluster);
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

        builder = MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance")
            .param("serviceName", TEST_SERVICE_NAME)
            .param("ip", "1.1.1.1")
            .param("port", "9999")
            .header("Client-Version", "Nacos-Java-Client:v1.0.0");
        actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();

        Assert.assertEquals("ok", actualValue);
    }

    @Test
    public void testDeregister() throws Exception {

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
    public void testList_instances() throws Exception {

        Service service = new Service(TEST_SERVICE_NAME);

        Cluster cluster = new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service);
        service.addCluster(cluster);

        Instance instance = new Instance("10.10.10.10", 8888, cluster);
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
        JSONObject result = JSON.parseObject(actualValue);

        Assert.assertEquals(TEST_SERVICE_NAME, result.getString("name"));
        JSONArray hosts = result.getJSONArray("hosts");
        Assert.assertNotNull(hosts);
        Assert.assertEquals(hosts.size(), 1);

        JSONObject host = hosts.getJSONObject(0);
        Assert.assertNotNull(host);
        Assert.assertEquals("10.10.10.10", host.getString("ip"));
        Assert.assertEquals(8888, host.getIntValue("port"));
        Assert.assertEquals(2.0, host.getDoubleValue("weight"), 0.001);
    }

    @Test
    public void getNullServiceInstances() throws Exception {
        Mockito.when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(null);

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/list")
                .param("serviceName", TEST_SERVICE_NAME);

        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        JSONObject result = JSON.parseObject(actualValue);

        JSONArray hosts = result.getJSONArray("hosts");
        Assert.assertEquals(hosts.size(), 0);
    }

    @Test
    public void testRegister() throws Exception {
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.post(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance");
        Assert.assertEquals("ok", mockmvc.perform(builder).andReturn().getResponse().getContentAsString());
    }

    @Test
    public void testBeat() throws Exception {
        Instance instance = createInstance(IP1, 1);
        Service service = instance.getCluster().getService();
        Mockito.when(serviceManager.createServiceIfAbsent(TEST_NAMESPACE, TEST_SERVICE_NAME, true)).thenReturn(service);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/beat")
                .param("beat", BEAT_INFO_NO_CLUSTER)
                .param(CommonParams.NAMESPACE_ID, TEST_NAMESPACE)
                .param(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        JSONObject result = JSONObject.parseObject(mockmvc.perform(builder).andReturn().getResponse().getContentAsString());
        Assert.assertEquals(6, result.get("clientBeatInterval"));
        Assert.assertEquals(new Cluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME, service), service.getClusterMap().get(UtilsAndCommons.DEFAULT_CLUSTER_NAME));

        instance.setWeight(2.0);
        instance.getMetadata().put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "6");
        Mockito.when(serviceManager.getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, IP1, 1)).thenReturn(instance);
        Mockito.when(serviceManager.getService(TEST_NAMESPACE, TEST_SERVICE_NAME)).thenReturn(instance.getCluster().getService());
        builder = MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/beat")
            .param("beat", BEAT_INFO)
            .param(CommonParams.NAMESPACE_ID, TEST_NAMESPACE)
            .param(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        result = JSONObject.parseObject(mockmvc.perform(builder).andReturn().getResponse().getContentAsString());
        Assert.assertEquals(6, result.get("clientBeatInterval"));

        Mockito.when(switchDomain.isDefaultInstanceEphemeral()).thenReturn(false);
        builder = MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/beat")
            .param("beat", BEAT_INFO_EPHEMERAL_FALSE)
            .param(CommonParams.NAMESPACE_ID, TEST_NAMESPACE)
            .param(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        result = JSONObject.parseObject(mockmvc.perform(builder).andReturn().getResponse().getContentAsString());
        Assert.assertEquals(5000, result.get("clientBeatInterval"));
    }

    @Test
    public void testBeat_noService() throws Exception {
        expectedException.expect(NestedServletException.class);
        expectedException.expectMessage("service not found: test-service@test-namespace");
        Instance instance = createInstance(IP1, 1);
        Mockito.when(serviceManager.getInstance(TEST_NAMESPACE, TEST_SERVICE_NAME, TEST_CLUSTER_NAME, IP1, 1)).thenReturn(instance);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.put(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/beat")
                .param("beat", BEAT_INFO)
                .param(CommonParams.NAMESPACE_ID, TEST_NAMESPACE)
                .param(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        mockmvc.perform(builder).andReturn().getResponse();
    }


    private static final String BEAT_INFO_EPHEMERAL_FALSE = "{\"ephemeral\":false,\"cluster\":\"test-cluster\",\"ip\":\"1.1.1.1\",\"metadata\":{\"preserved.heart.beat.interval\":\"6\"},\"period\":6,\"port\":1,\"scheduled\":false,\"serviceName\":\"test-group-name@@test-service\",\"stopped\":false,\"weight\":2.0}";
    private static final String BEAT_INFO = "{\"cluster\":\"test-cluster\",\"ip\":\"1.1.1.1\",\"metadata\":{\"preserved.heart.beat.interval\":\"6\"},\"period\":6,\"port\":1,\"scheduled\":false,\"serviceName\":\"test-group-name@@test-service\",\"stopped\":false,\"weight\":2.0}";
    private static final String BEAT_INFO_NO_CLUSTER = "{\"ip\":\"1.1.1.1\",\"metadata\":{\"preserved.heart.beat.interval\":\"6\"},\"period\":6,\"port\":1,\"scheduled\":false,\"serviceName\":\"test-group-name@@test-service\",\"stopped\":false,\"weight\":2.0}";
}
