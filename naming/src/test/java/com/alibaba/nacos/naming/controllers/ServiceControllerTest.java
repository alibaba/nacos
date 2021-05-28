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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceOperatorV1Impl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ServiceControllerTest extends BaseTest {
    
    @InjectMocks
    private ServiceController serviceController;
    
    @InjectMocks
    private ServiceOperatorV1Impl serviceOperatorV1;
    
    private MockMvc mockmvc;
    
    @Before
    public void before() {
        super.before();
        ReflectionTestUtils.setField(serviceController, "serviceOperatorV1", serviceOperatorV1);
        mockmvc = MockMvcBuilders.standaloneSetup(serviceController).build();
    }
    
    @Test
    public void testList() throws Exception {
        Map<String, Service> serviceNameList = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            serviceNameList.put("DEFAULT_GROUP@@providers:com.alibaba.nacos.controller.test:" + i, new Service());
        }
        
        Mockito.when(serviceManager.chooseServiceMap(Constants.DEFAULT_NAMESPACE_ID)).thenReturn(serviceNameList);
        
        mockmvc.perform(MockMvcRequestBuilders.get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service" + "/list")
                .param("pageNo", "1").param("pageSize", "10").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(MockMvcResultMatchers.jsonPath("$.doms").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.doms").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.count").value(serviceNameList.size()));
    }
}
