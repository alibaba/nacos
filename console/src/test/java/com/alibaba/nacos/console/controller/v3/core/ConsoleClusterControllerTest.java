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

package com.alibaba.nacos.console.controller.v3.core;

import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.proxy.core.ClusterProxy;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * ConsoleClusterControllerTest.
 *
 * @author zhangyukun on:2024/8/28
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleClusterControllerTest {
    
    @Mock
    private ClusterProxy clusterProxy;
    
    @InjectMocks
    private ConsoleClusterController consoleClusterController;
    
    private MockedStatic<EnvUtil> mockedEnvUtil;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(consoleClusterController).build();
        
        mockedEnvUtil = mockStatic(EnvUtil.class);
        mockedEnvUtil.when(() -> EnvUtil.getProperty(anyString())).thenReturn("default_value");
    }
    
    @AfterEach
    void tearDown() {
        mockedEnvUtil.close();
    }
    
    @Test
    void testGetNodeList() throws Exception {
        NacosMember member = new NacosMember();
        member.setIp("127.0.0.1");
        member.setPort(8848);
        Collection<NacosMember> members = List.of(member);
        
        when(clusterProxy.getNodeList(anyString())).thenReturn(members);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/core/cluster/nodes")
                .param("keyword", "127.0.0.1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Collection<NacosMember>> result = new ObjectMapper().readValue(actualValue, new TypeReference<>() {
        });
        
        assertEquals(1, result.getData().size());
        assertEquals("127.0.0.1", result.getData().iterator().next().getIp());
    }
}