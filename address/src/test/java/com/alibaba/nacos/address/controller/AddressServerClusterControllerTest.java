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
import com.alibaba.nacos.naming.core.ClusterOperator;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.After;
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
    private InstanceOperator instanceOperator;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ClusterOperator clusterOperator;
    
    private MockMvc mockMvc;
    
    @Before
    public void before() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AddressServerClusterController(instanceOperator, metadataManager, clusterOperator,
                        new AddressServerManager(), new AddressServerGeneratorManager())).build();
        Service service = Service
                .newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "nacos.as.default", false);
        ServiceManager.getInstance().getSingleton(service);
    }
    
    @After
    public void tearDown() {
        Service service = Service
                .newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "nacos.as.default", false);
        ServiceManager.getInstance().removeSingleton(service);
    }
    
    @Test
    public void testPostCluster() throws Exception {
        
        mockMvc.perform(post("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2")).andExpect(status().isOk());
        
    }
    
    @Test
    public void testPostClusterWithErrorIps() throws Exception {
        mockMvc.perform(post("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "192.168.1")).andExpect(status().isBadRequest());
    }
    
    @Test
    public void testPostClusterThrowException() throws Exception {
        
        Mockito.doThrow(new NacosException(500, "create service error")).when(clusterOperator)
                .updateClusterMetadata(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID), Mockito.eq(
                        Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"),
                        Mockito.eq("serverList"), Mockito.any());
        
        mockMvc.perform(post("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "192.168.1")).andExpect(status().isInternalServerError());
        
    }
    
    @Test
    public void testDeleteCluster() throws Exception {
        mockMvc.perform(delete("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2")).andExpect(status().isOk());
    }
    
    @Test
    public void testDeleteClusterCannotFindService() throws Exception {
        tearDown();
        mockMvc.perform(delete("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2")).andExpect(status().isNotFound());
    }
    
    @Test
    public void testDeleteClusterEmptyIps() throws Exception {
        mockMvc.perform(delete("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "")).andExpect(status().isBadRequest());
    }
    
    @Test
    public void testDeleteClusterErrorIps() throws Exception {
        mockMvc.perform(delete("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "192.168.1")).andExpect(status().isBadRequest());
    }
    
    @Test
    public void testDeleteClusterThrowException() throws Exception {
        Mockito.doThrow(new NacosException(500, "remove service error")).when(instanceOperator)
                .removeInstance(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID), Mockito.eq(
                        Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default"),
                        Mockito.any());
        
        mockMvc.perform(delete("/nacos/v1/as/nodes").param("product", "default").param("cluster", "serverList")
                .param("ips", "192.168.3.1,192.168.3.2")).andExpect(status().isInternalServerError());
    }
    
}
