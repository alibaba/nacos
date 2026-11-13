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
 */

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.namespace.model.NamespaceTypeEnum;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** The ordinary namespace API cannot address the reserved AI_HUB namespace. */
class HubNamespaceIsolationS4Test {
    
    @Test
    void ordinaryTypedAndUntypedLookupsRejectTheReservedNamespaceBeforeDbAccess() {
        NamespacePersistService persistence = mock(NamespacePersistService.class);
        NamespaceOperationService service = new NamespaceOperationService(persistence);
        assertThrows(NacosException.class,
            () -> service.getNamespace(AiConstants.Hub.NAMESPACE_ID));
        assertThrows(NacosException.class,
            () -> service.getNamespace(AiConstants.Hub.NAMESPACE_ID, NamespaceTypeEnum.CUSTOM));
        verifyNoInteractions(persistence);
    }
    
    @Test
    void misclassifiedHubRowIsExcludedFromTheOrdinaryNamespaceList() {
        NamespacePersistService persistence = mock(NamespacePersistService.class);
        com.alibaba.nacos.core.namespace.model.TenantInfo hub =
            new com.alibaba.nacos.core.namespace.model.TenantInfo();
        hub.setTenantId(AiConstants.Hub.NAMESPACE_ID);
        hub.setTenantName("internal Hub");
        org.mockito.Mockito.when(persistence.findTenantByKp("1"))
            .thenReturn(java.util.List.of(hub));
        NamespaceOperationService service = new NamespaceOperationService(persistence);
        org.junit.jupiter.api.Assertions.assertTrue(service.getNamespaceList().stream()
            .noneMatch(namespace -> AiConstants.Hub.NAMESPACE_ID.equals(namespace.getNamespace())));
    }
    
}
