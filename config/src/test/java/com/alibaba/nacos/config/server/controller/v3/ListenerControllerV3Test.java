/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.listener.ConfigListenerStateDelegate;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.core.env.StandardEnvironment;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListenerControllerV3Test {
    
    ListenerControllerV3 listenerControllerV3;
    
    private MockMvc mockmvc;
    
    @Mock
    private ConfigListenerStateDelegate configListenerStateDelegate;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        listenerControllerV3 = new ListenerControllerV3(configListenerStateDelegate);
        mockmvc = MockMvcBuilders.standaloneSetup(listenerControllerV3).build();
    }
    
    @Test
    void testGetAllSubClientConfigByIp() throws Exception {
        
        ConfigListenerInfo sampleResult = new ConfigListenerInfo();
        Map<String, String> map = new HashMap<>();
        map.put("test", "test");
        sampleResult.setListenersStatus(map);
        when(configListenerStateDelegate.getListenerStateByIp("localhost", true))
            .thenReturn(sampleResult);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH)
                .param("ip", "localhost").param("all", "true").param("namespaceId", "test")
                .param("sampleTime", "1");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("0", JacksonUtils.toObj(actualValue).get("code").toString());
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigListenerInfo configListenerInfo = JacksonUtils.toObj(data, ConfigListenerInfo.class);
        Map<String, String> resultMap = configListenerInfo.getListenersStatus();
        assertEquals(ConfigListenerInfo.QUERY_TYPE_IP, configListenerInfo.getQueryType());
        assertEquals(map.get("test"), resultMap.get("test"));
        
    }
    
    @Test
    void testGetAllSubClientConfigByIpEmptyListeners() throws Exception {
        ConfigListenerInfo sampleResult = new ConfigListenerInfo();
        when(configListenerStateDelegate.getListenerStateByIp("localhost", true))
            .thenReturn(sampleResult);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH)
                .param("ip", "localhost").param("all", "true");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("0", JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testGetAllSubClientConfigByIpWithNamespaceFilter() throws Exception {
        ConfigListenerInfo sampleResult = new ConfigListenerInfo();
        Map<String, String> map = new HashMap<>();
        map.put("dataId+group+ns1", "md5a");
        map.put("dataId+group+ns2", "md5b");
        sampleResult.setListenersStatus(map);
        when(configListenerStateDelegate.getListenerStateByIp("localhost", true))
            .thenReturn(sampleResult);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH)
                .param("ip", "localhost").param("all", "false")
                .param("namespaceId", "ns1");
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        assertEquals("0", JacksonUtils.toObj(actualValue).get("code").toString());
    }
    
    @Test
    void testGetAllSubClientConfigByIpWithAllFlag() throws Exception {
        ConfigListenerInfo sampleResult = new ConfigListenerInfo();
        Map<String, String> map = new HashMap<>();
        map.put("dataId+group+tenant", "md5");
        sampleResult.setListenersStatus(map);
        when(configListenerStateDelegate.getListenerStateByIp("localhost", true))
            .thenReturn(sampleResult);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH)
                .param("ip", "localhost").param("all", "true");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigListenerInfo configListenerInfo = JacksonUtils.toObj(data, ConfigListenerInfo.class);
        assertEquals("md5", configListenerInfo.getListenersStatus().get("dataId+group+tenant"));
    }
    
    @Test
    void testGetAllSubClientConfigByIpKeepsDefaultNamespaceWhenAllIsFalse()
        throws Exception {
        ConfigListenerInfo sampleResult = new ConfigListenerInfo();
        Map<String, String> map = new HashMap<>();
        map.put("dataId+group", "md5");
        sampleResult.setListenersStatus(map);
        when(configListenerStateDelegate.getListenerStateByIp("localhost", true))
            .thenReturn(sampleResult);
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH)
                .param("ip", "localhost").param("all", "false");
        
        String actualValue =
            mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigListenerInfo configListenerInfo = JacksonUtils.toObj(data, ConfigListenerInfo.class);
        assertEquals("md5", configListenerInfo.getListenersStatus().get("dataId+group"));
    }
    
}
