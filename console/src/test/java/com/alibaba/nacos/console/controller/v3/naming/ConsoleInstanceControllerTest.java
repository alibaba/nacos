/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3.naming;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.console.proxy.naming.InstanceProxy;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ConsoleInstanceControllerTest.
 *
 * @author zhangyukun on:2024/9/5
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleInstanceControllerTest {
    
    @Mock
    private InstanceProxy instanceProxy;
    
    @InjectMocks
    private ConsoleInstanceController consoleInstanceController;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(consoleInstanceController).build();
    }
    
    @Test
    void testGetInstanceList() throws Exception {
        Page<? extends Instance> page = new Page<>();
        doReturn(page).when(instanceProxy)
                .listInstances(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/ns/instance/list")
                .param("namespaceId", "default").param("serviceName", "testService").param("pageNo", "1")
                .param("pageSize", "10");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Page<? extends Instance>> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        
        assertNotNull(result.getData());
    }
    
    @Test
    void testUpdateInstance() throws Exception {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setServiceName("testService");
        instanceForm.setIp("127.0.0.1");
        instanceForm.setPort(8080);
        instanceForm.setWeight(1.0);
        
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        instance.setWeight(1.0);
        
        doNothing().when(instanceProxy).updateInstance(any(InstanceForm.class), any(Instance.class));
        
        Result<String> result = consoleInstanceController.updateInstance(instanceForm);
        
        verify(instanceProxy).updateInstance(any(InstanceForm.class), any(Instance.class));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testUpdateInstanceWithIllegalWeight() throws Exception {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setServiceName("testService");
        instanceForm.setIp("127.0.0.1");
        instanceForm.setPort(8080);
        instanceForm.setWeight(-1.0);
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        instance.setWeight(-1.0);
        
        assertThrows(NacosApiException.class, () -> consoleInstanceController.updateInstance(instanceForm));
        verify(instanceProxy, never()).updateInstance(any(InstanceForm.class), any(Instance.class));
    }
}