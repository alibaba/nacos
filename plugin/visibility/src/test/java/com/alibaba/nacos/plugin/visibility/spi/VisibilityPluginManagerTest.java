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

package com.alibaba.nacos.plugin.visibility.spi;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class VisibilityPluginManagerTest {
    
    private static final String TEST_SERVICE_NAME = "test-visibility";
    
    private static final String VISIBILITY_ENABLED_KEY = "nacos.plugin.visibility.enabled";
    
    private VisibilityPluginManager manager;
    
    @Mock
    private VisibilityService mockVisibilityService;
    
    private Map<String, VisibilityService> serviceMap;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        EnvUtil.reset();
        System.clearProperty(VISIBILITY_ENABLED_KEY);
        PluginStateCheckerHolder.setInstance(null);
        manager = VisibilityPluginManager.getInstance();
        Field field = VisibilityPluginManager.class.getDeclaredField("visibilityServiceMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, VisibilityService> map = (Map<String, VisibilityService>) field.get(manager);
        serviceMap = map;
        serviceMap.clear();
        serviceMap.put(TEST_SERVICE_NAME, mockVisibilityService);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.reset();
        System.clearProperty(VISIBILITY_ENABLED_KEY);
        PluginStateCheckerHolder.setInstance(null);
        serviceMap.clear();
    }
    
    @Test
    void testGetInstance() {
        assertNotNull(VisibilityPluginManager.getInstance());
    }
    
    @Test
    void testFindVisibilityServiceExists() {
        Optional<VisibilityService> result = manager.findVisibilityService(TEST_SERVICE_NAME);
        assertTrue(result.isPresent());
        assertEquals(mockVisibilityService, result.get());
    }
    
    @Test
    void testFindVisibilityServiceWhenVisibilityPluginDisabled() {
        System.setProperty(VISIBILITY_ENABLED_KEY, "false");
        Optional<VisibilityService> result = manager.findVisibilityService(TEST_SERVICE_NAME);
        assertFalse(result.isPresent());
    }
    
    @Test
    void testFindVisibilityServiceWhenNamedPluginDisabled() {
        PluginStateCheckerHolder.setInstance((pluginType, pluginName) -> false);
        
        Optional<VisibilityService> result = manager.findVisibilityService(TEST_SERVICE_NAME);
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetAllPluginsIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () -> manager.getAllPlugins().clear());
    }
    
    @Test
    void testRegisterVisibilityServiceBranches() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("nacos.plugin.visibility.custom.timeout", "1000");
        properties.setProperty("nacos.plugin.visibility.other.timeout", "2000");
        TestVisibilityService service = new TestVisibilityService("custom");
        
        registerVisibilityService(service, properties);
        registerVisibilityService(new TestVisibilityService(""), properties);
        registerVisibilityService(new ThrowNameVisibilityService(), properties);
        registerVisibilityService(new ThrowInitVisibilityService("throw-init"), properties);
        
        assertEquals(service, serviceMap.get("custom"));
        assertEquals("1000", service.initProperties.getProperty("timeout"));
        assertFalse(service.initProperties.containsKey("nacos.plugin.visibility.custom.timeout"));
        assertFalse(serviceMap.containsKey(""));
        assertFalse(serviceMap.containsKey("throw-init"));
    }
    
    @Test
    void testInitAndPropertyResolutionBranches() throws Exception {
        Field initialized = VisibilityPluginManager.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        initialized.set(manager, true);
        
        Method initMethod =
            VisibilityPluginManager.class.getDeclaredMethod("initVisibilityServices");
        initMethod.setAccessible(true);
        initMethod.invoke(manager);
        
        Method propertiesMethod = VisibilityPluginManager.class.getDeclaredMethod(
            "resolveServiceProperties", Properties.class, String.class);
        propertiesMethod.setAccessible(true);
        Properties result = (Properties) propertiesMethod.invoke(manager, new Properties(),
            "custom");
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testResolveInitPropertiesFallsBackWhenEnvUtilThrows() throws Exception {
        System.setProperty("nacos.plugin.visibility.fallback.timeout", "5000");
        EnvUtil.setThrowException(true);
        
        Method propertiesMethod =
            VisibilityPluginManager.class.getDeclaredMethod("resolveInitProperties");
        propertiesMethod.setAccessible(true);
        
        Properties result = (Properties) propertiesMethod.invoke(manager);
        
        assertEquals("5000", result.getProperty("nacos.plugin.visibility.fallback.timeout"));
    }
    
    @Test
    void testInitVisibilityServicesLoadsSpiAndSkipsBrokenProvider() throws Exception {
        System.setProperty("nacos.plugin.visibility.spi-loaded.timeout", "3000");
        Field initialized = VisibilityPluginManager.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        initialized.set(manager, false);
        serviceMap.clear();
        
        Method initMethod =
            VisibilityPluginManager.class.getDeclaredMethod("initVisibilityServices");
        initMethod.setAccessible(true);
        initMethod.invoke(manager);
        
        assertTrue((Boolean) initialized.get(manager));
        assertTrue(serviceMap.containsKey("spi-loaded"));
        assertEquals("3000", SpiLoadedVisibilityService.initProperties.getProperty("timeout"));
    }
    
    private void registerVisibilityService(VisibilityService service, Properties properties)
        throws Exception {
        Method method = VisibilityPluginManager.class.getDeclaredMethod("registerVisibilityService",
            VisibilityService.class, Properties.class);
        method.setAccessible(true);
        method.invoke(manager, service, properties);
    }
    
    private static class TestVisibilityService implements VisibilityService {
        
        private final String serviceName;
        
        private Properties initProperties;
        
        private TestVisibilityService(String serviceName) {
            this.serviceName = serviceName;
        }
        
        @Override
        public void init(Properties properties) {
            initProperties = properties;
        }
        
        @Override
        public ValidationResult validateVisibility(String identity, String action, String apiType,
            VisibilityResource resource) {
            return null;
        }
        
        @Override
        public QueryAdvisor adviseQuery(String identity, String action, String apiType,
            VisibilityQueryContext context) {
            return null;
        }
        
        @Override
        public String getVisibilityServiceName() {
            return serviceName;
        }
    }
    
    private static class ThrowNameVisibilityService extends TestVisibilityService {
        
        private ThrowNameVisibilityService() {
            super("throw-name");
        }
        
        @Override
        public String getVisibilityServiceName() {
            throw new IllegalStateException("name failed");
        }
    }
    
    private static class ThrowInitVisibilityService extends TestVisibilityService {
        
        private ThrowInitVisibilityService(String serviceName) {
            super(serviceName);
        }
        
        @Override
        public void init(Properties properties) {
            throw new IllegalStateException("init failed");
        }
    }
}
