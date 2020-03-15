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
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.Application;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @Author: kkyeer
 * @Description: Test if Application Controller functions collect
 * @Date:Created in 20:35 3-15
 * @Modified By:
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ApplicationControllerTest  extends BaseTest {

    @InjectMocks
    private ApplicationController applicationController;

    @InjectMocks
    private InstanceController instanceController;

    private MockMvc mockmvc;

    /**
     * feed service manager app list
     */
    @Before
    public void before() {
        super.before();
        mockmvc = MockMvcBuilders.standaloneSetup(applicationController,instanceController).build();
        List<Application> mockApplications = new ArrayList<>();
        for (int i = 0; i < SERVICE_COUNT; i++) {
            Application application = new Application(IP_PREFIX + i, i);
            application.setInstanceCount(INSTANCE_COUNT);
            mockApplications.add(application);
        }
        Mockito.when(serviceManager.getApplications(Constants.DEFAULT_NAMESPACE_ID, null, null)).thenReturn(mockApplications);
        List<Instance> instanceList = new ArrayList<>();
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            Instance instance = new Instance();
            instance.setIp(IP_PREFIX+"1");
            instance.setPort(1);
            instanceList.add(instance);
        }
        Mockito.when(serviceManager.getInstancesForApp(Constants.DEFAULT_NAMESPACE_ID, null, null)).thenReturn(instanceList);
    }


    /**
     * test full page query
     * @throws Exception
     */
    @Test
    public void testFullPageQueryApp() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String pageQueryResult = mockmvc.perform(
                get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog/applications")
                    .param("pageNo", "" + i)
                    .param("pageSize", "" + 10)
            ).andReturn().getResponse().getContentAsString();
            JSONObject result = JSON.parseObject(pageQueryResult);
            Assert.assertEquals(result.getLongValue("count"), SERVICE_COUNT);
            JSONArray applicationList = result.getJSONArray("applicationList");
            Assert.assertEquals(10, applicationList.size());
            for (int j = 0; j < applicationList.size(); j++) {
                JSONObject app = applicationList.getJSONObject(j);
                int index = (i - 1) * 10 + j;
                Assert.assertEquals(IP_PREFIX + index,app.getString("ip"));
                Assert.assertEquals(Integer.valueOf(index),app.getInteger("port"));
                Assert.assertEquals(Integer.valueOf(INSTANCE_COUNT), app.getInteger("instanceCount") );
            }
        }
    }

    /**
     * test second page query
     * @throws Exception
     */
    @Test
    public void testNotFullPageQueryApp() throws Exception {
        String pageQueryResult = mockmvc.perform(
            get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog/applications")
                .param("pageNo", "3")
                .param("pageSize", "10")
        ).andReturn().getResponse().getContentAsString();
        JSONObject result = JSON.parseObject(pageQueryResult);
        Assert.assertEquals(result.getLongValue("count"), SERVICE_COUNT);
        JSONArray applicationList = result.getJSONArray("applicationList");
        Assert.assertEquals(5, applicationList.size());
        for (int i = 0; i < applicationList.size(); i++) {
            JSONObject app = applicationList.getJSONObject(i);
            int index = 20 + i;
            Assert.assertEquals(IP_PREFIX + index, app.getString("ip"));
            Assert.assertEquals(Integer.valueOf(index), app.getInteger("port"));
            Assert.assertEquals(Integer.valueOf(INSTANCE_COUNT), app.getInteger("instanceCount"));
        }
    }


    private static final int SERVICE_COUNT = 25;
    private static final int INSTANCE_COUNT = 25;
    private static final String IP_PREFIX = "192.169.1.";
}
