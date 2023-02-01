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

package com.alibaba.nacos.prometheus.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.prometheus.api.ApiConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * {@link PrometheusController} unit tests.
 *
 * @author karsonto
 * @date 2023-02-01 10:56
 */
@RunWith(MockitoJUnitRunner.class)
public class PrometheusControllerTest {
    
    @InjectMocks
    private PrometheusController prometheusController;
    
    @Mock
    private InstanceOperatorClientImpl instanceServiceV2;
    
    private Service service;
    
    private final String nameSpace = "A";
    
    private final String group = "B";
    
    private final String name = "C";
    
    private Instance instance;
    
    private List testInstanceList;
    
    private MockMvc mockMvc;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NacosException {
        ServiceManager serviceManager = ServiceManager.getInstance();
        service = Service.newService(nameSpace, group, name);
        serviceManager.getSingleton(service);
        testInstanceList = new ArrayList<>();
        instance = new Instance();
        instance.setClusterName("A");
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        testInstanceList.add(instance);
        mockMvc = MockMvcBuilders.standaloneSetup(prometheusController).build();
    }
    
    @After
    public void tearDown() throws Exception {
        ServiceManager serviceManager = ServiceManager.getInstance();
        serviceManager.removeSingleton(service);
    }
    
    @Test
    public void testMetric() throws Exception {
        when(instanceServiceV2.listAllInstances(nameSpace, NamingUtils.getGroupedName(name, group))).thenReturn(
                testInstanceList);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(ApiConstants.PROMETHEUS_CONTROLLER_PATH);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals(200, response.getStatus());
        assertEquals(testInstanceList.size(), JacksonUtils.toObj(response.getContentAsString()).size());
    }
    
}
