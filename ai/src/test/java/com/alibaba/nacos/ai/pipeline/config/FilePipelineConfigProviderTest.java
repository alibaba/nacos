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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for FilePipelineConfigProvider configuration parsing correctness.
 *
 * <p><b>Validates: Requirements 4.3, 4.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class FilePipelineConfigProviderTest {
    
    private static List<List<NodeTestData>> sampleValidNodeConfigs() {
        List<List<NodeTestData>> lists = new ArrayList<>();
        lists.add(Collections.emptyList());
        Properties p1 = new Properties();
        p1.setProperty("k1", "v1");
        lists.add(Collections.singletonList(new NodeTestData("scanner", "t", p1)));
        Properties p2 = new Properties();
        p2.setProperty("a", "b");
        p2.setProperty("c", "d");
        lists.add(Arrays.asList(
            new NodeTestData("alpha", "x", p2),
            new NodeTestData("beta", "y", new Properties())));
        return lists;
    }
    
    private static List<ErrorScenario> sampleErrorScenarios() {
        return Arrays.asList(
            new ErrorScenario(true, false, "fail-enabled"),
            new ErrorScenario(false, true, "fail-nodes"),
            new ErrorScenario(true, true, "fail-both"));
    }
    
    /**
     * Configuration parsing correctness.
     *
     * <p><b>Validates: Requirements 4.3, 4.4</b></p>
     */
    @Test
    void configParsingCorrectness() {
        for (boolean enabled : new boolean[] {true, false}) {
            for (List<NodeTestData> nodeConfigs : sampleValidNodeConfigs()) {
                Properties envProperties = new Properties();
                envProperties.setProperty("nacos.plugin.ai-pipeline.enabled",
                    String.valueOf(enabled));
                if (!nodeConfigs.isEmpty()) {
                    String typeValue = nodeConfigs.stream().map(node -> node.pipelineId)
                        .collect(Collectors.joining(","));
                    envProperties.setProperty("nacos.plugin.ai-pipeline.type", typeValue);
                }
                for (NodeTestData node : nodeConfigs) {
                    for (String key : node.propertyKeys()) {
                        String fullKey = "nacos.plugin.ai-pipeline." + node.pipelineId + "." + key;
                        envProperties.setProperty(fullKey, node.properties.getProperty(key));
                    }
                }
                
                try (MockedStatic<EnvUtil> envMock = Mockito.mockStatic(EnvUtil.class);
                    MockedStatic<NotifyCenter> notifyMock =
                        Mockito.mockStatic(NotifyCenter.class)) {
                    envMock.when(EnvUtil::getProperties).thenReturn(envProperties);
                    
                    FilePipelineConfigProvider provider = createFreshInstance();
                    PipelineConfig config = provider.getConfig();
                    List<NodeTestData> expectedNodes = new ArrayList<>(nodeConfigs);
                    expectedNodes.sort(Comparator.comparing(node -> node.pipelineId));
                    
                    assertNotNull(config, "Parsed config should not be null");
                    assertEquals(enabled && !nodeConfigs.isEmpty(), config.isEnabled(),
                        "Enabled flag should match");
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
                            assertEquals(expected.properties.getProperty(key),
                                actual.getProperties().getProperty(key),
                                "Property '" + key + "' for node '" + expected.pipelineId
                                    + "' should match");
                        }
                        assertEquals(expected.propertyKeys().size(), actual.getProperties().size(),
                            "Number of properties for node '" + expected.pipelineId
                                + "' should match");
                    }
                }
            }
        }
    }
    
    /**
     * Configuration error tolerance.
     *
     * <p><b>Validates: Requirements 7.3</b></p>
     */
    @Test
    void configErrorTolerance() {
        for (ErrorScenario scenario : sampleErrorScenarios()) {
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
                assertTrue(config.getNodes().isEmpty(),
                    "Default config should have empty nodes list");
            }
        }
    }
    
    @Test
    void subPropertyEnabledShouldNotBeTreatedAsNodeSwitch() {
        Properties envProperties = new Properties();
        envProperties.setProperty("nacos.plugin.ai-pipeline.enabled", "true");
        envProperties.setProperty("nacos.plugin.ai-pipeline.type", "skill-scanner");
        envProperties.setProperty("nacos.plugin.ai-pipeline.skill-scanner.enabled", "true");
        envProperties.setProperty("nacos.plugin.ai-pipeline.skill-scanner.command",
            "/tmp/skill-scanner");
        try (MockedStatic<EnvUtil> envMock = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<NotifyCenter> notifyMock = Mockito.mockStatic(NotifyCenter.class)) {
            envMock.when(EnvUtil::getProperties).thenReturn(envProperties);
            
            FilePipelineConfigProvider provider = createFreshInstance();
            PipelineConfig config = provider.getConfig();
            assertTrue(config.isEnabled(),
                "Config should be enabled when plugin switch is true and type exists");
            assertEquals(1, config.getNodes().size(),
                "Only configured type should be parsed as one node");
            PipelineNodeConfig node = config.getNodes().get(0);
            assertEquals("skill-scanner", node.getPipelineId(),
                "Node id should match configured type");
            assertEquals("true", node.getProperties().getProperty("enabled"),
                "Type sub-property enabled should be preserved as node property");
            assertEquals("/tmp/skill-scanner", node.getProperties().getProperty("command"),
                "Type sub-property command should be preserved");
        }
    }
    
    private FilePipelineConfigProvider createFreshInstance() {
        try {
            Constructor<FilePipelineConfigProvider> constructor =
                FilePipelineConfigProvider.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to create FilePipelineConfigProvider instance via reflection", e);
        }
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
    }
}
