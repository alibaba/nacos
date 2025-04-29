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

package com.alibaba.nacos.console;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosConsoleStartUpTest {
    
    private static String mockNacosHome;
    
    NacosConsoleStartUp nacosConsoleStartUp;
    
    MockEnvironment environment;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeAll
    static void initMockNacosHome() throws Exception {
        String classResource = NacosConsoleStartUp.class.getClassLoader().getResource("nacos-console.properties").getPath();
        File file = new File(classResource).getParentFile();
        mockNacosHome = file.getAbsolutePath() + File.separator + "NacosConsoleStartUpTest";
    }
    
    @BeforeEach
    void setUp() {
        EnvUtil.setNacosHomePath(null);
        environment = new MockEnvironment();
        System.setProperty(EnvUtil.NACOS_HOME_KEY, mockNacosHome);
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(environment);
        nacosConsoleStartUp = new NacosConsoleStartUp();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setNacosHomePath(null);
        EnvUtil.setEnvironment(cachedEnvironment);
        System.clearProperty(EnvUtil.NACOS_HOME_KEY);
        System.clearProperty(Constants.NACOS_DEPLOYMENT_TYPE);
        System.clearProperty("nacos.local.ip");
        System.clearProperty("nacos.mode");
        System.clearProperty("nacos.function.mode");
    }
    
    @AfterAll
    static void cleanMockNacosHome() throws Exception {
        DiskUtils.deleteDirectory(mockNacosHome);
    }
    
    @Test
    void makeWorkDir() {
        System.setProperty(Constants.NACOS_DEPLOYMENT_TYPE, Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE);
        File file = new File(mockNacosHome, "logs");
        assertFalse(file.exists());
        String[] actual = nacosConsoleStartUp.makeWorkDir();
        assertTrue(file.exists());
        assertEquals(1, actual.length);
        assertEquals(file.getAbsolutePath(), actual[0]);
    }
    
    @Test
    void makeWorkDirNotConsoleDeploymentType() {
        String[] actual = nacosConsoleStartUp.makeWorkDir();
        assertEquals(0, actual.length);
    }
    
    @Test
    void markWorkDirWithException() {
        System.setProperty(Constants.NACOS_DEPLOYMENT_TYPE, Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE);
        System.setProperty(EnvUtil.NACOS_HOME_KEY, "invalid\0path");
        assertThrows(Exception.class, () -> nacosConsoleStartUp.makeWorkDir());
    }
    
    @Test
    void injectEnvironment() {
        MockEnvironment newMockEnvironment = new MockEnvironment();
        nacosConsoleStartUp.injectEnvironment(newMockEnvironment);
        assertNotEquals(newMockEnvironment, EnvUtil.getEnvironment());
        changeIsConsoleDeploymentType();
        nacosConsoleStartUp.injectEnvironment(newMockEnvironment);
        assertEquals(newMockEnvironment, EnvUtil.getEnvironment());
    }
    
    @Test
    void loadPreProperties() {
        String testPath = NacosConsoleStartUpTest.class.getClassLoader().getResource("nacos-console.properties").getPath();
        File file = new File(testPath).getParentFile();
        testPath = new File(file.getAbsolutePath(), "mock").getAbsolutePath();
        int oldSize = environment.getPropertySources().size();
        environment.setProperty("spring.config.additional-location", "file:" + testPath);
        nacosConsoleStartUp.loadPreProperties(environment);
        assertEquals(oldSize, environment.getPropertySources().size());
        changeIsConsoleDeploymentType();
        nacosConsoleStartUp.loadPreProperties(environment);
        assertEquals(oldSize + 1, environment.getPropertySources().size());
    }
    
    @Test
    void loadPrePropertiesWithException() {
        environment.setProperty("spring.config.additional-location", "file:" + mockNacosHome + File.separator + "nacos-console.properties");
        changeIsConsoleDeploymentType();
        assertThrows(NacosRuntimeException.class, () -> nacosConsoleStartUp.loadPreProperties(environment));
    }
    
    @Test
    void initSystemProperty() {
        nacosConsoleStartUp.initSystemProperty();
        assertFalse(System.getProperties().containsKey("nacos.local.ip"));
        assertFalse(System.getProperties().containsKey("nacos.mode"));
        assertFalse(System.getProperties().containsKey("nacos.function.mode"));
        changeIsConsoleDeploymentType();
        nacosConsoleStartUp.initSystemProperty();
        assertTrue(System.getProperties().containsKey("nacos.local.ip"));
        assertTrue(System.getProperties().containsKey("nacos.mode"));
        assertTrue(System.getProperties().containsKey("nacos.function.mode"));
        assertEquals(NetUtils.localIp(), System.getProperty("nacos.local.ip"));
        assertEquals("stand alone", System.getProperty("nacos.mode"));
        assertEquals("All", System.getProperty("nacos.function.mode"));
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", EnvUtil.FUNCTION_MODE_CONFIG);
        nacosConsoleStartUp.initSystemProperty();
        assertEquals(EnvUtil.FUNCTION_MODE_CONFIG, System.getProperty("nacos.function.mode"));
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", EnvUtil.FUNCTION_MODE_NAMING);
        nacosConsoleStartUp.initSystemProperty();
        assertEquals(EnvUtil.FUNCTION_MODE_NAMING, System.getProperty("nacos.function.mode"));
    }
    
    private void changeIsConsoleDeploymentType() {
        ReflectionTestUtils.setField(nacosConsoleStartUp, "isConsoleDeploymentType", true);
    }
}