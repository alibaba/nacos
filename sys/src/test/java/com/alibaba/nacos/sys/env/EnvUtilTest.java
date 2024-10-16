/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.env;

import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.plugin.environment.CustomEnvironmentPluginManager;
import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.OperatingSystemMXBean;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.alibaba.nacos.sys.env.Constants.NACOS_SERVER_IP;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvUtilTest {
    
    MockedStatic<OperatingSystemBeanManager> systemBeanManagerMocked;
    
    MockEnvironment environment;
    
    @BeforeEach
    void before() {
        systemBeanManagerMocked = Mockito.mockStatic(OperatingSystemBeanManager.class);
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void after() {
        if (!systemBeanManagerMocked.isClosed()) {
            systemBeanManagerMocked.close();
        }
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testCustomEnvironment() {
        environment.setProperty("nacos.custom.environment.enabled", "true");
        List<CustomEnvironmentPluginService> pluginServices = (List<CustomEnvironmentPluginService>) ReflectionTestUtils.getField(
                CustomEnvironmentPluginManager.getInstance(), "SERVICE_LIST");
        pluginServices.add(new CustomEnvironmentPluginService() {
            @Override
            public Map<String, Object> customValue(Map<String, Object> property) {
                return Collections.emptyMap();
            }
            
            @Override
            public Set<String> propertyKey() {
                return Collections.singleton("nacos.custom.environment.enabled");
            }
            
            @Override
            public Integer order() {
                return 0;
            }
            
            @Override
            public String pluginName() {
                return "";
            }
        });
        MutablePropertySources mock = Mockito.mock(MutablePropertySources.class);
        ReflectionTestUtils.setField(environment, "propertySources", mock);
        EnvUtil.customEnvironment();
        verify(mock).addFirst(any(MapPropertySource.class));
    }
    
    @Test
    void testGetEnvironment() {
        assertEquals(environment, EnvUtil.getEnvironment());
    }
    
    @Test
    void testContainsProperty() {
        assertFalse(EnvUtil.containsProperty("nacos.custom.environment.enabled"));
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertTrue(EnvUtil.containsProperty("nacos.custom.environment.enabled"));
    }
    
    @Test
    void testGetProperty() {
        assertNull(EnvUtil.getProperty("nacos.custom.environment.enabled"));
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertEquals("true", EnvUtil.getProperty("nacos.custom.environment.enabled"));
    }
    
    @Test
    void testGetPropertyWithDefault() {
        assertEquals("false", EnvUtil.getProperty("nacos.custom.environment.enabled", "false"));
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertEquals("true", EnvUtil.getProperty("nacos.custom.environment.enabled"));
    }
    
    @Test
    void testGetPropertyWithType() {
        assertNull(EnvUtil.getProperty("nacos.custom.environment.enabled", Boolean.class));
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertTrue(EnvUtil.getProperty("nacos.custom.environment.enabled", Boolean.class));
    }
    
    @Test
    void testGetRequiredProperty() {
        assertThrows(IllegalStateException.class,
                () -> EnvUtil.getRequiredProperty("nacos.custom.environment.enabled"));
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertEquals("true", EnvUtil.getRequiredProperty("nacos.custom.environment.enabled"));
    }
    
    @Test
    void testGetRequiredPropertyWithType() {
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertTrue(EnvUtil.getRequiredProperty("nacos.custom.environment.enabled", Boolean.class));
    }
    
    @Test
    void testGetProperties() {
        environment.setProperty("nacos.custom.environment.enabled", "true");
        Properties properties = EnvUtil.getProperties();
        assertEquals(1, properties.size());
        assertEquals("true", properties.getProperty("nacos.custom.environment.enabled"));
    }
    
    @Test
    void testResolvePlaceholders() {
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertEquals("true", EnvUtil.resolvePlaceholders("${nacos.custom.environment.enabled}"));
    }
    
    @Test
    void testResolveRequiredPlaceholders() {
        assertThrows(IllegalArgumentException.class,
                () -> EnvUtil.resolveRequiredPlaceholders("${nacos.custom.environment.enabled}"));
        environment.setProperty("nacos.custom.environment.enabled", "true");
        assertEquals("true", EnvUtil.resolvePlaceholders("${nacos.custom.environment.enabled}"));
    }
    
    @Test
    void testGetPropertyList() {
        environment.setProperty("nacos.properties[0]", "value1");
        environment.setProperty("nacos.properties[1]", "value2");
        assertEquals(Arrays.asList("value1", "value2"), EnvUtil.getPropertyList("nacos.properties"));
    }
    
    @Test
    void testGetLocalAddress() {
        System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
        System.setProperty(Constants.AUTO_REFRESH_TIME, "100");
        try {
            assertEquals("1.1.1.1:8848", EnvUtil.getLocalAddress());
            EnvUtil.setLocalAddress("testLocalAddress:8848");
            assertEquals("testLocalAddress:8848", EnvUtil.getLocalAddress());
        } finally {
            System.clearProperty(NACOS_SERVER_IP);
            System.clearProperty(Constants.AUTO_REFRESH_TIME);
        }
    }
    
    @Test
    void testGetPort() {
        assertEquals(8848, EnvUtil.getPort());
        EnvUtil.setPort(3306);
        assertEquals(3306, EnvUtil.getPort());
    }
    
    @Test
    void testGetContextPath() {
        EnvUtil.setContextPath(null);
        assertEquals("/nacos", EnvUtil.getContextPath());
        EnvUtil.setContextPath(null);
        environment.setProperty(Constants.WEB_CONTEXT_PATH, "/");
        assertEquals("", EnvUtil.getContextPath());
        EnvUtil.setContextPath(null);
        environment.setProperty(Constants.WEB_CONTEXT_PATH, "/other");
        assertEquals("/other", EnvUtil.getContextPath());
    }
    
    @Test
    void testGetStandaloneMode() {
        assertFalse(EnvUtil.getStandaloneMode());
        EnvUtil.setIsStandalone(true);
        assertTrue(EnvUtil.getStandaloneMode());
    }
    
    @Test
    void testGetFunctionMode() {
        try {
            assertNull(EnvUtil.getFunctionMode());
            System.setProperty(Constants.FUNCTION_MODE_PROPERTY_NAME, EnvUtil.FUNCTION_MODE_CONFIG);
            assertEquals(EnvUtil.FUNCTION_MODE_CONFIG, EnvUtil.getFunctionMode());
        } finally {
            System.clearProperty(Constants.FUNCTION_MODE_PROPERTY_NAME);
            ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", null);
        }
    }
    
    @Test
    void testGetNacosTmpDir() {
        assertEquals(EnvUtil.getNacosHome() + "/data/tmp", EnvUtil.getNacosTmpDir());
    }
    
    @Test
    void testGetNacosHome() {
        try {
            assertEquals(System.getProperty("user.home") + "/nacos", EnvUtil.getNacosHome());
            EnvUtil.setNacosHomePath(null);
            System.setProperty(EnvUtil.NACOS_HOME_KEY, "/home/admin/nacos");
            assertEquals("/home/admin/nacos", EnvUtil.getNacosHome());
            EnvUtil.setNacosHomePath("/tmp/nacos");
            assertEquals("/tmp/nacos", EnvUtil.getNacosHome());
        } finally {
            System.clearProperty(EnvUtil.NACOS_HOME_KEY);
        }
    }
    
    @Test
    void testGetSystemEnv() {
        assertDoesNotThrow(() -> EnvUtil.getSystemEnv("test"));
    }
    
    @Test
    void testGetLoad() {
        OperatingSystemMXBean operatingSystemMxBean = mock(OperatingSystemMXBean.class);
        systemBeanManagerMocked.when(OperatingSystemBeanManager::getOperatingSystemBean)
                .thenReturn(operatingSystemMxBean);
        when(operatingSystemMxBean.getSystemLoadAverage()).thenReturn(100.0d);
        assertEquals(100d, EnvUtil.getLoad());
    }
    
    @Test
    void testGetCpu() {
        systemBeanManagerMocked.when(OperatingSystemBeanManager::getSystemCpuUsage).thenReturn(50.0d);
        assertEquals(50.0d, EnvUtil.getCpu());
    }
    
    @Test
    public void testGetMem() {
        systemBeanManagerMocked.when(OperatingSystemBeanManager::getFreePhysicalMem).thenReturn(123L);
        systemBeanManagerMocked.when(OperatingSystemBeanManager::getTotalPhysicalMem).thenReturn(2048L);
        assertEquals(EnvUtil.getMem(), 1 - ((double) 123L / (double) 2048L));
        
        systemBeanManagerMocked.when(OperatingSystemBeanManager::getFreePhysicalMem).thenReturn(0L);
        assertEquals(EnvUtil.getMem(), 1 - ((double) 0L / (double) 2048L));
    }
    
    @Test
    void testGetConfPath() {
        try {
            assertEquals(EnvUtil.getNacosHome() + "/conf", EnvUtil.getConfPath());
            EnvUtil.setConfPath("/tmp/nacos/conf");
            assertEquals("/tmp/nacos/conf", EnvUtil.getConfPath());
        } finally {
            EnvUtil.setConfPath(null);
        }
    }
    
    @Test
    void testGetClusterConfFilePath() {
        assertEquals(EnvUtil.getNacosHome() + "/conf/cluster.conf", EnvUtil.getClusterConfFilePath());
    }
    
    @Test
    void testReadClusterConfFromFile() throws URISyntaxException, IOException {
        try {
            File file = new File(EnvUtilTest.class.getClassLoader().getResource("conf/cluster.conf").toURI());
            EnvUtil.setNacosHomePath(file.getParentFile().getParentFile().getAbsolutePath());
            List<String> actual = EnvUtil.readClusterConf();
            assertEquals(3, actual.size());
            assertEquals("127.0.0.1", actual.get(0));
            assertEquals("127.0.0.2", actual.get(1));
            assertEquals("127.0.0.3", actual.get(2));
        } finally {
            EnvUtil.setNacosHomePath(null);
        }
    }
    
    @Test
    void testReadClusterConfFromProperties() throws IOException {
        try {
            EnvUtil.setNacosHomePath("/non/exist/path");
            environment.setProperty("nacos.member.list", "127.0.0.1,127.0.0.2");
            assertEquals(Arrays.asList("127.0.0.1", "127.0.0.2"), EnvUtil.readClusterConf());
        } finally {
            EnvUtil.setNacosHomePath(null);
        }
    }
    
    @Test
    void testReadClusterConfFromSystem() throws IOException {
        try {
            EnvUtil.setNacosHomePath("/non/exist/path");
            EnvUtil.setEnvironment(null);
            System.setProperty("nacos.member.list", "127.0.0.1,127.0.0.2");
            assertEquals(Arrays.asList("127.0.0.1", "127.0.0.2"), EnvUtil.readClusterConf());
        } finally {
            EnvUtil.setNacosHomePath(null);
        }
    }
    
    @Test
    void testWriteClusterConf() throws IOException {
        DiskUtils.forceMkdir(EnvUtil.getNacosHome() + "/conf");
        EnvUtil.writeClusterConf("127.0.0.1");
        File file = new File(EnvUtil.getNacosHome() + "/conf/cluster.conf");
        assertTrue(file.exists());
        assertEquals("127.0.0.1", FileUtils.readFileToString(file, "UTF-8"));
    }
    
    @Test
    void testLoadProperties() throws IOException {
        String path = "test-properties.properties";
        Map<String, ?> actual = EnvUtil.loadProperties(new ClassPathResource(path));
        assertFalse(actual.isEmpty());
    }
    
    @Test
    void testGetApplicationConfFileResourceDefault() throws IOException {
        Resource resource = EnvUtil.getApplicationConfFileResource();
        assertNotNull(resource);
        assertInstanceOf(BufferedInputStream.class, resource.getInputStream());
    }
    
    @Test
    void testGetApplicationConfFileResourceCustom() throws IOException {
        String path = new ClassPathResource("test-properties.properties").getFile().getParentFile().getAbsolutePath();
        environment.setProperty("spring.config.additional-location",
                "file:test-properties-malformed-unicode.properties,file:" + path);
        Resource resource = EnvUtil.getApplicationConfFileResource();
        assertNotNull(resource);
        assertInstanceOf(FileInputStream.class, resource.getInputStream());
    }
    
    @Test
    void testGetApplicationConfFileResourceCustomButFileNotExist() throws IOException {
        environment.setProperty("spring.config.additional-location",
                "file:test-properties-malformed-unicode.properties,file:test-properties.properties");
        Resource resource = EnvUtil.getApplicationConfFileResource();
        assertNotNull(resource);
        assertInstanceOf(BufferedInputStream.class, resource.getInputStream());
    }
    
    @Test
    void testGetAvailableProcessorsDefaultMultiple() {
        assertEquals(ThreadUtils.getSuitableThreadCount(1), EnvUtil.getAvailableProcessors());
        environment.setProperty(Constants.AVAILABLE_PROCESSORS_BASIC, "0");
        assertEquals(1, EnvUtil.getAvailableProcessors());
        environment.setProperty(Constants.AVAILABLE_PROCESSORS_BASIC, "2");
        assertEquals(2, EnvUtil.getAvailableProcessors());
    }
    
    @Test
    void testGetAvailableProcessorsWithMultiple() {
        assertThrows(IllegalArgumentException.class, () -> EnvUtil.getAvailableProcessors(0));
        assertEquals(ThreadUtils.getSuitableThreadCount(2), EnvUtil.getAvailableProcessors(2));
        environment.setProperty(Constants.AVAILABLE_PROCESSORS_BASIC, "0");
        assertEquals(ThreadUtils.getSuitableThreadCount(2), EnvUtil.getAvailableProcessors(2));
        environment.setProperty(Constants.AVAILABLE_PROCESSORS_BASIC, "2");
        assertEquals(4, EnvUtil.getAvailableProcessors(2));
    }
    
    @Test
    void testGetAvailableProcessorsWithScale() {
        assertThrows(IllegalArgumentException.class, () -> EnvUtil.getAvailableProcessors(-1.0d));
        assertThrows(IllegalArgumentException.class, () -> EnvUtil.getAvailableProcessors(1.1d));
        int defaultValue = (int) (ThreadUtils.getSuitableThreadCount(1) * 0.5);
        defaultValue = Math.max(defaultValue, 1);
        assertEquals(defaultValue, EnvUtil.getAvailableProcessors(0.5d));
        environment.setProperty(Constants.AVAILABLE_PROCESSORS_BASIC, "0");
        assertEquals(1, EnvUtil.getAvailableProcessors(0.5d));
        environment.setProperty(Constants.AVAILABLE_PROCESSORS_BASIC, "4");
        assertEquals(2, EnvUtil.getAvailableProcessors(0.5d));
    }
}
