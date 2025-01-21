/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.configuration;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DatasourceConfigurationTest {
    
    @Mock
    ConfigurableApplicationContext context;
    
    DatasourceConfiguration datasourceConfiguration;
    
    MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        datasourceConfiguration = new DatasourceConfiguration();
        DatasourceConfiguration.useExternalDB = false;
        DatasourceConfiguration.embeddedStorage = true;
    }
    
    @AfterEach
    void tearDown() {
        DatasourceConfiguration.useExternalDB = false;
        DatasourceConfiguration.embeddedStorage = true;
        EnvUtil.setIsStandalone(true);
        EnvUtil.setEnvironment(null);
        System.clearProperty(PersistenceConstant.EMBEDDED_STORAGE);
    }
    
    @Test
    void testInitializeForEmptyDatasourceForStandaloneMode() {
        datasourceConfiguration.initialize(context);
        assertTrue(DatasourceConfiguration.isEmbeddedStorage());
        assertFalse(DatasourceConfiguration.isUseExternalDB());
    }
    
    @Test
    void testInitializeForEmptyDatasourceForClusterMode() {
        EnvUtil.setIsStandalone(false);
        DatasourceConfiguration.embeddedStorage = false;
        datasourceConfiguration.initialize(context);
        assertFalse(DatasourceConfiguration.isEmbeddedStorage());
        assertTrue(DatasourceConfiguration.isUseExternalDB());
    }
    
    @Test
    void testInitializeForDerbyForStandaloneMode() {
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, PersistenceConstant.DERBY);
        System.setProperty(PersistenceConstant.EMBEDDED_STORAGE, "true");
        datasourceConfiguration.initialize(context);
        assertTrue(DatasourceConfiguration.isEmbeddedStorage());
        assertFalse(DatasourceConfiguration.isUseExternalDB());
    }
    
    @Test
    void testInitializeForDerbyForClusterMode() {
        EnvUtil.setIsStandalone(false);
        DatasourceConfiguration.embeddedStorage = false;
        System.setProperty(PersistenceConstant.EMBEDDED_STORAGE, "true");
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, PersistenceConstant.DERBY);
        datasourceConfiguration.initialize(context);
        assertTrue(DatasourceConfiguration.isEmbeddedStorage());
        assertFalse(DatasourceConfiguration.isUseExternalDB());
    }
    
    @Test
    void testInitializeForMySqlForStandaloneMode() {
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, PersistenceConstant.MYSQL);
        datasourceConfiguration.initialize(context);
        assertFalse(DatasourceConfiguration.isEmbeddedStorage());
        assertTrue(DatasourceConfiguration.isUseExternalDB());
        
    }
    
    @Test
    void testInitializeForMySqlForClusterMode() {
        EnvUtil.setIsStandalone(false);
        DatasourceConfiguration.embeddedStorage = false;
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, PersistenceConstant.MYSQL);
        datasourceConfiguration.initialize(context);
        assertFalse(DatasourceConfiguration.isEmbeddedStorage());
        assertTrue(DatasourceConfiguration.isUseExternalDB());
    }
    
    @Test
    void testInitializeForPgSqlForStandaloneMode() {
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, "postgresql");
        datasourceConfiguration.initialize(context);
        assertFalse(DatasourceConfiguration.isEmbeddedStorage());
        assertTrue(DatasourceConfiguration.isUseExternalDB());
    }
    
    @Test
    void testInitializeForPgSqlForClusterMode() {
        EnvUtil.setIsStandalone(false);
        DatasourceConfiguration.embeddedStorage = false;
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, "postgresql");
        datasourceConfiguration.initialize(context);
        assertFalse(DatasourceConfiguration.isEmbeddedStorage());
        assertTrue(DatasourceConfiguration.isUseExternalDB());
    }
}