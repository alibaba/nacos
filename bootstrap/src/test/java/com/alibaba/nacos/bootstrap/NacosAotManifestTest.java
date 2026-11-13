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

package com.alibaba.nacos.bootstrap;

import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosAotManifestTest {
    
    @TempDir
    Path temporary;
    
    private MockedStatic<EnvUtil> global;
    
    private StandardEnvironment environment;
    
    private Map<String, Object> properties;
    
    private URLClassLoader loader;
    
    @BeforeEach
    void setUp() {
        global = mockStatic(EnvUtil.class);
        global.when(EnvUtil::getDeploymentType).thenReturn(DeploymentType.SERVER);
        global.when(EnvUtil::getStandaloneMode).thenReturn(true);
        environment = new StandardEnvironment();
        properties = new HashMap<>();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        loader = new URLClassLoader(new URL[0], null);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        global.close();
        loader.close();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"derby", "mysql", "postgresql", "oracle", "custom-database"})
    void shouldAcceptMatchingDatasourceWithoutWhitelist(String datasource) {
        properties.put("nacos.plugin.datasource-dialect.type", datasource);
        Properties manifest = NacosAotManifest.snapshot(environment, loader);
        assertEquals(datasource, manifest.getProperty("datasource"));
        assertDoesNotThrow(() -> NacosAotManifest.validate(manifest, environment, loader));
    }
    
    @Test
    void shouldUseStandardSelectorBeforeLegacyAlias() {
        properties.put("spring.sql.init.platform", "mysql");
        assertEquals("mysql",
            NacosAotManifest.snapshot(environment, loader).getProperty("datasource"));
        properties.put("nacos.plugin.datasource-dialect.type", "postgresql");
        assertEquals("postgresql",
            NacosAotManifest.snapshot(environment, loader).getProperty("datasource"));
    }
    
    @Test
    void shouldUseEmbeddedDefaultInStandaloneMode() {
        assertEquals("derby",
            NacosAotManifest.snapshot(environment, loader).getProperty("datasource"));
    }
    
    @Test
    void shouldAllowRuntimeConnectionDetailsAndPortsToChange() {
        properties.put("nacos.plugin.datasource-dialect.type", "mysql");
        Properties manifest = NacosAotManifest.snapshot(environment, loader);
        properties.put("nacos.plugin.datasource.db.url.0",
            "jdbc:mysql://another-host/another-database");
        properties.put("nacos.plugin.datasource.db.password.0", "runtime-secret");
        properties.put("nacos.server.main.port", "28848");
        properties.put("nacos.ai.registry.port", "29080");
        assertDoesNotThrow(() -> NacosAotManifest.validate(manifest, environment, loader));
        assertTrue(manifest.values().stream()
            .noneMatch(value -> value.toString().contains("runtime-secret")));
    }
    
    @ParameterizedTest
    @CsvSource({"nacos.plugin.datasource-dialect.type,mysql,datasource",
        "nacos.ai.mcp.registry.enabled,true,nacos.ai.mcp.registry.enabled",
        "nacos.ai.skill.registry.enabled,true,nacos.ai.skill.registry.enabled",
        "nacos.ai.ard.enabled,true,nacos.ai.ard.enabled",
        "nacos.ai.resource.search.enabled,false,nacos.ai.resource.search.enabled"})
    void shouldRejectStructuralChanges(String key, String value, String expectedKey) {
        Properties manifest = NacosAotManifest.snapshot(environment, loader);
        properties.put(key, value);
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> NacosAotManifest.validate(manifest, environment, loader));
        assertTrue(error.getMessage().contains(expectedKey));
    }
    
    @Test
    void shouldRejectDeploymentAndProfileChanges() {
        Properties manifest = NacosAotManifest.snapshot(environment, loader);
        global.when(EnvUtil::getDeploymentType).thenReturn(DeploymentType.MERGED);
        assertThrows(IllegalStateException.class,
            () -> NacosAotManifest.validate(manifest, environment, loader));
        global.when(EnvUtil::getDeploymentType).thenReturn(DeploymentType.SERVER);
        environment.setActiveProfiles("another-profile");
        assertThrows(IllegalStateException.class,
            () -> NacosAotManifest.validate(manifest, environment, loader));
    }
    
    @Test
    void shouldAcceptEnabledRegistryProtocolsWhenBuiltWithSameConfiguration() {
        properties.put("nacos.ai.mcp.registry.enabled", "true");
        properties.put("nacos.ai.skill.registry.enabled", "true");
        properties.put("nacos.ai.ard.enabled", "true");
        assertTrue(NacosAotManifest.hasAiRegistry(environment));
        assertDoesNotThrow(() -> NacosAotManifest
            .validate(NacosAotManifest.snapshot(environment, loader), environment, loader));
    }
    
    @Test
    void shouldRejectPluginsAddedAfterGenerationWithoutInstantiatingProviders() throws Exception {
        Properties before = NacosAotManifest.snapshot(environment, loader);
        String service = "com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect";
        Path file = temporary.resolve("META-INF/services/" + service);
        Files.createDirectories(file.getParent());
        Files.writeString(file,
            "# extension fixture\nexample.CustomDialect # comment\nexample.CustomDialect\n");
        try (URLClassLoader plugins =
            new URLClassLoader(new URL[] {temporary.toUri().toURL()}, null)) {
            Properties after = NacosAotManifest.snapshot(environment, plugins);
            assertEquals("example.CustomDialect", after.getProperty("spi." + service));
            assertDoesNotThrow(() -> NacosAotManifest.validate(after, environment, plugins));
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> NacosAotManifest.validate(before, environment, plugins));
            assertTrue(error.getMessage().contains("spi." + service));
        }
    }
    
    @Test
    void shouldExplainObsoleteManifest() {
        assertTrue(assertThrows(IllegalStateException.class,
            () -> NacosAotManifest.validate(new Properties(), environment, loader)).getMessage()
            .contains("regenerate"));
    }
    
    @ParameterizedTest
    @CsvSource({"false,false,mysql", "false,true,derby", "true,false,derby"})
    void shouldChooseDefaultDatasourceForStorageMode(boolean standalone, boolean embedded,
        String expected) {
        String previous = System.getProperty("embeddedStorage");
        try {
            global.when(EnvUtil::getStandaloneMode).thenReturn(standalone);
            System.setProperty("embeddedStorage", Boolean.toString(embedded));
            properties.put("nacos.plugin.datasource-dialect.type", "  ");
            properties.put("spring.sql.init.platform", "  ");
            Properties manifest = NacosAotManifest.snapshot(environment, loader);
            assertEquals(expected, manifest.getProperty("datasource"));
            properties.put("nacos.plugin.datasource-dialect.type", expected);
            System.setProperty("embeddedStorage", Boolean.toString(!embedded));
            assertTrue(assertThrows(IllegalStateException.class,
                () -> NacosAotManifest.validate(manifest, environment, loader)).getMessage()
                .contains("embeddedStorage"));
        } finally {
            if (previous == null) {
                System.clearProperty("embeddedStorage");
            } else {
                System.setProperty("embeddedStorage", previous);
            }
        }
    }
    
    @Test
    void shouldNormalizeDatasourceAndSortProfiles() {
        properties.put("nacos.plugin.datasource-dialect.type", " MySQL ");
        environment.setActiveProfiles("two", "one");
        Properties manifest = NacosAotManifest.snapshot(environment, loader);
        assertEquals("mysql", manifest.getProperty("datasource"));
        assertEquals("one,two", manifest.getProperty("profiles"));
        environment.setActiveProfiles("one", "two");
        assertDoesNotThrow(() -> NacosAotManifest.validate(manifest, environment, loader));
    }
    
    @Test
    void shouldRejectStandaloneAndFunctionModeChanges() {
        Properties manifest = NacosAotManifest.snapshot(environment, loader);
        global.when(EnvUtil::getStandaloneMode).thenReturn(false);
        assertThrows(IllegalStateException.class,
            () -> NacosAotManifest.validate(manifest, environment, loader));
        global.when(EnvUtil::getStandaloneMode).thenReturn(true);
        properties.put("nacos.function.mode", "config");
        assertTrue(assertThrows(IllegalStateException.class,
            () -> NacosAotManifest.validate(manifest, environment, loader)).getMessage()
            .contains("functionMode"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"nacos.ai.mcp.registry.enabled", "nacos.ai.skill.registry.enabled",
        "nacos.ai.ard.enabled"})
    void shouldGenerateRegistryForEachProtocolIndependently(String key) {
        assertFalse(NacosAotManifest.hasAiRegistry(environment));
        properties.put(key, true);
        assertTrue(NacosAotManifest.hasAiRegistry(environment));
    }
    
    @Test
    void shouldReportSpiEnumerationFailureWithCause() throws Exception {
        ClassLoader failing = mock(ClassLoader.class);
        String service = NacosAotManifest.SERVICES[0];
        IOException cause = new IOException("unreadable provider index");
        when(failing.getResources("META-INF/services/" + service)).thenThrow(cause);
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> NacosAotManifest.snapshot(environment, failing));
        assertTrue(error.getMessage().contains(service));
        assertSame(cause, error.getCause());
    }
}
