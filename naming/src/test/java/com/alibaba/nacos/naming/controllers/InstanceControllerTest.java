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
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.raft.PeerSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author dungu.zpf
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class InstanceControllerTest extends BaseTest {

    @InjectMocks
    private InstanceController instanceController;

    @Mock
    private PeerSet peerSet;

    private MockMvc mockmvc;

    @Before
    public void before() {
        super.before();
        mockmvc = MockMvcBuilders.standaloneSetup(instanceController).build();
    }

    @Test
    public void registerInstance() throws Exception {

        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.test.1");

        Cluster cluster = new Cluster();
        cluster.setName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        cluster.setDom(domain);
        domain.addCluster(cluster);

        IpAddress ipAddress = new IpAddress();
        ipAddress.setIp("1.1.1.1");
        ipAddress.setPort(9999);
        List<IpAddress> ipList = new ArrayList<IpAddress>();
        ipList.add(ipAddress);
        domain.updateIPs(ipList, false);

        Mockito.when(domainsManager.getDomain("nacos.test.1")).thenReturn(domain);

        Mockito.when(domainsManager.addLock("nacos.test.1")).thenReturn(new ReentrantLock());

        MockHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.put("/naming/instance")
                        .param("serviceName", "nacos.test.1")
                        .param("ip", "1.1.1.1")
                        .param("port", "9999");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();

        Assert.assertEquals("ok", actualValue);
    }

    @Test
    public void deregisterInstance() throws Exception {

        MockHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.delete("/naming/instance")
                        .param("serviceName", "nacos.test.1")
                        .param("ip", "1.1.1.1")
                        .param("port", "9999")
                        .param("clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();

        Assert.assertEquals("ok", actualValue);
    }

    @Test
    public void getInstances() throws Exception {

        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.test.1");

        Cluster cluster = new Cluster();
        cluster.setName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        cluster.setDom(domain);
        domain.addCluster(cluster);

        IpAddress ipAddress = new IpAddress();
        ipAddress.setIp("10.10.10.10");
        ipAddress.setPort(8888);
        ipAddress.setWeight(2.0);
        List<IpAddress> ipList = new ArrayList<IpAddress>();
        ipList.add(ipAddress);
        domain.updateIPs(ipList, false);

        Mockito.when(domainsManager.getDomain("nacos.test.1")).thenReturn(domain);

        MockHttpServletRequestBuilder builder =
                MockMvcRequestBuilders.get("/naming/instances")
                        .param("serviceName", "nacos.test.1");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        JSONObject result = JSON.parseObject(actualValue);

        Assert.assertEquals("nacos.test.1", result.getString("dom"));
        JSONArray hosts = result.getJSONArray("hosts");
        Assert.assertTrue(hosts != null);
        Assert.assertNotNull(hosts);
        Assert.assertEquals(hosts.size(), 1);

        JSONObject host = hosts.getJSONObject(0);
        Assert.assertNotNull(host);
        Assert.assertEquals("10.10.10.10", host.getString("ip"));
        Assert.assertEquals(8888, host.getIntValue("port"));
        Assert.assertEquals(2.0, host.getDoubleValue("weight"), 0.001);
    }
}
