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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerStateRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    ServerStateRemoteHandler remoteHandler;
    
    ModuleState mockModuleState;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() {
        super.setUpWithNaming();
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
        remoteHandler = new ServerStateRemoteHandler(clientHolder);
        mockModuleState = new ModuleState("mock");
        mockModuleState.newState("moduleK", "moduleV");
        Map<String, ModuleState> moduleStates = (Map<String, ModuleState>) ReflectionTestUtils.getField(
                ModuleStateHolder.getInstance(), "moduleStates");
        moduleStates.put("mock", mockModuleState);
    }
    
    @AfterEach
    void tearDown() {
        Map<String, ModuleState> moduleStates = (Map<String, ModuleState>) ReflectionTestUtils.getField(
                ModuleStateHolder.getInstance(), "moduleStates");
        moduleStates.remove("mock");
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void getServerState() throws NacosException {
        Map<String, String> serverState = new HashMap<>();
        serverState.put("testK", "testV");
        when(namingMaintainerService.getServerState()).thenReturn(serverState);
        Map<String, String> actual = remoteHandler.getServerState();
        assertFalse(actual.isEmpty());
        assertTrue(actual.size() >= 2);
        assertTrue(actual.containsKey("testK"));
        assertEquals("testV", actual.get("testK"));
        assertTrue(actual.containsKey("moduleK"));
        assertEquals("moduleV", actual.get("moduleK"));
    }
}