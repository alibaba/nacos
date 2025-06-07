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

import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.proxy.core.NamespaceProxy;
import com.alibaba.nacos.core.namespace.model.form.CreateNamespaceForm;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConsoleNamespaceControllerTest.
 *
 * @author zhangyukun on:2024/8/28
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleNamespaceControllerTest {
    
    @Mock
    private NamespaceProxy namespaceProxy;
    
    @InjectMocks
    private ConsoleNamespaceController consoleNamespaceController;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(consoleNamespaceController).build();
    }
    
    @Test
    void testGetNamespaceList() throws Exception {
        Namespace namespace = new Namespace();
        namespace.setNamespace("testNamespace");
        namespace.setNamespaceDesc("desc");
        
        List<Namespace> namespaces = Arrays.asList(namespace);
        when(namespaceProxy.getNamespaceList()).thenReturn(namespaces);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/core/namespace/list");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<List<Namespace>> result = new ObjectMapper().readValue(actualValue,
                new TypeReference<Result<List<Namespace>>>() {
                });
        
        assertEquals(1, result.getData().size());
        assertEquals("testNamespace", result.getData().get(0).getNamespace());
    }
    
    @Test
    void testGetNamespaceDetail() throws Exception {
        Namespace namespace = new Namespace();
        namespace.setNamespace("testNamespace");
        namespace.setNamespaceDesc("desc");
        
        when(namespaceProxy.getNamespaceDetail(anyString())).thenReturn(namespace);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/core/namespace")
                .param("namespaceId", "testNamespace");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Namespace> result = new ObjectMapper().readValue(actualValue, new TypeReference<Result<Namespace>>() {
        });
        
        assertEquals("testNamespace", result.getData().getNamespace());
    }
    
    @Test
    void testCreateNamespace() throws Exception {
        when(namespaceProxy.createNamespace("testNamespace", "testNamespaceName", "testDesc")).thenReturn(true);
        CreateNamespaceForm namespaceForm = new CreateNamespaceForm();
        namespaceForm.setCustomNamespaceId("testNamespace");
        namespaceForm.setNamespaceName("testNamespaceName");
        namespaceForm.setNamespaceDesc("testDesc");
        Result<Boolean> result = consoleNamespaceController.createNamespace(namespaceForm);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }
    
    @Test
    void testUpdateNamespace() throws Exception {
        when(namespaceProxy.updateNamespace(any(NamespaceForm.class))).thenReturn(true);
        
        NamespaceForm namespaceForm = new NamespaceForm("testNamespace", "testNamespaceName", "testDesc");
        
        Result<Boolean> result = consoleNamespaceController.updateNamespace(namespaceForm);
        
        verify(namespaceProxy).updateNamespace(any(NamespaceForm.class));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }
    
    @Test
    void testDeleteNamespace() throws Exception {
        when(namespaceProxy.deleteNamespace(anyString())).thenReturn(true);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete("/v3/console/core/namespace")
                .param("namespaceId", "testNamespace");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testCheckNamespaceIdExist() throws Exception {
        when(namespaceProxy.checkNamespaceIdExist(anyString())).thenReturn(true);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/core/namespace/exist")
                .param("customNamespaceId", "testNamespace");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        
        Result<Boolean> result = new ObjectMapper().readValue(actualValue, new TypeReference<Result<Boolean>>() {
        });
        
        assertTrue(result.getData());
    }
    
    @Test
    void testCheckNamespaceIdExistForEmpty() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get("/v3/console/core/namespace/exist")
                .param("customNamespaceId", "");
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String actualValue = response.getContentAsString();
        Result<Boolean> result = new ObjectMapper().readValue(actualValue, new TypeReference<Result<Boolean>>() {
        });
        assertFalse(result.getData());
    }
}
