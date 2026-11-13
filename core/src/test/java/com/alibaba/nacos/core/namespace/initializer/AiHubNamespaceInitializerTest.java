/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.initializer;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.namespace.model.NamespaceTypeEnum;
import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 * Initializes the AI Hub namespace after the application is ready.
 * @author xyp
 *
 */
@ExtendWith(MockitoExtension.class)
class AiHubNamespaceInitializerTest {
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    private AiHubNamespaceInitializer initializer;
    
    @BeforeEach
    void setUp() {
        initializer = new AiHubNamespaceInitializer(
                namespacePersistService,
                namespaceOperationService);
    }
    
    @Test
    void testHubNamespaceAlreadyExists() throws NacosException {
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setTenantId(AiConstants.Hub.NAMESPACE_ID);
        
        when(namespacePersistService.findTenantByKp(
                String.valueOf(NamespaceTypeEnum.AI_HUB.getType()),
                AiConstants.Hub.NAMESPACE_ID))
                .thenReturn(tenantInfo);
        
        assertDoesNotThrow(initializer::ensureHubNamespace);
        
        verify(namespaceOperationService, never())
                .createNamespace(
                        eq(AiConstants.Hub.NAMESPACE_ID),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        eq(NamespaceTypeEnum.AI_HUB));
    }
    
    @Test
    void testHubNamespaceIdOccupiedByCustomNamespace() {
        when(namespacePersistService.findTenantByKp(
                String.valueOf(NamespaceTypeEnum.AI_HUB.getType()),
                AiConstants.Hub.NAMESPACE_ID))
                .thenReturn(null);
        
        when(namespacePersistService.tenantInfoCountByTenantId(
                AiConstants.Hub.NAMESPACE_ID))
                .thenReturn(1);
        
        assertThrows(IllegalStateException.class,
                initializer::ensureHubNamespace);
    }
    
    @Test
    void testCreateHubNamespace() throws Exception {
        TenantInfo created = new TenantInfo();
        created.setTenantId(AiConstants.Hub.NAMESPACE_ID);
        
        when(namespacePersistService.findTenantByKp(
                String.valueOf(NamespaceTypeEnum.AI_HUB.getType()),
                AiConstants.Hub.NAMESPACE_ID))
                .thenReturn(null)
                .thenReturn(created);
        
        when(namespacePersistService.tenantInfoCountByTenantId(
                AiConstants.Hub.NAMESPACE_ID))
                .thenReturn(0);
        
        assertDoesNotThrow(initializer::ensureHubNamespace);
        
        verify(namespaceOperationService).createNamespace(
                eq(AiConstants.Hub.NAMESPACE_ID),
                eq("Nacos AI Resource Hub"),
                eq("System namespace for Nacos AI Resource Hub"),
                eq(NamespaceTypeEnum.AI_HUB));
    }
}
