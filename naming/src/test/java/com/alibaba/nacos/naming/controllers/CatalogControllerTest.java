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

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.exception.NacosException;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.isA;
import static org.mockito.Mockito.when;

/**
 * @author jifengnan 2019-04-29
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class CatalogControllerTest extends BaseTest {
    @InjectMocks
    private CatalogController catalogController;

    private MockMvc mockmvc;

    @Before
    public void before() {
        super.before();
        mockmvc = MockMvcBuilders.standaloneSetup(catalogController).build();
    }

    @Test
    public void testServiceDetail() throws Exception {
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        service.setProtectThreshold(12.34f);
        service.setGroupName(TEST_GROUP_NAME);
        Cluster cluster = new Cluster(TEST_CLUSTER_NAME, service);
        cluster.setDefaultPort(1);

        service.addCluster(cluster);
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);

        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog/service")
                .param("namespaceId", Constants.DEFAULT_NAMESPACE_ID)
                .param("serviceName", TEST_SERVICE_NAME);
        JSONObject result = JSONObject.parseObject(mockmvc.perform(builder).andReturn().getResponse().getContentAsString());

        JSONObject serviceResult = (JSONObject) result.get("service");
        Assert.assertEquals(TEST_SERVICE_NAME, serviceResult.get("name"));
        Assert.assertEquals(12.34, Float.parseFloat(serviceResult.get("protectThreshold").toString()), 0.01);
        Assert.assertEquals(TEST_GROUP_NAME, serviceResult.get("groupName"));

        JSONArray clusterResults = (JSONArray) result.get("clusters");
        Assert.assertEquals(1, clusterResults.size());
        JSONObject clusterResult = (JSONObject) clusterResults.get(0);
        Assert.assertEquals(TEST_CLUSTER_NAME, clusterResult.get("name"));
        Assert.assertEquals(1, Integer.parseInt(clusterResult.get("defaultPort").toString()));
        Assert.assertTrue(Boolean.parseBoolean(clusterResult.get("useIPPort4Check").toString()));
        Assert.assertEquals(TEST_SERVICE_NAME, clusterResult.get("serviceName"));
        Assert.assertEquals(80, Integer.parseInt(clusterResult.get("defaultCheckPort").toString()));
    }

    @Test
    public void testServiceDetailNotFound() throws Exception {
        expectedException.expectCause(isA(NacosException.class));
        expectedException.expectMessage(containsString("serivce test-service is not found!"));
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog/service")
                .param("namespaceId", Constants.DEFAULT_NAMESPACE_ID)
                .param("serviceName", TEST_SERVICE_NAME);
        mockmvc.perform(builder);
    }
}
