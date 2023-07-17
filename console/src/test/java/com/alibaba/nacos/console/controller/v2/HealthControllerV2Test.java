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

package com.alibaba.nacos.console.controller.v2;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.service.ConfigReadinessCheckService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.core.cluster.health.ModuleHealthCheckerHolder;
import com.alibaba.nacos.naming.cluster.NamingReadinessCheckService;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

/**
 * HealthControllerV2Test.
 *
 * @author DiligenceLai
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthControllerV2Test {
    
    @InjectMocks
    private HealthControllerV2 healthControllerV2;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ServerStatusManager serverStatusManager;
    
    @Before
    public void setUp() {
        // auto register to module health checker holder.
        new NamingReadinessCheckService(serverStatusManager);
        new ConfigReadinessCheckService(configInfoPersistService);
    }
    
    @After
    public void tearDown() throws IllegalAccessException, NoSuchFieldException {
        Field moduleHealthCheckersField = ModuleHealthCheckerHolder.class.getDeclaredField("moduleHealthCheckers");
        moduleHealthCheckersField.setAccessible(true);
        ((List) moduleHealthCheckersField.get(ModuleHealthCheckerHolder.getInstance())).clear();
    }
    
    @Test
    public void testLiveness() throws Exception {
        Result<String> result = healthControllerV2.liveness();
        Assert.assertEquals(0, result.getCode().intValue());
    }
    
    @Test
    public void testReadinessSuccess() throws Exception {
        
        Mockito.when(configInfoPersistService.configInfoCount(any(String.class))).thenReturn(0);
        Mockito.when(serverStatusManager.getServerStatus()).thenReturn(ServerStatus.UP);
        Result<String> result = healthControllerV2.readiness(null);
        Assert.assertEquals(0, result.getCode().intValue());
        Assert.assertEquals("success", result.getMessage());
    }
    
    @Test
    public void testReadinessBothFailure() {
        // Config and Naming are not in readiness
        Mockito.when(configInfoPersistService.configInfoCount(any(String.class)))
                .thenThrow(new RuntimeException("HealthControllerV2Test.testReadiness"));
        Mockito.when(serverStatusManager.getServerStatus())
                .thenThrow(new RuntimeException("HealthControllerV2Test.testReadiness"));
        Result<String> result = healthControllerV2.readiness(null);
        Assert.assertEquals(30000, result.getCode().intValue());
        Assert.assertEquals("naming and config not in readiness", result.getMessage());
    }
    
    @Test
    public void testReadinessConfigFailure() {
        // Config is not in readiness
        Mockito.when(configInfoPersistService.configInfoCount(any(String.class)))
                .thenThrow(new RuntimeException("HealthControllerV2Test.testReadiness"));
        Mockito.when(serverStatusManager.getServerStatus()).thenReturn(ServerStatus.UP);
        Result<String> result = healthControllerV2.readiness(null);
        Assert.assertEquals(30000, result.getCode().intValue());
        Assert.assertEquals("config not in readiness", result.getMessage());
    }
    
    @Test
    public void testReadinessNamingFailure() {
        // Naming is not in readiness
        Mockito.when(configInfoPersistService.configInfoCount(any(String.class))).thenReturn(0);
        Mockito.when(serverStatusManager.getServerStatus())
                .thenThrow(new RuntimeException("HealthControllerV2Test.testReadiness"));
        Result<String> result = healthControllerV2.readiness(null);
        Assert.assertEquals(30000, result.getCode().intValue());
        Assert.assertEquals("naming not in readiness", result.getMessage());
    }
    
}
