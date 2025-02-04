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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NamingMaintainerFactoryTest {
    
    @Test
    public void testCreateNamingMaintainerServiceWithServerList() throws NacosException {
        // Arrange
        String serverList = "localhost:8848";
        
        // Act
        NamingMaintainerService service = NamingMaintainerFactory.createNamingMaintainerService(serverList);
        
        // Assert
        assertNotNull(service);
        assertInstanceOf(NacosNamingMaintainerService.class, service);
    }
    
    @Test
    public void testCreateNamingMaintainerServiceWithProperties() throws NacosException {
        // Arrange
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        
        // Act
        NamingMaintainerService service = NamingMaintainerFactory.createNamingMaintainerService(properties);
        
        // Assert
        assertNotNull(service);
        assertInstanceOf(NacosNamingMaintainerService.class, service);
    }
    
    @Test
    public void testCreateNamingMaintainerServiceWithEmptyServerList() {
        // Arrange
        String serverList = "";
        
        // Act & Assert
        assertThrows(NacosException.class, () -> {
            NamingMaintainerFactory.createNamingMaintainerService(serverList);
        });
    }
    
    @Test
    public void testCreateNamingMaintainerServiceWithNullProperties() {
        // Arrange
        Properties properties = null;
        
        // Act & Assert
        assertThrows(NacosException.class, () -> {
            NamingMaintainerFactory.createNamingMaintainerService(properties);
        });
    }
}