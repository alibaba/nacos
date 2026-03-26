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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * <p>For any valid combination of {@code nacos.plugin.ai-pipeline.enabled},
     * {@code nacos.plugin.ai-pipeline.type}, and {@code nacos.plugin.ai-pipeline.{type}.*}
     * config entries, FilePipelineConfigProvider should parse them into a PipelineConfig whose enabled value,
     * nodes list, and per-node Properties all match the configured plugin types.</p>
     *
     * <p><b>Validates: Requirements 4.3, 4.4</b></p>
     */
    @Property
    void configParsingCorrectness(@ForAll boolean enabled,
            @ForAll("validNodeConfigs") List<NodeTestData> nodeConfigs) {
        Properties envProperties = new Properties();
        envProperties.setProperty("nacos.plugin.ai-pipeline.enabled", String.valueOf(enabled));
        if (!nodeConfigs.isEmpty()) {
            String typeValue = nodeConfigs.stream().map(node -> node.pipelineId).collect(Collectors.joining(","));
            envProperties.setProperty("nacos.plugin.ai-pipeline.type", typeValue);
        }
        for (NodeTestData node : nodeConfigs) {
            for (String key : node.propertyKeys()) {
                String fullKey = "nacos.plugin.ai-pipeline." + node.pipelineId + "." + key;
                envProperties.setProperty(fullKey, node.properties.getProperty(key));
            }
        }
        
        try (MockedStatic<EnvUtil> envMock = Mockito.mockStatic(EnvUtil.class);
                MockedStatic<NotifyCenter> notifyMock = Mockito.mockStatic(NotifyCenter.class)) {
            envMock.when(EnvUtil::getProperties).thenReturn(envProperties);
            
            FilePipelineConfigProvider provider = createFreshInstance();
            PipelineConfig config = provider.getConfig();
            List<NodeTestData> expectedNodes = new ArrayList<>(nodeConfigs);
            expectedNodes.sort(Comparator.comparing(node -> node.pipelineId));
            
            assertNotNull(config, "Parsed config should not be null");
            assertEquals(enabled && !nodeConfigs.isEmpty(), config.isEnabled(), "Enabled flag should match");
            assertNotNull(config.getNodes(), "Nodes list should not be null");
            assertEquals(enabled ? nodeConfigs.size() : 0, config.getNodes().size(),
                    "Number of parsed nodes should match input");
            
            for (int i = 0; i < config.getNodes().size(); i++) {
                NodeTestData expected = expectedNodes.get(i);
                PipelineNodeConfig actual = config.getNodes().get(i);
                assertEquals(expected.pipelineId, actual.getPipelineId(),
                        "Node pipelineId at index " + i + " should match");
                assertNotNull(actual.getProperties(),
                        "Node properties at index " + i + " should not be null");
                for (String key : expected.propertyKeys()) {
                    assertEquals(expected.properties.getProperty(key), actual.getProperties().getProperty(key),
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
     * <p>For any malformed configuration scenario where {@code EnvUtil.getProperties} throws a
     * {@link RuntimeException}, FilePipelineConfigProvider should fall back to default config
     * (enabled=false, nodes is empty list).</p>
     *
     * <p><b>Validates: Requirements 7.3</b></p>
     */
    @Property
    void configErrorTolerance(@ForAll("errorScenarios") ErrorScenario scenario) {
        try (MockedStatic<EnvUtil> envMock = Mockito.mockStatic(EnvUtil.class);
                MockedStatic<NotifyCenter> notifyMock = Mockito.mockStatic(NotifyCenter.class)) {
            envMock.when(EnvUtil::getProperties).thenAnswer(inv -> {
                if (scenario.failOnEnabled || scenario.failOnNodes) {
                    throw new RuntimeException(scenario.errorMessage);
                }
                return new Properties();
            });
            
            FilePipelineConfigProvider provider = createFreshInstance();
            PipelineConfig config = provider.getConfig();
            
            assertNotNull(config, "Config should not be null even on error");
            assertFalse(config.isEnabled(), "Default config should have enabled=false");
            assertNotNull(config.getNodes(), "Default config nodes should not be null");
            assertTrue(config.getNodes().isEmpty(), "Default config should have empty nodes list");
        }
    }
    
    @Test
    void subPropertyEnabledShouldNotBeTreatedAsNodeSwitch() {
        Properties envProperties = new Properties();
        envProperties.setProperty("nacos.plugin.ai-pipeline.enabled", "true");
        envProperties.setProperty("nacos.plugin.ai-pipeline.type", "skill-scanner");
        envProperties.setProperty("nacos.plugin.ai-pipeline.skill-scanner.enabled", "true");
        envProperties.setProperty("nacos.plugin.ai-pipeline.skill-scanner.command", "/tmp/skill-scanner");
        try (MockedStatic<EnvUtil> envMock = Mockito.mockStatic(EnvUtil.class);
                MockedStatic<NotifyCenter> notifyMock = Mockito.mockStatic(NotifyCenter.class)) {
            envMock.when(EnvUtil::getProperties).thenReturn(envProperties);
            
            FilePipelineConfigProvider provider = createFreshInstance();
            PipelineConfig config = provider.getConfig();
            assertTrue(config.isEnabled(), "Config should be enabled when plugin switch is true and type exists");
            assertEquals(1, config.getNodes().size(), "Only configured type should be parsed as one node");
            PipelineNodeConfig node = config.getNodes().get(0);
            assertEquals("skill-scanner", node.getPipelineId(), "Node id should match configured type");
            assertEquals("true", node.getProperties().getProperty("enabled"),
                    "Type sub-property enabled should be preserved as node property");
            assertEquals("/tmp/skill-scanner", node.getProperties().getProperty("command"),
                    "Type sub-property command should be preserved");
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
        Arbitrary<String> typeNames = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(8)
                .map(String::toLowerCase);
        Arbitrary<Properties> props = nodeProperties();
        return Combinators.combine(pipelineIds, typeNames, props).as(NodeTestData::new);
    }
    
    private Arbitrary<Properties> nodeProperties() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10)
                .map(String::toLowerCase);
        Arbitrary<String> values = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
        return Combinators.combine(keys, values).as((key, value) -> {
            Properties properties = new Properties();
            properties.setProperty(key, value);
            return properties;
        }).list().ofMinSize(0).ofMaxSize(3).map(propsList -> {
            Properties merged = new Properties();
            for (Properties properties : propsList) {
                merged.putAll(properties);
            }
            return merged;
        });
    }
    
    static class ErrorScenario {
        
        final boolean failOnEnabled;
        
        final boolean failOnNodes;
        
        final String errorMessage;
        
        ErrorScenario(Boolean failOnEnabled, Boolean failOnNodes, String errorMessage) {
            this.failOnEnabled = Boolean.TRUE.equals(failOnEnabled);
            this.failOnNodes = Boolean.TRUE.equals(failOnNodes);
            this.errorMessage = errorMessage;
        }
        
        @Override
        public String toString() {
            return "ErrorScenario{failOnEnabled=" + failOnEnabled
                    + ", failOnNodes=" + failOnNodes
                    + ", errorMessage='" + errorMessage + "'}";
        }
    }
    
    static class NodeTestData {
        
        final String pipelineId;
        
        final Properties properties;
        
        NodeTestData(String pipelineId, String typeName, Properties properties) {
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
