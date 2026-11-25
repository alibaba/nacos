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

package com.alibaba.nacos.persistence.datasource;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.utils.DatasourceDialectPluginTypePolicy;
import com.alibaba.nacos.persistence.utils.DatasourcePlatformUtil;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import com.alibaba.nacos.plugin.datasource.manager.DatabaseDialectManager;
import com.alibaba.nacos.plugin.datasource.manager.DatasourceDialectPluginProvider;
import com.alibaba.nacos.plugin.datasource.mapper.Mapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plugin that declares its dialect type and its mapper data source in mixed case keeps working when the selector
 * spells the name the same way, as it did before the datasource platform was lower-cased. The plugin is the pair of
 * classes that {@code src/test/resources/META-INF/services} registers through SPI.
 */
class MixedCasePluginSelectionTest {
    
    @BeforeEach
    void setUp() throws Exception {
        // Register the plugins of the SPI files once more, so that the test does not depend on which test used the
        // process-wide registries before.
        MapperManager.instance(false).loadInitial();
        Method loadDialects = DatabaseDialectManager.class.getDeclaredMethod("loadInitial");
        loadDialects.setAccessible(true);
        loadDialects.invoke(null);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testDialectAndMapperResolveThroughTheConfiguredSelector() {
        for (String selector : new String[] {MixedCaseTestDialect.TYPE,
            " " + MixedCaseTestDialect.TYPE + " "}) {
            MockEnvironment environment = environmentSelecting(selector);
            EnvUtil.setEnvironment(environment);
            String platform =
                DatasourcePlatformUtil.getDatasourcePlatform(PersistenceConstant.DERBY);
            assertEquals(MixedCaseTestDialect.TYPE, platform, selector);
            
            // The external datasource asks the dialect of the selected platform for the driver.
            List<HikariDataSource> pools =
                new ExternalDataSourceProperties().build(environment, platform, pool -> {
                });
            assertEquals(MixedCaseTestDialect.DRIVER, pools.get(0).getDriverClassName(), selector);
            pools.forEach(HikariDataSource::close);
            
            // The persistence services look the mapper up by the platform of the datasource.
            Mapper mapper =
                MapperManager.instance(false).findMapper(platform, TableConstant.CONFIG_INFO);
            assertInstanceOf(MixedCaseTestMapper.class, mapper, selector);
        }
    }
    
    @Test
    void testPluginManagerFindsTheSelectedDialectAmongTheDiscoveredPlugins() {
        PluginTypeConfiguration configuration = configurationSelecting(MixedCaseTestDialect.TYPE);
        DatasourceDialectPluginTypePolicy policy = new DatasourceDialectPluginTypePolicy();
        policy.initialize(configuration);
        
        // The plugin manager discovers the dialects through the provider and requires the selected plugin among
        // them by its exact name, which is the name that the policy returns.
        Map<String, DatabaseDialect> discovered =
            new DatasourceDialectPluginProvider().getAllPlugins();
        Set<String> required = policy.getRequiredPluginNames(configuration);
        assertTrue(discovered.keySet().containsAll(required),
            "required " + required + ", discovered " + discovered.keySet());
        String pluginName = required.iterator().next();
        assertEquals(MixedCaseTestDialect.TYPE, pluginName);
        assertInstanceOf(MixedCaseTestDialect.class, discovered.get(pluginName));
        assertTrue(policy.isPluginEnabledByDefault(pluginName, configuration));
    }
    
    @Test
    void testNamesAreMatchedExactlyAsDeclared() {
        // The lower-case spelling names no registered plugin or mapper: the lookups do not fold the case.
        EnvUtil.setEnvironment(environmentSelecting("acmedb"));
        String platform = DatasourcePlatformUtil.getDatasourcePlatform(PersistenceConstant.DERBY);
        assertEquals("acmedb", platform);
        assertThrows(NacosRuntimeException.class,
            () -> MapperManager.instance(false).findMapper(platform, TableConstant.CONFIG_INFO));
        assertThrows(IllegalStateException.class,
            () -> DatabaseDialectManager.getInstance().getDialect(platform));
    }
    
    private static MockEnvironment environmentSelecting(String selector) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY, selector);
        environment.setProperty("nacos.plugin.datasource.db.num", "1");
        environment.setProperty("nacos.plugin.datasource.db.url.0", "jdbc:h2:mem:mixed_case");
        environment.setProperty("nacos.plugin.datasource.db.user", "nacos");
        environment.setProperty("nacos.plugin.datasource.db.password", "nacos");
        return environment;
    }
    
    private static PluginTypeConfiguration configurationSelecting(String selector) {
        return new PluginTypeConfiguration() {
            
            @Override
            public String getProperty(String key) {
                return PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY.equals(key) ? selector
                    : null;
            }
            
            @Override
            public boolean containsProperty(String key) {
                return PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY.equals(key);
            }
        };
    }
}
