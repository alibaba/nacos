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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class ServerListControllerTest {
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    private Service service;
    
    private MockMvc mockMvc;
    
    @Before
    public void before() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(
                new ServerListController(new AddressServerGeneratorManager(), metadataManager, serviceStorage)).build();
        service = Service
                .newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "nacos.as.default", false);
        ServiceManager.getInstance().getSingleton(service);
    }
    
    @After
    public void tearDown() {
        ServiceManager.getInstance().removeSingleton(service);
    }
    
    @Test
    public void testGetCluster() throws Exception {
        
        final Service service = Service
                .newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "nacos.as.default", false);
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.getClusters().put("serverList", new ClusterMetadata());
        when(metadataManager.getServiceMetadata(service)).thenReturn(Optional.of(serviceMetadata));
        List<Instance> list = new ArrayList<>(2);
        list.add(new Instance());
        list.add(new Instance());
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(list);
        when(serviceStorage.getData(service)).thenReturn(serviceInfo);
        mockMvc.perform(get("/nacos/serverList")).andExpect(status().isOk());
    }
    
    @Test
    public void testGetClusterCannotFindService() throws Exception {
        tearDown();
        mockMvc.perform(get("/default/serverList")).andExpect(status().isNotFound());
        
    }
    
    @Test
    public void testGetClusterCannotFindCluster() throws Exception {
        mockMvc.perform(get("/nacos/serverList")).andExpect(status().isNotFound());
        
    }
}
