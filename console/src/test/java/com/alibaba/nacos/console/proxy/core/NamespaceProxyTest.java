/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.console.handler.core.NamespaceHandler;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespaceProxyTest {
    
    private static final String NAMESPACE_ID = "testNamespaceId";
    
    private static final String NAMESPACE_NAME = "testNamespaceName";
    
    private static final String NAMESPACE_DESC = "testNamespaceDesc";
    
    @Mock
    private NamespaceHandler namespaceHandler;
    
    private NamespaceProxy namespaceProxy;
    
    @BeforeEach
    public void setUp() {
        namespaceProxy = new NamespaceProxy(namespaceHandler);
    }
    
    @Test
    public void getNamespaceDetail() throws NacosException {
        String namespaceId = "testNamespaceId";
        Namespace expectedNamespace = new Namespace();
        expectedNamespace.setNamespace(namespaceId);
        expectedNamespace.setNamespaceShowName("Test Namespace");
        
        when(namespaceHandler.getNamespaceDetail(namespaceId)).thenReturn(expectedNamespace);
        
        Namespace actualNamespace = namespaceProxy.getNamespaceDetail(namespaceId);
        
        assertEquals(expectedNamespace.getNamespace(), actualNamespace.getNamespace());
        assertEquals(expectedNamespace.getNamespaceShowName(), actualNamespace.getNamespaceShowName());
    }
    
    @Test
    public void getNamespaceList() throws NacosException {
        List<Namespace> expectedNamespaces = Arrays.asList(new Namespace("namespace1", "Namespace 1"),
                new Namespace("namespace2", "Namespace 2"));
        when(namespaceHandler.getNamespaceList()).thenReturn(expectedNamespaces);
        
        List<Namespace> actualNamespaces = namespaceProxy.getNamespaceList();
        
        assertEquals(expectedNamespaces, actualNamespaces);
    }
    
    @Test
    public void createNamespace() throws NacosException {
        when(namespaceHandler.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC)).thenReturn(true);
        
        Boolean result = namespaceProxy.createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC);
        
        assertTrue(result);
        verify(namespaceHandler, times(1)).createNamespace(NAMESPACE_ID, NAMESPACE_NAME, NAMESPACE_DESC);
    }
    
    @Test
    public void updateNamespace() throws NacosException {
        NamespaceForm namespaceForm = new NamespaceForm("namespaceId", "namespaceName", "namespaceDesc");
        when(namespaceHandler.updateNamespace(namespaceForm)).thenReturn(true);
        
        Boolean result = namespaceProxy.updateNamespace(namespaceForm);
        
        assertTrue(result);
        verify(namespaceHandler, times(1)).updateNamespace(namespaceForm);
    }
    
    @Test
    public void deleteNamespace() throws NacosException {
        String namespaceId = "testNamespaceId";
        when(namespaceHandler.deleteNamespace(namespaceId)).thenReturn(true);
        
        Boolean result = namespaceProxy.deleteNamespace(namespaceId);
        
        assertTrue(result);
        verify(namespaceHandler, times(1)).deleteNamespace(namespaceId);
    }
    
    @Test
    public void checkNamespaceIdExist() throws NacosException {
        when(namespaceHandler.checkNamespaceIdExist(NAMESPACE_ID)).thenReturn(true);
        
        Boolean result = namespaceProxy.checkNamespaceIdExist(NAMESPACE_ID);
        
        assertTrue(result);
        verify(namespaceHandler, times(1)).checkNamespaceIdExist(NAMESPACE_ID);
    }
    
}
