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
import com.alibaba.nacos.address.constant.AddressServerConstants;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class ServerListControllerTest {
    
    @Mock
    private ServiceManager serviceManager;
    
    private MockMvc mockMvc;
    
    @Before
    public void before() {
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new ServerListController(serviceManager, new AddressServerGeneratorManager()))
                .build();
    }
    
    @Test
    public void testGetCluster() throws Exception {
    
        final Service service = new Service(
                Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default");
        
        Cluster cluster = new Cluster();
        cluster.setName("serverList");
        cluster.setService(service);
        
        final HashMap<String, Cluster> clusterMap = new HashMap<>(1);
        clusterMap.put("serverList", cluster);
        service.setClusterMap(clusterMap);
        
        List<Instance> list = new ArrayList<>(2);
        list.add(new Instance("192.168.3.1", 8848));
        list.add(new Instance("192.168.3.2", 8848));
        cluster.updateIps(list, false);
        
        Mockito.when(serviceManager.getService(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID),
                Mockito.eq(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default")))
                .thenReturn(service);
        
        mockMvc.perform(get("/nacos/serverList"))
                .andExpect(status().isOk());
    
    }
    
    @Test
    public void testGetClusterCannotFindService() throws Exception {
    
        mockMvc.perform(get("/default/serverList"))
                .andExpect(status().isNotFound());
        
    }
    
    @Test
    public void testGetClusterCannotFindCluster() throws Exception {
    
        final Service service = new Service(
                Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default");
    
        final HashMap<String, Cluster> clusterMap = new HashMap<>(1);
        service.setClusterMap(clusterMap);
    
        Mockito.when(serviceManager.getService(Mockito.eq(Constants.DEFAULT_NAMESPACE_ID),
                        Mockito.eq(Constants.DEFAULT_GROUP + AddressServerConstants.GROUP_SERVICE_NAME_SEP + "nacos.as.default")))
                .thenReturn(service);
    
        mockMvc.perform(get("/nacos/serverList"))
                .andExpect(status().isNotFound());
        
    }
}
