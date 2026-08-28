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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.BasicContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpCompatibilityModeResolverTest {
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void backgroundOperationShouldAlwaysObserveDurableMode() {
        McpLifecycleManagementStateService stateService =
            mock(McpLifecycleManagementStateService.class);
        when(stateService.resolveMode()).thenReturn(McpCompatibilityMode.SYNCING,
            McpCompatibilityMode.LIFECYCLE_MANAGED);
        McpCompatibilityModeResolver resolver = new McpCompatibilityModeResolver(stateService);
        
        assertEquals(McpCompatibilityMode.SYNCING, resolver.resolve());
        assertEquals(McpCompatibilityMode.LIFECYCLE_MANAGED, resolver.resolve());
        verify(stateService, times(2)).resolveMode();
    }
    
    @Test
    void protocolRequestShouldPinFirstResolvedMode() {
        RequestContextHolder.getContext().getBasicContext()
            .setRequestProtocol(BasicContext.HTTP_PROTOCOL);
        McpLifecycleManagementStateService stateService =
            mock(McpLifecycleManagementStateService.class);
        when(stateService.resolveMode()).thenReturn(McpCompatibilityMode.SYNCING,
            McpCompatibilityMode.LIFECYCLE_MANAGED);
        McpCompatibilityModeResolver resolver = new McpCompatibilityModeResolver(stateService);
        
        assertEquals(McpCompatibilityMode.SYNCING, resolver.resolve());
        assertEquals(McpCompatibilityMode.SYNCING, resolver.resolve());
        verify(stateService).resolveMode();
    }
    
    @Test
    void nextProtocolRequestShouldResolveAgain() {
        McpLifecycleManagementStateService stateService =
            mock(McpLifecycleManagementStateService.class);
        when(stateService.resolveMode()).thenReturn(McpCompatibilityMode.SYNCING,
            McpCompatibilityMode.LIFECYCLE_MANAGED);
        McpCompatibilityModeResolver resolver = new McpCompatibilityModeResolver(stateService);
        RequestContextHolder.getContext().getBasicContext()
            .setRequestProtocol(BasicContext.GRPC_PROTOCOL);
        assertEquals(McpCompatibilityMode.SYNCING, resolver.resolve());
        
        RequestContextHolder.removeContext();
        RequestContextHolder.getContext().getBasicContext()
            .setRequestProtocol(BasicContext.GRPC_PROTOCOL);
        assertEquals(McpCompatibilityMode.LIFECYCLE_MANAGED, resolver.resolve());
    }
    
    @Test
    void localCapabilityShouldDelegateToStateService() {
        McpLifecycleManagementStateService stateService =
            mock(McpLifecycleManagementStateService.class);
        when(stateService.localMemberSupportsManagedLifecycle()).thenReturn(false, true);
        McpCompatibilityModeResolver resolver = new McpCompatibilityModeResolver(stateService);
        
        assertFalse(resolver.localMemberSupportsManagedLifecycle());
        assertTrue(resolver.localMemberSupportsManagedLifecycle());
    }
}
