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

package com.alibaba.nacos.address.controller;

import com.alibaba.nacos.address.component.AddressServerGeneratorManager;
import com.alibaba.nacos.address.component.AddressServerManager;
import com.alibaba.nacos.address.constant.AddressServerConstants;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class AddressServerClusterControllerTest {
    
    @Mock
    private ServiceManager serviceManager;
    
    private MockMvc mockMvc;
    
    @Before
    public void before() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AddressServerClusterController(serviceManager, new AddressServerManager(),
                new AddressServerGeneratorManager())).build();
    }
    
    @Test
    public void testPostCluster() throws Exception {
        
        mockMvc.perform(post("/nacos/v1/as/nodes")
                .param("product", "default")
                .param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2"))
                .andExpect(status().isOk());

    }
    
    @Test
    public void testPostClusterWithErrorIps() throws Exception {
        mockMvc.perform(post("/nacos/v1/as/nodes")
                        .param("product", "default")
                        .param("cluster", "serverList")
                        .param("ips", "192.168.1"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testPostClusterThrowException() throws Exception {
    
        Mockito.doThrow(new NacosException(500, "create service error")).when(serviceManager)
                .createServiceIfAbsent(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID), Mockito.eq(
                                Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"),
                        Mockito.eq(false), Mockito.any());
    
        mockMvc.perform(post("/nacos/v1/as/nodes")
                        .param("product", "default")
                        .param("cluster", "serverList")
                        .param("ips", "192.168.1"))
                .andExpect(status().isInternalServerError());
        
    }
    
    @Test
    public void testDeleteCluster() throws Exception {
        
        Mockito.when(serviceManager.getService(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID),
                Mockito.eq(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default")))
                .thenReturn(new Service(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"));
        
        mockMvc.perform(delete("/nacos/v1/as/nodes")
                .param("product", "default")
                .param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2")
        ).andExpect(status().isOk());
    
    }
    
    @Test
    public void testDeleteClusterCannotFindService() throws Exception {
        
        mockMvc.perform(delete("/nacos/v1/as/nodes")
                .param("product", "default")
                .param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2")
        ).andExpect(status().isNotFound());
    }
    
    @Test
    public void testDeleteClusterEmptyIps() throws Exception {
    
        Mockito.when(serviceManager.getService(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID),
                        Mockito.eq(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default")))
                .thenReturn(new Service(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"));
        
        mockMvc.perform(delete("/nacos/v1/as/nodes")
                .param("product", "default")
                .param("cluster", "serverList")
                .param("ips", "")
        ).andExpect(status().isBadRequest());
    }
    
    @Test
    public void testDeleteClusterErrorIps() throws Exception {
    
        Mockito.when(serviceManager.getService(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID),
                        Mockito.eq(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default")))
                .thenReturn(new Service(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"));
    
        mockMvc.perform(delete("/nacos/v1/as/nodes")
                .param("product", "default")
                .param("cluster", "serverList")
                .param("ips", "192.168.1")
        ).andExpect(status().isBadRequest());
    }
    
    @Test
    public void testDeleteClusterThrowException() throws Exception {
    
        Mockito.when(serviceManager.getService(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID),
                        Mockito.eq(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default")))
                .thenReturn(new Service(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"));
        
        Mockito.doThrow(new NacosException(500, "remove service error"))
                .when(serviceManager)
                .removeInstance(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID),
                        Mockito.eq(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"),
                        Mockito.eq(false),
                        Mockito.any());
    
        mockMvc.perform(delete("/nacos/v1/as/nodes")
                .param("product", "default")
                .param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2")
        ).andExpect(status().isInternalServerError());
    }

}
