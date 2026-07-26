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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.core.plugin.config.PluginConfigApplyException;
import com.alibaba.nacos.core.plugin.storage.PluginPersistenceException;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StandalonePluginStateSynchronizerTest {
    
    @Mock
    private PluginStatePersistenceService persistence;
    
    @Mock
    private PluginStateApplier applier;
    
    private StandalonePluginStateSynchronizer synchronizer;
    
    @BeforeEach
    void setUp() {
        synchronizer = new StandalonePluginStateSynchronizer(persistence, applier);
    }
    
    @Test
    void syncStateChangeSuccess() throws NacosApiException {
        synchronizer.syncStateChange("auth:nacos", true);
        
        InOrder inOrder = inOrder(applier, persistence);
        inOrder.verify(applier).validateStateChange("auth:nacos", true);
        inOrder.verify(persistence).saveState(eq("auth:nacos"), eq(true));
        inOrder.verify(applier).applyStateChange("auth:nacos", true);
    }
    
    @Test
    void syncStateChangePersistenceThrows() {
        doThrow(new PluginPersistenceException("save failed")).when(persistence).saveState(any(),
            anyBoolean());
        
        assertThrows(NacosApiException.class,
            () -> synchronizer.syncStateChange("auth:nacos", false));
        verify(applier).validateStateChange("auth:nacos", false);
        verify(applier, never()).applyStateChange(any(), anyBoolean());
    }
    
    @Test
    void syncStateChangeInvalidParameter() {
        doThrow(new IllegalArgumentException("invalid state")).when(applier)
            .validateStateChange("auth:nacos", false);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> synchronizer.syncStateChange("auth:nacos", false));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        verify(persistence, never()).saveState(any(), anyBoolean());
        verify(applier, never()).applyStateChange(any(), anyBoolean());
    }
    
    @Test
    void syncConfigChangeSuccess() throws NacosApiException {
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        synchronizer.syncConfigChange("trace:otel", config);
        
        verify(applier).applyConfigChange("trace:otel", config);
        verify(persistence, never()).saveConfig(eq("trace:otel"), eq(config));
    }
    
    @Test
    void syncConfigChangePersistenceThrows() {
        doThrow(new PluginPersistenceException("save config failed")).when(applier)
            .applyConfigChange(any(), anyMap());
        
        assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("trace:otel", Collections.singletonMap("k", "v")));
    }
    
    @Test
    void syncConfigChangeInvalidParameter() {
        doThrow(new IllegalArgumentException("invalid config")).when(applier)
            .applyConfigChange(any(), anyMap());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("trace:otel", Collections.emptyMap()));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    void syncConfigChangeReportsAcceptedConfigApplyFailure() {
        doThrow(new PluginConfigApplyException("config updated but apply failed",
            new IllegalStateException("apply failed"))).when(applier)
            .applyConfigChange(any(), anyMap());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("trace:otel", Collections.emptyMap()));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("config updated but apply failed", exception.getErrMsg());
    }
}
