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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigReadinessCheckServiceTest {
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Test
    void testReadinessSuccess() {
        when(configInfoPersistService.configInfoCount("")).thenReturn(10);
        ConfigReadinessCheckService service =
            new ConfigReadinessCheckService(configInfoPersistService);
        assertTrue(service.readiness());
    }
    
    @Test
    void testReadinessFailure() {
        when(configInfoPersistService.configInfoCount(""))
            .thenThrow(new RuntimeException("db error"));
        ConfigReadinessCheckService service =
            new ConfigReadinessCheckService(configInfoPersistService);
        assertFalse(service.readiness());
    }
    
    @Test
    void testGetModuleName() {
        ConfigReadinessCheckService service =
            new ConfigReadinessCheckService(configInfoPersistService);
        assertEquals("config", service.getModuleName());
    }
}
