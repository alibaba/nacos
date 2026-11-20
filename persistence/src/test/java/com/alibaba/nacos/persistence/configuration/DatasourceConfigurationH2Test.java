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

package com.alibaba.nacos.persistence.configuration;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceConfigurationH2Test {
    
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY,
            PersistenceConstant.H2);
        EnvUtil.setEnvironment(environment);
        EnvUtil.setIsStandalone(true);
        DatasourceConfiguration.useExternalDb = false;
        DatasourceConfiguration.embeddedStorage = true;
    }
    
    @AfterEach
    void tearDown() {
        DatasourceConfiguration.useExternalDb = false;
        DatasourceConfiguration.embeddedStorage = true;
        EnvUtil.setIsStandalone(true);
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testH2InStandaloneModeUsesEmbeddedStorage() {
        new DatasourceConfiguration().initialize(null);
        
        assertTrue(DatasourceConfiguration.isEmbeddedStorage());
        assertFalse(DatasourceConfiguration.isUseExternalDb());
    }
    
    @Test
    void testH2InClusterModeFailsFastWithStandaloneHint() {
        EnvUtil.setIsStandalone(false);
        DatasourceConfiguration.embeddedStorage = false;
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> new DatasourceConfiguration().initialize(null));
        
        assertTrue(exception.getMessage().contains("standalone"), exception.getMessage());
    }
    
    @Test
    void testCheckEmbeddedPlatformSupported() {
        assertDoesNotThrow(
            () -> DatasourceConfiguration.checkEmbeddedPlatformSupported("h2", true));
        assertDoesNotThrow(
            () -> DatasourceConfiguration.checkEmbeddedPlatformSupported("derby", false));
        assertDoesNotThrow(
            () -> DatasourceConfiguration.checkEmbeddedPlatformSupported("mysql", false));
        assertDoesNotThrow(() -> DatasourceConfiguration.checkEmbeddedPlatformSupported("", false));
        assertThrows(IllegalStateException.class,
            () -> DatasourceConfiguration.checkEmbeddedPlatformSupported("H2", false));
    }
}
