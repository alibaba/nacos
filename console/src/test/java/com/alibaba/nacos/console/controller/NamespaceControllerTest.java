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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.service.repository.CommonPersistService;
import com.alibaba.nacos.console.model.Namespace;
import com.alibaba.nacos.console.model.NamespaceAllInfo;
import com.alibaba.nacos.console.service.NamespaceOperationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NamespaceControllerTest {
    
    @InjectMocks
    private NamespaceController namespaceController;
    
    @Mock
    private CommonPersistService commonPersistService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Before
    public void setUp() {
    
    }
    
    @Test
    public void testGetNamespaces() throws Exception {
        Namespace namespace = new Namespace("", "public");
        when(namespaceOperationService.getNamespaceList()).thenReturn(Collections.singletonList(namespace));
        RestResult<List<Namespace>> actual = namespaceController.getNamespaces();
        assertTrue(actual.ok());
        assertEquals(200, actual.getCode());
        assertEquals(namespace, actual.getData().get(0));
    }
    
    @Test
    public void testGetNamespaceByNamespaceId() throws Exception {
        NamespaceAllInfo namespace = new NamespaceAllInfo("", "public", 0, 0, 0, "");
        when(namespaceOperationService.getNamespace("")).thenReturn(namespace);
        assertEquals(namespace, namespaceController.getNamespace(""));
    }
    
    @Test
    public void testCreateNamespaceWithCustomId() throws Exception {
        namespaceController.createNamespace("test-Id", "testName", "testDesc");
        verify(namespaceOperationService).createNamespace("test-Id", "testName", "testDesc");
    }
    
    @Test
    public void testCreateNamespaceWithIllegalCustomId() throws Exception {
        assertFalse(namespaceController.createNamespace("test.Id", "testName", "testDesc"));
        verify(namespaceOperationService, never()).createNamespace("test.Id", "testName", "testDesc");
    }
    
    @Test
    public void testCreateNamespaceWithLongCustomId() throws Exception {
        StringBuilder longId = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            longId.append("a");
        }
        assertFalse(namespaceController.createNamespace(longId.toString(), "testName", "testDesc"));
        verify(namespaceOperationService, never()).createNamespace(longId.toString(), "testName", "testDesc");
    }
    
    @Test
    public void testCreateNamespaceWithAutoId() throws Exception {
        assertFalse(namespaceController.createNamespace("", "testName", "testDesc"));
        verify(namespaceOperationService)
                .createNamespace(matches("[A-Za-z\\d]{8}-[A-Za-z\\d]{4}-[A-Za-z\\d]{4}-[A-Za-z\\d]{4}-[A-Za-z\\d]{12}"),
                        eq("testName"), eq("testDesc"));
    }
    
    @Test
    public void testCreateNamespaceFailure() throws NacosException {
        when(namespaceOperationService.createNamespace(anyString(), anyString(), anyString()))
                .thenThrow(new NacosException(500, "test"));
        assertFalse(namespaceController.createNamespace("", "testName", "testDesc"));
    }
    
    @Test
    public void testCheckNamespaceIdExist() throws Exception {
        when(commonPersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        when(commonPersistService.tenantInfoCountByTenantId("123")).thenReturn(0);
        assertFalse(namespaceController.checkNamespaceIdExist(""));
        assertTrue(namespaceController.checkNamespaceIdExist("public"));
        assertFalse(namespaceController.checkNamespaceIdExist("123"));
    }
    
    @Test
    public void testEditNamespace() {
        namespaceController.editNamespace("test-Id", "testName", "testDesc");
        verify(namespaceOperationService).editNamespace("test-Id", "testName", "testDesc");
    }
    
    @Test
    public void deleteConfig() throws Exception {
        namespaceController.deleteNamespace("test-Id");
        verify(namespaceOperationService).removeNamespace("test-Id");
    }
}
