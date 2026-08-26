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

import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpLifecycleManagementStateServiceTest {
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    private McpLifecycleManagementStateService stateService;
    
    @BeforeEach
    void setUp() {
        stateService = new McpLifecycleManagementStateService(configQueryChainService);
    }
    
    @Test
    void testValidMarkerEnablesAndPermanentlyCachesManagedState() {
        when(configQueryChainService.handle(any())).thenReturn(response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            "{\"schemaVersion\":1,\"state\":\"LIFECYCLE_MANAGED\"}"));
        
        assertTrue(stateService.isLifecycleManaged());
        assertTrue(stateService.isLifecycleManaged());
        verify(configQueryChainService, times(1)).handle(any());
        ArgumentCaptor<ConfigQueryChainRequest> captor = ArgumentCaptor.forClass(
            ConfigQueryChainRequest.class);
        verify(configQueryChainService).handle(captor.capture());
        assertEquals(McpLifecycleManagementStateService.MIGRATION_DATA_ID,
            captor.getValue().getDataId());
        assertEquals(McpLifecycleManagementStateService.INTERNAL_GROUP,
            captor.getValue().getGroup());
        assertEquals(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID,
            captor.getValue().getTenant());
    }
    
    @Test
    void testMissingMarkerKeepsSyncingAndCachesNegativeResultBriefly() {
        when(configQueryChainService.handle(any())).thenReturn(response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND, null));
        
        assertFalse(stateService.isLifecycleManaged());
        assertFalse(stateService.isLifecycleManaged());
        verify(configQueryChainService, times(1)).handle(any());
    }
    
    @Test
    void testInvalidMarkersFailClosed() {
        when(configQueryChainService.handle(any())).thenReturn(response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            "{\"schemaVersion\":2,\"state\":\"LIFECYCLE_MANAGED\"}"));
        assertFalse(stateService.isLifecycleManaged());
        
        stateService = new McpLifecycleManagementStateService(configQueryChainService);
        when(configQueryChainService.handle(any())).thenReturn(response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            "{\"schemaVersion\":1,\"state\":\"SYNCING\"}"));
        assertFalse(stateService.isLifecycleManaged());
        
        stateService = new McpLifecycleManagementStateService(configQueryChainService);
        when(configQueryChainService.handle(any())).thenReturn(response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            "{\"schemaVersion\":\"1\",\"state\":\"LIFECYCLE_MANAGED\"}"));
        assertFalse(stateService.isLifecycleManaged());
        
        stateService = new McpLifecycleManagementStateService(configQueryChainService);
        when(configQueryChainService.handle(any())).thenReturn(response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL, "invalid"));
        assertFalse(stateService.isLifecycleManaged());
    }
    
    @Test
    void testNullAndFailedResponsesFailClosed() {
        when(configQueryChainService.handle(any())).thenReturn(null);
        assertFalse(stateService.isLifecycleManaged());
        
        stateService = new McpLifecycleManagementStateService(configQueryChainService);
        ConfigQueryChainResponse failed = response(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT, null);
        failed.setMessage("conflict");
        when(configQueryChainService.handle(any())).thenReturn(failed);
        assertFalse(stateService.isLifecycleManaged());
        
        stateService = new McpLifecycleManagementStateService(configQueryChainService);
        when(configQueryChainService.handle(any())).thenThrow(new IllegalStateException("failed"));
        assertFalse(stateService.isLifecycleManaged());
    }
    
    private ConfigQueryChainResponse response(ConfigQueryChainResponse.ConfigQueryStatus status,
        String content) {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(status);
        result.setContent(content);
        return result;
    }
}
