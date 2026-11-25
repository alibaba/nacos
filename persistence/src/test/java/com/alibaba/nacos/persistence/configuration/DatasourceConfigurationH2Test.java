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
import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URL;
import java.net.URLClassLoader;

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
        EnvUtil.setDeploymentType(null);
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
            () -> DatasourceConfiguration.checkEmbeddedPlatformSupported("h2", false));
    }
    
    @Test
    void testCheckEmbeddedPlatformNameAcceptsOnlyTheLowerCaseH2() {
        assertDoesNotThrow(() -> DatasourceConfiguration.checkEmbeddedPlatformName("h2"));
        assertDoesNotThrow(() -> DatasourceConfiguration.checkEmbeddedPlatformName("derby"));
        assertDoesNotThrow(() -> DatasourceConfiguration.checkEmbeddedPlatformName("mysql"));
        assertDoesNotThrow(() -> DatasourceConfiguration.checkEmbeddedPlatformName(""));
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> DatasourceConfiguration.checkEmbeddedPlatformName("H2"));
        assertTrue(error.getMessage().contains("'h2'"), error.getMessage());
        assertTrue(error.getMessage().contains("case-sensitive"), error.getMessage());
    }
    
    @Test
    void testServerProcessRejectsUppercaseH2AtStartup() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY, "H2");
        EnvUtil.setEnvironment(environment);
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new DatasourceConfiguration().loadDatasourceConfiguration(
                DatasourceConfiguration.class.getClassLoader()));
        assertTrue(error.getMessage().contains("case-sensitive"), error.getMessage());
    }
    
    @Test
    void testCheckEmbeddedDriverAvailableOnlyForH2() throws Exception {
        try (URLClassLoader withoutDriver = new URLClassLoader(new URL[0], null)) {
            assertDoesNotThrow(
                () -> DatasourceConfiguration.checkEmbeddedDriverAvailable(null, withoutDriver));
            assertDoesNotThrow(
                () -> DatasourceConfiguration.checkEmbeddedDriverAvailable("derby", withoutDriver));
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatasourceConfiguration.checkEmbeddedDriverAvailable("h2", withoutDriver));
            assertTrue(error.getMessage().contains("plugins directory"), error.getMessage());
        }
    }
    
    @Test
    void testConsoleOnlyProcessLoadsH2ConfigurationWithoutTheDriver() throws Exception {
        EnvUtil.setDeploymentType(DeploymentType.CONSOLE);
        try (URLClassLoader withoutDriver = new URLClassLoader(new URL[0], null)) {
            for (boolean standalone : new boolean[] {false, true}) {
                EnvUtil.setIsStandalone(standalone);
                DatasourceConfiguration.embeddedStorage = standalone;
                DatasourceConfiguration.useExternalDb = false;
                assertDoesNotThrow(
                    () -> new DatasourceConfiguration().loadDatasourceConfiguration(withoutDriver));
            }
            
            // A cluster console reads configuration through the remote server and keeps no embedded storage.
            EnvUtil.setIsStandalone(false);
            DatasourceConfiguration.embeddedStorage = false;
            DatasourceConfiguration.useExternalDb = false;
            new DatasourceConfiguration().loadDatasourceConfiguration(withoutDriver);
            assertTrue(DatasourceConfiguration.isUseExternalDb());
        }
    }
    
    @Test
    void testServerProcessFailsFastForH2WithoutStandaloneModeOrDriver() throws Exception {
        try (URLClassLoader withoutDriver = new URLClassLoader(new URL[0], null)) {
            for (DeploymentType type : new DeploymentType[] {null, DeploymentType.MERGED,
                DeploymentType.SERVER}) {
                EnvUtil.setDeploymentType(type);
                EnvUtil.setIsStandalone(false);
                IllegalStateException clusterError = assertThrows(IllegalStateException.class,
                    () -> new DatasourceConfiguration().loadDatasourceConfiguration(withoutDriver));
                assertTrue(clusterError.getMessage().contains("standalone mode only"),
                    clusterError.getMessage());
                
                EnvUtil.setIsStandalone(true);
                IllegalStateException driverError = assertThrows(IllegalStateException.class,
                    () -> new DatasourceConfiguration().loadDatasourceConfiguration(withoutDriver));
                assertTrue(driverError.getMessage().contains("plugins directory"),
                    driverError.getMessage());
            }
        }
    }
    
    @Test
    void testOnlyConsoleDeploymentSkipsServerPersistenceChecks() {
        EnvUtil.setDeploymentType(null);
        assertTrue(DatasourceConfiguration.hostsServerPersistence());
        EnvUtil.setDeploymentType(DeploymentType.MERGED);
        assertTrue(DatasourceConfiguration.hostsServerPersistence());
        EnvUtil.setDeploymentType(DeploymentType.SERVER);
        assertTrue(DatasourceConfiguration.hostsServerPersistence());
        EnvUtil.setDeploymentType(DeploymentType.CONSOLE);
        assertFalse(DatasourceConfiguration.hostsServerPersistence());
    }
    
    @Test
    void testCheckEmbeddedDriverAvailablePassesWhenDriverIsPresent() {
        assertDoesNotThrow(() -> DatasourceConfiguration.checkEmbeddedDriverAvailable("h2",
            DatasourceConfiguration.class.getClassLoader()));
    }
}
