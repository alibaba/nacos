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

package com.alibaba.nacos.ai.pipeline.config;

import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeConfig;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.sys.env.EnvUtil;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Property-based test for FilePipelineConfigProvider configuration parsing correctness.
 *
 * <p><b>Validates: Requirements 4.3, 4.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class FilePipelineConfigProviderPropertyTest {
    
    /**
     * Property 10: Configuration parsing correctness.
     *
     * <p>For any valid combination of {@code nacos.ai.pipeline.enabled},
     * {@code nacos.ai.pipeline.nodes}, and {@code nacos.ai.pipeline.node.{pipelineId}.*}
     * config entries, FilePipelineConfigProvider should parse them into a PipelineConfig
     * whose enabled value, nodes list, and per-node Properties all match the original config.</p>
     *
     * <p><b>Validates: Requirements 4.3, 4.4</b></p>
     */
    @Property
    void configParsingCorrectness(@ForAll boolean enabled,
            @ForAll("validNodeConfigs") List<NodeTestData> nodeConfigs) {
        
        // Build lookup maps for all expected EnvUtil.getProperty calls
        Map<String, String> singleArgProps = new HashMap<>();
        Map<String, String> twoArgProps = new HashMap<>();
        
        String nodesStr = nodeConfigs.stream().map(n -> n.pipelineId).collect(Collectors.joining(","));
        twoArgProps.put("nacos.ai.pipeline.nodes", nodesStr);
        
        for (NodeTestData node : nodeConfigs) {
            String propsKey = "nacos.ai.pipeline.node." + node.pipelineId + ".props";
            twoArgProps.put(propsKey, String.join(",", node.propertyKeys()));
            
            for (String key : node.propertyKeys()) {
                String fullKey = "nacos.ai.pipeline.node." + node.pipelineId + "." + key;
                singleArgProps.put(fullKey, node.properties.getProperty(key));
            }
        }
        
        try (MockedStatic<EnvUtil> envMock = Mockito.mockStatic(EnvUtil.class);
                MockedStatic<NotifyCenter> notifyMock = Mockito.mockStatic(NotifyCenter.class)) {
            
            // Single-arg getProperty: look up from map, default null
            envMock.when(() -> EnvUtil.getProperty(anyString()))
                    .thenAnswer(inv -> singleArgProps.get(inv.<String>getArgument(0)));
            
            // Two-arg getProperty(key, defaultValue): look up from map, fall back to default
            envMock.when(() -> EnvUtil.getProperty(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        String key = inv.getArgument(0);
                        String defaultVal = inv.getArgument(1);
                        return twoArgProps.getOrDefault(key, defaultVal);
                    });
            
            // Three-arg getProperty(key, Class, defaultValue): handle enabled flag
            envMock.when(() -> EnvUtil.getProperty(anyString(), any(Class.class), any()))
                    .thenAnswer(inv -> {
                        String key = inv.getArgument(0);
                        if ("nacos.ai.pipeline.enabled".equals(key)) {
                            return enabled;
                        }
                        return inv.getArgument(2);
                    });
            
            // Create a fresh instance via reflection (bypassing singleton)
            FilePipelineConfigProvider provider = createFreshInstance();
            
            // Verify parsed config
            PipelineConfig config = provider.getConfig();
            assertNotNull(config, "Parsed config should not be null");
            assertEquals(enabled, config.isEnabled(), "Enabled flag should match");
            assertNotNull(config.getNodes(), "Nodes list should not be null");
            assertEquals(nodeConfigs.size(), config.getNodes().size(),
                    "Number of parsed nodes should match input");
            
            for (int i = 0; i < nodeConfigs.size(); i++) {
                NodeTestData expected = nodeConfigs.get(i);
                PipelineNodeConfig actual = config.getNodes().get(i);
                assertEquals(expected.pipelineId, actual.getPipelineId(),
                        "Node pipelineId at index " + i + " should match");
                assertNotNull(actual.getProperties(),
                        "Node properties at index " + i + " should not be null");
                
                for (String key : expected.propertyKeys()) {
                    assertEquals(expected.properties.getProperty(key),
                            actual.getProperties().getProperty(key),
                            "Property '" + key + "' for node '" + expected.pipelineId + "' should match");
                }
                assertEquals(expected.propertyKeys().size(), actual.getProperties().size(),
                        "Number of properties for node '" + expected.pipelineId + "' should match");
            }
        }
    }
    
    /**
     * Property 14: Configuration error tolerance.
     *
     * <p>For any malformed configuration scenario where {@code EnvUtil.getProperty} throws a
     * {@link RuntimeException}, FilePipelineConfigProvider should fall back to default config
     * (enabled=false, nodes is empty list).</p>
     *
     * <p><b>Validates: Requirements 7.3</b></p>
     */
    @Property
    void configErrorTolerance(@ForAll("errorScenarios") ErrorScenario scenario) {
        
        try (MockedStatic<EnvUtil> envMock = Mockito.mockStatic(EnvUtil.class);
                MockedStatic<NotifyCenter> notifyMock = Mockito.mockStatic(NotifyCenter.class)) {
            
            // Mock three-arg getProperty(key, Class, defaultValue) for enabled flag
            envMock.when(() -> EnvUtil.getProperty(anyString(), any(Class.class), any()))
                    .thenAnswer(inv -> {
                        if (scenario.failOnEnabled) {
                            throw new RuntimeException(scenario.errorMessage);
                        }
                        String key = inv.getArgument(0);
                        if ("nacos.ai.pipeline.enabled".equals(key)) {
                            return false;
                        }
                        return inv.getArgument(2);
                    });
            
            // Mock two-arg getProperty(key, defaultValue) for nodes
            envMock.when(() -> EnvUtil.getProperty(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        if (scenario.failOnNodes) {
                            throw new RuntimeException(scenario.errorMessage);
                        }
                        return inv.<String>getArgument(1);
                    });
            
            // Mock single-arg getProperty(key) - not expected to be called in error scenarios
            envMock.when(() -> EnvUtil.getProperty(anyString()))
                    .thenAnswer(inv -> {
                        throw new RuntimeException(scenario.errorMessage);
                    });
            
            // Create a fresh instance - constructor triggers resetConfig() -> getConfigFromEnv()
            FilePipelineConfigProvider provider = createFreshInstance();
            
            // Verify fallback to default config
            PipelineConfig config = provider.getConfig();
            assertNotNull(config, "Config should not be null even on error");
            assertFalse(config.isEnabled(), "Default config should have enabled=false");
            assertNotNull(config.getNodes(), "Default config nodes should not be null");
            assertTrue(config.getNodes().isEmpty(), "Default config should have empty nodes list");
        }
    }
    
    @Provide
    Arbitrary<ErrorScenario> errorScenarios() {
        Arbitrary<Boolean> failOnEnabled = Arbitraries.of(true, false);
        Arbitrary<Boolean> failOnNodes = Arbitraries.of(true, false);
        Arbitrary<String> errorMessages = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        
        return Combinators.combine(failOnEnabled, failOnNodes, errorMessages)
                .as(ErrorScenario::new)
                .filter(s -> s.failOnEnabled || s.failOnNodes);
    }
    
    /**
     * Test data holder for an error scenario.
     */
    static class ErrorScenario {
        
        final boolean failOnEnabled;
        
        final boolean failOnNodes;
        
        final String errorMessage;
        
        ErrorScenario(boolean failOnEnabled, boolean failOnNodes, String errorMessage) {
            this.failOnEnabled = failOnEnabled;
            this.failOnNodes = failOnNodes;
            this.errorMessage = errorMessage;
        }
        
        @Override
        public String toString() {
            return "ErrorScenario{failOnEnabled=" + failOnEnabled
                    + ", failOnNodes=" + failOnNodes
                    + ", errorMessage='" + errorMessage + "'}";
        }
    }
    
    /**
     * Creates a fresh FilePipelineConfigProvider instance via reflection, bypassing the singleton.
     * The constructor calls resetConfig() which calls getConfigFromEnv(), so EnvUtil must be mocked first.
     */
    private FilePipelineConfigProvider createFreshInstance() {
        try {
            Constructor<FilePipelineConfigProvider> constructor =
                    FilePipelineConfigProvider.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create FilePipelineConfigProvider instance via reflection", e);
        }
    }
    
    @Provide
    Arbitrary<List<NodeTestData>> validNodeConfigs() {
        return nodeTestData().list().ofMinSize(0).ofMaxSize(5)
                .filter(list -> list.stream().map(n -> n.pipelineId).distinct().count() == list.size());
    }
    
    private Arbitrary<NodeTestData> nodeTestData() {
        Arbitrary<String> pipelineIds = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(12)
                .map(String::toLowerCase);
        Arbitrary<Properties> props = nodeProperties();
        
        return Combinators.combine(pipelineIds, props).as(NodeTestData::new);
    }
    
    private Arbitrary<Properties> nodeProperties() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10)
                .map(String::toLowerCase);
        Arbitrary<String> values = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
        
        return Combinators.combine(keys, values).as((k, v) -> {
            Properties p = new Properties();
            p.setProperty(k, v);
            return p;
        }).list().ofMinSize(0).ofMaxSize(3).map(propsList -> {
            Properties merged = new Properties();
            for (Properties p : propsList) {
                merged.putAll(p);
            }
            return merged;
        });
    }
    
    /**
     * Test data holder for a single pipeline node configuration.
     */
    static class NodeTestData {
        
        final String pipelineId;
        
        final Properties properties;
        
        NodeTestData(String pipelineId, Properties properties) {
            this.pipelineId = pipelineId;
            this.properties = properties;
        }
        
        List<String> propertyKeys() {
            return new ArrayList<>(properties.stringPropertyNames());
        }
        
        @Override
        public String toString() {
            return "NodeTestData{pipelineId='" + pipelineId + "', properties=" + properties + "}";
        }
    }
}
