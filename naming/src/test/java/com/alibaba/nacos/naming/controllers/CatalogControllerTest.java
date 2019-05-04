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
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author jifengnan 2019-04-29
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CatalogControllerTest {

    @Autowired
    private MockMvc mockmvc;

    @MockBean
    private ServiceManager serviceManager;

    @Test
    public void testServiceDetail() throws Exception {
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        service.setProtectThreshold(12.34f);
        service.setGroupName(TEST_GROUP_NAME);
        Cluster cluster = new Cluster(TEST_CLUSTER_NAME, service);
        cluster.setDefaultPort(1);

        service.addCluster(cluster);
        when(serviceManager.getService(anyString(), anyString())).thenReturn(service);
        String result1 = mockmvc.perform(get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog/service")
            .param(CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID)
            .param(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME)
            .param(CommonParams.GROUP_NAME, TEST_GROUP_NAME))
            .andReturn().getResponse().getContentAsString();
        JSONObject result = JSONObject.parseObject(result1);
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
        String result = mockmvc.perform(get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog/service")
            .param(CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID)
            .param(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME)).andReturn().getResponse().getContentAsString();

        Assert.assertTrue(result.contains("test-service is not found!"));
    }

    private static final String TEST_CLUSTER_NAME = "test-cluster";
    private static final String TEST_SERVICE_NAME = "test-service";
    private static final String TEST_GROUP_NAME = "test-group-name";
}
