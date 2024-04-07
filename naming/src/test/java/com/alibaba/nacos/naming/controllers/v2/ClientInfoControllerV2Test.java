/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientInfoControllerV2Test extends BaseTest {
    
    @InjectMocks
    ClientInfoControllerV2 clientInfoControllerV2;
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    private MockMvc mockmvc;
    
    private IpPortBasedClient ipPortBasedClient;
    
    private ConnectionBasedClient connectionBasedClient;
    
    private static final String URL =
            UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_CLIENT_CONTEXT;
    
    @Before
    public void before() {
        when(clientManager.allClientId()).thenReturn(Arrays.asList("127.0.0.1:8080#test1", "test2#test2"));
        when(clientManager.contains(anyString())).thenReturn(true);
        mockmvc = MockMvcBuilders.standaloneSetup(clientInfoControllerV2).build();
        ipPortBasedClient = new IpPortBasedClient("127.0.0.1:8080#test1", false);
        connectionBasedClient = new ConnectionBasedClient("test2", true, 1L);
    }
    
    @Test
    public void testGetClientList() throws Exception {
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/list");
        MockHttpServletResponse response = mockmvc.perform(mockHttpServletRequestBuilder).andReturn().getResponse();
        Assert.assertEquals(200, response.getStatus());
        JsonNode jsonNode = JacksonUtils.toObj(response.getContentAsString()).get("data");
        Assert.assertEquals(2, jsonNode.size());
    }
    
    @Test
    public void testGetClientDetail() throws Exception {
        when(clientManager.getClient("test1")).thenReturn(ipPortBasedClient);
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL)
                .param("clientId", "test1");
        MockHttpServletResponse response = mockmvc.perform(mockHttpServletRequestBuilder).andReturn().getResponse();
        Assert.assertEquals(200, response.getStatus());
    }

    @Test
    public void testGetPublishedServiceList() throws Exception {
        // single instance
        when(clientManager.getClient("test1")).thenReturn(connectionBasedClient);
        Service service = Service.newService("test", "test", "test");
        connectionBasedClient.addServiceInstance(service, new InstancePublishInfo("127.0.0.1", 8848));
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/publish/list")
                .param("clientId", "test1");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));
        // batch instances
        BatchInstancePublishInfo instancePublishInfo = new BatchInstancePublishInfo();
        instancePublishInfo.setInstancePublishInfos(Arrays.asList(new InstancePublishInfo("127.0.0.1", 8848),
                new InstancePublishInfo("127.0.0.1", 8849)));
        connectionBasedClient.addServiceInstance(service, instancePublishInfo);
        mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/publish/list")
                .param("clientId", "test1");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2));
    }

    @Test
    public void testGetPublishedClientList() throws Exception {
        String baseTestKey = "nacos-getPublishedClientList-test";
        // single instance
        Service service = Service.newService(baseTestKey, baseTestKey, baseTestKey);
        when(clientServiceIndexesManager.getAllClientsRegisteredService(service)).thenReturn(Arrays.asList("test"));
        when(clientManager.getClient("test")).thenReturn(connectionBasedClient);
        connectionBasedClient.addServiceInstance(service, new InstancePublishInfo("127.0.0.1", 8848));
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/service/publisher/list")
                .param("namespaceId", baseTestKey)
                .param("groupName", baseTestKey)
                .param("serviceName", baseTestKey)
                .param("ip", "127.0.0.1")
                .param("port", "8848");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));

        // batch instances
        when(clientServiceIndexesManager.getAllClientsRegisteredService(service)).thenReturn(Arrays.asList("test"));
        when(clientManager.getClient("test")).thenReturn(connectionBasedClient);
        BatchInstancePublishInfo instancePublishInfo = new BatchInstancePublishInfo();
        instancePublishInfo.setInstancePublishInfos(Arrays.asList(new InstancePublishInfo("127.0.0.1", 8848),
                new InstancePublishInfo("127.0.0.1", 8849)));
        connectionBasedClient.addServiceInstance(service, instancePublishInfo);
        mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/service/publisher/list")
                .param("namespaceId", baseTestKey)
                .param("groupName", baseTestKey)
                .param("serviceName", baseTestKey)
                .param("ip", "127.0.0.1")
                .param("port", "8848");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));
    }
}
