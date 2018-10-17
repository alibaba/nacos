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
/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2018 All Rights Reserved.
 */

package com.alibaba.nacos.naming.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.core.DomainsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author en.xuze@alipay.com
 * @version $Id: APICommandsTest.java, v 0.1 2018年5月14日 下午4:31:13 en.xuze@alipay.com Exp $
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class APICommandsTest {

    @InjectMocks
    private ApiCommands apiCommands;
    @Mock
    private DomainsManager domainsManager;
    private MockMvc mockmvc;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        mockmvc = MockMvcBuilders.standaloneSetup(apiCommands).build();
    }

    @Test
    public void testDomCount() throws Exception {
        int mockValue = 5;
        JSONObject expectedResult = new JSONObject();
        expectedResult.put("count", mockValue);
        Mockito.when(domainsManager.getDomCount()).thenReturn(mockValue);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/naming/api/domCount");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertTrue("UnitTest:APICommands.domCount failure!", expectedResult.toString().equals(actualValue));
    }

    @Test
    public void dom() throws Exception {
        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.domain.1");
        Mockito.when(domainsManager.getDomain("nacos.domain.1")).thenReturn(domain);

        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/naming/api/dom")
                .param("dom", "nacos.domain.1");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertNotNull(actualValue);
        JSONObject json = JSON.parseObject(actualValue);
        Assert.assertNotNull(json);
        Assert.assertEquals("nacos.domain.1",json.getString("name"));
    }

    @Test
    public void ip4Dom() throws Exception {

        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.domain.1");
        Cluster cluster = new Cluster();
        cluster.setName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        cluster.setDom(domain);
        domain.addCluster(cluster);
        IpAddress ipAddress = new IpAddress();
        ipAddress.setIp("1.1.1.1");
        ipAddress.setPort(1234);
        List<IpAddress> list = new ArrayList<IpAddress>();
        list.add(ipAddress);

        domain.onChange("iplist", JSON.toJSONString(list));

        Mockito.when(domainsManager.getDomain("nacos.domain.1")).thenReturn(domain);

        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/naming/api/ip4Dom")
                .param("dom", "nacos.domain.1");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        Assert.assertNotNull(actualValue);
        JSONObject json = JSON.parseObject(actualValue);
        Assert.assertNotNull(json);
        JSONArray ips = json.getJSONArray("ips");
        Assert.assertNotNull(ips);
        Assert.assertEquals(1, ips.size());
        Assert.assertEquals("1.1.1.1", ips.getJSONObject(0).getString("ip"));
        Assert.assertEquals(1234, ips.getJSONObject(0).getIntValue("port"));
    }
}
