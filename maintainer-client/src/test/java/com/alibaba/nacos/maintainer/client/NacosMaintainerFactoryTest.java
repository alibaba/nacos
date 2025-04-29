/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerFactory;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class NacosMaintainerFactoryTest {
    
    private ConfigMaintainerService mockConfigMaintainerService;
    
    @BeforeEach
    void setUp() {
        mockConfigMaintainerService = Mockito.mock(ConfigMaintainerService.class);
    }
    
    @Test
    void testCreateConfigMaintainerServiceWithServerList() throws NacosException {
        String serverList = "127.0.0.1:8848";
        
        try (MockedStatic<ConfigMaintainerFactory> mockedFactory = mockStatic(ConfigMaintainerFactory.class)) {
            mockedFactory.when(() -> ConfigMaintainerFactory.createConfigMaintainerService(serverList))
                    .thenReturn(mockConfigMaintainerService);
            
            ConfigMaintainerService result = NacosMaintainerFactory.createConfigMaintainerService(serverList);
            
            assertNotNull(result);
            
            mockedFactory.verify(() -> ConfigMaintainerFactory.createConfigMaintainerService(serverList), times(1));
        }
    }
    
    @Test
    void testCreateConfigMaintainerServiceWithProperties() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "127.0.0.1:8848");
        
        try (MockedStatic<ConfigMaintainerFactory> mockedFactory = mockStatic(ConfigMaintainerFactory.class)) {
            mockedFactory.when(() -> ConfigMaintainerFactory.createConfigMaintainerService(properties))
                    .thenReturn(mockConfigMaintainerService);
            
            ConfigMaintainerService result = NacosMaintainerFactory.createConfigMaintainerService(properties);
            
            assertNotNull(result);
            
            mockedFactory.verify(() -> ConfigMaintainerFactory.createConfigMaintainerService(properties), times(1));
        }
    }
}
