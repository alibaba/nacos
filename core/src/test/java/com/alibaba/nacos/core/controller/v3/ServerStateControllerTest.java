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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.cluster.health.ModuleHealthCheckerHolder;
import com.alibaba.nacos.core.cluster.health.ReadinessResult;
import com.alibaba.nacos.core.service.NacosServerStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * {@link ServerStateController} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class ServerStateControllerTest {
    
    @Mock
    private NacosServerStateService stateService;
    
    private MockedStatic<ModuleHealthCheckerHolder> holderMockedStatic;
    
    @Mock
    private ModuleHealthCheckerHolder holder;
    
    private ServerStateController serverStateController;
    
    @BeforeEach
    void setUp() {
        serverStateController = new ServerStateController(stateService);
        holderMockedStatic = Mockito.mockStatic(ModuleHealthCheckerHolder.class);
        holderMockedStatic.when(ModuleHealthCheckerHolder::getInstance).thenReturn(holder);
    }
    
    @AfterEach
    void tearDown() {
        if (holderMockedStatic != null) {
            holderMockedStatic.close();
        }
    }
    
    @Test
    void testServerState() {
        Map<String, String> state = Collections.singletonMap("key", "value");
        when(stateService.getServerState()).thenReturn(state);
        
        Result<Map<String, String>> result = serverStateController.serverState();
        
        assertNotNull(result);
        assertEquals(0, result.getCode().intValue());
        assertEquals(state, result.getData());
    }
    
    @Test
    void testLiveness() {
        Result<String> result = serverStateController.liveness();
        
        assertNotNull(result);
        assertEquals(0, result.getCode().intValue());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testReadinessSuccess() throws Exception {
        when(holder.checkReadiness()).thenReturn(new ReadinessResult(true, "OK"));
        
        Result<String> result = serverStateController.readiness();
        
        assertNotNull(result);
        assertEquals(0, result.getCode().intValue());
        assertEquals("ok", result.getData());
    }
    
    @Test
    void testReadinessFailure() throws Exception {
        when(holder.checkReadiness())
            .thenReturn(new ReadinessResult(false, "module1 not in readiness"));
        
        Result<String> result = serverStateController.readiness();
        
        assertNotNull(result);
        assertEquals(30000, result.getCode().intValue());
        assertEquals("module1 not in readiness", result.getMessage());
    }
}
