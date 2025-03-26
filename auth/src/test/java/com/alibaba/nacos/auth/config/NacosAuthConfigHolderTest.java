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

package com.alibaba.nacos.auth.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosAuthConfigHolderTest {
    
    @Mock
    NacosAuthConfig nacosAuthConfig;
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    @BeforeEach
    void setUp() {
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        Map<String, NacosAuthConfig> mockMap = Map.of("test", nacosAuthConfig);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap", mockMap);
    }
    
    @AfterEach
    void tearDown() {
        if (cachedConfigMap != null) {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap", cachedConfigMap);
        }
    }
    
    @Test
    void getNacosAuthConfigByScope() {
        assertEquals(nacosAuthConfig, NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope("test"));
        assertNull(NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope("test1"));
    }
    
    @Test
    void getAllNacosAuthConfig() {
        assertEquals(1, NacosAuthConfigHolder.getInstance().getAllNacosAuthConfig().size());
        assertTrue(NacosAuthConfigHolder.getInstance().getAllNacosAuthConfig().contains(nacosAuthConfig));
    }
    
    @Test
    void isAnyAuthEnabled() {
        assertFalse(NacosAuthConfigHolder.getInstance().isAnyAuthEnabled());
        when(nacosAuthConfig.isAuthEnabled()).thenReturn(true);
        assertTrue(NacosAuthConfigHolder.getInstance().isAnyAuthEnabled());
    }
}