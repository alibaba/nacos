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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * {@link PrometheusController} unit tests.
 *
 * @author karsonto
 * @date 2023-02-01 10:56
 */
@ExtendWith(MockitoExtension.class)
public class PrometheusControllerTest {
    
    private final String nameSpace = "A";
    
    private final String group = "B";
    
    private final String name = "C";
    
    @InjectMocks
    private PrometheusController prometheusController;
    
    @Mock
    private InstanceOperatorClientImpl instanceServiceV2;
    
    private Service service;
    
    private List testInstanceList;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NacosException {
        ServiceManager serviceManager = ServiceManager.getInstance();
        service = Service.newService(nameSpace, group, name);
        serviceManager.getSingleton(service);
        testInstanceList = new ArrayList<>();
        testInstanceList.add(prepareInstance("A", "127.0.0.1", 8080, Collections.singletonMap("__meta_key", "value")));
        testInstanceList.add(prepareInstance("A", "127.0.0.1", 8081, Collections.singletonMap("__meta_key", "value2")));
        mockMvc = MockMvcBuilders.standaloneSetup(prometheusController).build();
    }
    
    private Instance prepareInstance(String clusterName, String ip, int port, Map<String, String> metadata) {
        Instance instance = new Instance();
        instance.setClusterName("A");
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        instance.setMetadata(metadata);
        return instance;
    }
    
    @AfterEach
    public void tearDown() {
        ServiceManager serviceManager = ServiceManager.getInstance();
        serviceManager.removeSingleton(service);
    }
    
    @Test
    public void testMetric() throws Exception {
        when(instanceServiceV2.listAllInstances(nameSpace, NamingUtils.getGroupedName(name, group))).thenReturn(testInstanceList);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(ApiConstants.PROMETHEUS_CONTROLLER_PATH);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(testInstanceList.size(), JacksonUtils.toObj(response.getContentAsString()).size());
    }
    
    @Test
    public void testMetricNamespace() throws Exception {
        when(instanceServiceV2.listAllInstances(nameSpace, NamingUtils.getGroupedName(name, group))).thenReturn(testInstanceList);
        String prometheusNamespacePath = ApiConstants.PROMETHEUS_CONTROLLER_NAMESPACE_PATH.replace("{namespaceId}", nameSpace);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(prometheusNamespacePath);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(testInstanceList.size(), JacksonUtils.toObj(response.getContentAsString()).size());
    }
    
    @Test
    public void testMetricNamespaceService() throws Exception {
        when(instanceServiceV2.listAllInstances(nameSpace, NamingUtils.getGroupedName(name, group))).thenReturn(testInstanceList);
        String prometheusNamespaceServicePath = ApiConstants.PROMETHEUS_CONTROLLER_SERVICE_PATH.replace("{namespaceId}", nameSpace);
        prometheusNamespaceServicePath = prometheusNamespaceServicePath.replace("{service}", service.getName());
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(prometheusNamespaceServicePath);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(testInstanceList.size(), JacksonUtils.toObj(response.getContentAsString()).size());
    }
    
    @Test
    public void testEmptyMetricNamespaceService() throws Exception {
        String prometheusNamespaceServicePath = ApiConstants.PROMETHEUS_CONTROLLER_SERVICE_PATH.replace("{namespaceId}", nameSpace);
        prometheusNamespaceServicePath = prometheusNamespaceServicePath.replace("{service}", "D");  //query non-existed service
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(prometheusNamespaceServicePath);
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(0, JacksonUtils.toObj(response.getContentAsString()).size());
    }
    
}
