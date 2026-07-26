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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineMessageType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.SkillPipelineContext;
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SkillSpectorPipelineService} unit test.
 *
 * @author nacos
 */
class SkillSpectorPipelineServiceTest {
    
    @Test
    void pipelineMetadataTest() {
        SkillSpectorPipelineService service = createStubService(StubScanMode.PASS_LOW_RISK,
            SkillSpectorScanOptions.none());
        
        assertEquals("skill-spector", service.pipelineId());
        assertEquals(90, service.getPreferOrder());
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.SKILL));
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.AGENTSPEC));
        assertTrue(Arrays.asList(service.pipelineResourceTypes())
            .contains(PublishPipelineResourceType.PROMPT));
    }
    
    @Test
    void configDefinitionsTest() {
        SkillSpectorPipelineService service = new SkillSpectorPipelineService(
            SkillSpectorPluginConfig.fromMap(null), command -> "/resolved/spector");
        Map<String, ConfigItemDefinition> definitions = new LinkedHashMap<>();
        service.getConfigDefinitions()
            .forEach(definition -> definitions.put(definition.getKey(), definition));
        
        assertEquals(10, definitions.size());
        ConfigItemDefinition order = definitions.get(SkillSpectorPluginConfig.ORDER);
        assertEquals(ConfigItemType.NUMBER, order.getType());
        assertEquals("90", order.getDefaultValue());
        assertEquals(ConfigItemEffectMode.RUNTIME, order.getEffectMode());
        assertTrue(definitions.entrySet().stream()
            .filter(entry -> !SkillSpectorPluginConfig.ORDER.equals(entry.getKey()))
            .allMatch(entry -> ConfigItemEffectMode.RESTART == entry.getValue().getEffectMode()));
        ConfigItemDefinition command = definitions.get(SkillSpectorPluginConfig.COMMAND);
        assertEquals(ConfigItemType.STRING, command.getType());
        assertEquals("skill-spector", command.getDefaultValue());
        assertEquals(List.of("executable", "path"), command.getAliases());
        assertEquals(ConfigItemEffectMode.RESTART, command.getEffectMode());
        ConfigItemDefinition apiKey = definitions.get(SkillSpectorPluginConfig.API_KEY);
        assertTrue(apiKey.isSensitive());
        assertEquals(List.of("apiKey"), apiKey.getAliases());
        assertEquals(ConfigItemType.NUMBER,
            definitions.get(SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD).getType());
        assertEquals("20",
            definitions.get(SkillSpectorPluginConfig.MAX_FINDINGS).getDefaultValue());
    }
    
    @Test
    void applyConfigReplacesRuntimeSnapshotTest() {
        SkillSpectorPipelineService unavailable = new SkillSpectorPipelineService(
            SkillSpectorPluginConfig.fromMap(null), command -> null);
        assertEquals("skill-spector",
            unavailable.getCurrentConfig().get(SkillSpectorPluginConfig.COMMAND));
        
        AtomicInteger resolveCount = new AtomicInteger();
        SkillSpectorPipelineService service = new SkillSpectorPipelineService(
            SkillSpectorPluginConfig.fromMap(null), command -> {
                resolveCount.incrementAndGet();
                return "/resolved/" + command;
            });
        Map<String, String> config = new LinkedHashMap<>();
        config.put(SkillSpectorPluginConfig.COMMAND, "custom-spector");
        config.put(SkillSpectorPluginConfig.USE_LLM, "true");
        config.put(SkillSpectorPluginConfig.API_KEY, "secret");
        config.put(SkillSpectorPluginConfig.ORDER, "30");
        service.applyConfig(config);
        service.applyConfig(config);
        Map<String, String> reordered = new LinkedHashMap<>(config);
        reordered.put(SkillSpectorPluginConfig.ORDER, "3");
        service.applyConfig(reordered);
        Map<String, String> current = service.getCurrentConfig();
        
        assertEquals(2, resolveCount.get());
        assertEquals(3, service.getPreferOrder());
        assertEquals("custom-spector", current.get(SkillSpectorPluginConfig.COMMAND));
        assertEquals("true", current.get(SkillSpectorPluginConfig.USE_LLM));
        assertEquals("secret", current.get(SkillSpectorPluginConfig.API_KEY));
        current.put(SkillSpectorPluginConfig.COMMAND, "mutated");
        assertEquals("custom-spector",
            service.getCurrentConfig().get(SkillSpectorPluginConfig.COMMAND));
    }
    
    @Test
    void spiConstructorShouldDeferRuntimeInitializationUntilApplyTest() {
        AtomicInteger resolveCount = new AtomicInteger();
        SkillSpectorPipelineService service = new SkillSpectorPipelineService(command -> {
            resolveCount.incrementAndGet();
            return "/resolved/" + command;
        });
        assertEquals(0, resolveCount.get());
        assertEquals("skill-spector",
            service.getCurrentConfig().get(SkillSpectorPluginConfig.COMMAND));
        assertFalse(service.execute(new PublishPipelineContext()).isPassed());
        
        service.applyConfig(service.getCurrentConfig());
        
        assertEquals(1, resolveCount.get());
        assertEquals(SkillSpectorPluginConfig.DEFAULT_ORDER, service.getPreferOrder());
    }
    
    @Test
    void buildScanCommandStaticOnlyTest() {
        SkillSpectorPipelineService service = new SkillSpectorPipelineService("skillspector",
            null);
        
        List<String> command = service.buildScanCommand(Path.of("/tmp/skill"),
            Path.of("/tmp/report.json"), "skillspector", SkillSpectorScanOptions.none());
        
        assertEquals(List.of("skillspector", "scan", "/tmp/skill", "--format", "json",
            "--output", "/tmp/report.json", "--no-llm"), command);
    }
    
    @Test
    void buildScanCommandWithLlmTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.USE_LLM, "true");
        SkillSpectorScanOptions options = options(properties);
        SkillSpectorPipelineService service = new SkillSpectorPipelineService("skillspector",
            options);
        
        List<String> command = service.buildScanCommand(Path.of("/tmp/skill"),
            Path.of("/tmp/report.json"), "skillspector", options);
        
        assertFalse(command.contains("--no-llm"));
    }
    
    @Test
    void executeHandlesUnavailableAndInapplicableInputsTest() {
        SkillSpectorPipelineService unavailable = new SkillSpectorPipelineService((String) null,
            null);
        PublishPipelineResult unavailableResult = unavailable.execute(createSkillContext("skill"));
        assertFalse(unavailableResult.isPassed());
        assertTrue(unavailableResult.getMessage().contains("runtime 未安装"));
        
        SkillSpectorPipelineService service = new SkillSpectorPipelineService("spector", null);
        PublishPipelineResult unsupported = service.execute(new PublishPipelineContext());
        assertTrue(unsupported.isPassed());
        assertTrue(unsupported.getMessage().contains("不包含可扫描文件"));
        ResourceFilesPipelineContext empty = new ResourceFilesPipelineContext();
        PublishPipelineResult emptyResult = service.execute(empty);
        assertTrue(emptyResult.isPassed());
        assertTrue(emptyResult.getMessage().contains("无文件内容"));
    }
    
    @Test
    void executePassesWhenRiskScoreWithinDefaultThresholdTest() {
        PublishPipelineResult result = createStubService(StubScanMode.PASS_LOW_RISK,
            SkillSpectorScanOptions.none()).execute(createSkillContext("low-risk"));
        
        assertNotNull(result);
        assertTrue(result.isPassed(), result.getMessage());
        assertTrue(result.getMessage().contains("risk_score=20"));
        assertTrue(result.getMessage().contains("## 扫描结果"));
        assertTrue(result.getMessage().contains("HIGH / R0"));
        assertTrue(result.getMessage().contains("SKILL.md:3-4"));
        assertTrue(result.getMessage().contains("修复建议"));
        assertEquals(PublishPipelineMessageType.MARKDOWN, result.getType());
    }
    
    @Test
    void executeRejectsWhenRiskScoreExceedsDefaultThresholdTest() {
        PublishPipelineResult result = createStubService(StubScanMode.REJECT_HIGH_RISK,
            SkillSpectorScanOptions.none()).execute(createSkillContext("high-risk"));
        
        assertNotNull(result);
        assertFalse(result.isPassed(), result.getMessage());
        assertTrue(result.getMessage().contains("risk_score: 70"));
        assertTrue(result.getMessage().contains("HIGH / R1"));
        assertTrue(result.getMessage().contains("explanation R1"));
        assertEquals("SkillSpector risk_score 阈值", result.getCheckpoints().get(0).getTitle());
    }
    
    @Test
    void executeUsesConfiguredMaxFindingsTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.MAX_FINDINGS, "2");
        PublishPipelineResult result = createStubService(StubScanMode.REJECT_MANY_FINDINGS,
            options(properties)).execute(createSkillContext("many"));
        
        assertNotNull(result);
        assertFalse(result.isPassed(), result.getMessage());
        assertTrue(result.getMessage().contains("HIGH / R0"));
        assertTrue(result.getMessage().contains("HIGH / R1"));
        assertFalse(result.getMessage().contains("HIGH / R2"));
        assertTrue(result.getMessage().contains("仅展示前 2 条，共 6 条问题。"));
    }
    
    @Test
    void executePassesWhenThresholdIsRaisedEvenIfCliExitCodeIsOne() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD, "80");
        PublishPipelineResult result = createStubService(StubScanMode.REJECT_HIGH_RISK,
            options(properties))
            .execute(createSkillContext("high-risk"));
        
        assertNotNull(result);
        assertTrue(result.isPassed(), result.getMessage());
        assertTrue(result.getMessage().contains("risk_score=70"));
    }
    
    @Test
    void executeRejectsOnCliErrorTest() {
        PublishPipelineResult result = createStubService(StubScanMode.CLI_ERROR,
            SkillSpectorScanOptions.none()).execute(createSkillContext("cli-error"));
        
        assertNotNull(result);
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("exitCode=2"));
    }
    
    @Test
    void executeRejectsWithOutputWhenCliExitOneWithoutReportTest() {
        PublishPipelineResult result = createStubService(StubScanMode.CLI_EXIT_ONE_WITHOUT_REPORT,
            SkillSpectorScanOptions.none()).execute(createSkillContext("missing-report"));
        
        assertNotNull(result);
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("未生成扫描报告"));
        assertTrue(result.getMessage().contains("ModuleNotFoundError"));
    }
    
    @Test
    void executeRejectsWhenProcessCannotStartTest() {
        SkillSpectorPipelineService service = new SkillSpectorPipelineService(
            "/missing/skill-spector", SkillSpectorScanOptions.none());
        
        PublishPipelineResult result = service.execute(createSkillContext("missing-runtime"));
        
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("执行 SkillSpector runtime 失败"));
    }
    
    @Test
    void executeRejectsWhenInterruptedTest() {
        PublishPipelineResult result = createStubService(StubScanMode.INTERRUPTED,
            SkillSpectorScanOptions.none()).execute(createSkillContext("interrupted"));
        
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("扫描被中断"));
        assertTrue(Thread.interrupted());
    }
    
    @Test
    void executeFormatsSparseFindingsTest() {
        PublishPipelineResult result = createStubService(StubScanMode.PASS_SPARSE_FINDINGS,
            SkillSpectorScanOptions.none()).execute(createSkillContext("sparse"));
        
        assertTrue(result.isPassed(), result.getMessage());
        assertTrue(result.getMessage().contains("UNKNOWN / UNKNOWN"));
        assertTrue(result.getMessage().contains("`README.md`"));
        assertTrue(result.getMessage().contains("`SKILL.md:3`"));
        assertTrue(result.getMessage().contains("..."));
    }
    
    @Test
    void executeAgentSpecGeneratesSkillMdTest() {
        PublishPipelineResult result = createStubService(StubScanMode.PASS_AGENTSPEC,
            SkillSpectorScanOptions.none()).execute(createAgentSpecContext("agent"));
        
        assertNotNull(result);
        assertTrue(result.isPassed(), result.getMessage());
    }
    
    @Test
    void executePromptGeneratesSkillMdTest() {
        PublishPipelineResult result = createStubService(StubScanMode.PASS_PROMPT,
            SkillSpectorScanOptions.none()).execute(createPromptContext("prompt"));
        
        assertNotNull(result);
        assertTrue(result.isPassed(), result.getMessage());
    }
    
    @Test
    void executeWithLlmOptionsExposesEnvironmentToSubprocessTest() {
        Properties properties = new Properties();
        properties.setProperty(SkillSpectorPluginConfig.USE_LLM, "true");
        properties.setProperty(SkillSpectorPluginConfig.PROVIDER, "openai");
        properties.setProperty(SkillSpectorPluginConfig.MODEL, "gpt-test");
        properties.setProperty(SkillSpectorPluginConfig.API_KEY, "test-key");
        SkillSpectorScanOptions options = options(properties);
        
        PublishPipelineResult result =
            createStubService(StubScanMode.VERIFY_LLM_ENV, options)
                .execute(createSkillContext("llm"));
        
        assertNotNull(result);
        assertTrue(result.isPassed(), result.getMessage());
    }
    
    @Test
    void executeSkipsUnsafeAndBlankFilePathsTest() {
        SkillPipelineContext context = createSkillContext("path-boundary", Arrays.asList(
            new ResourceFileContent(null, "ignored"),
            new ResourceFileContent("", "ignored"),
            new ResourceFileContent("../evil.txt", "ignored"),
            new ResourceFileContent("valid.txt", null)));
        
        PublishPipelineResult result = createStubService(StubScanMode.PASS_SKIPPED_FILES,
            SkillSpectorScanOptions.none()).execute(context);
        
        assertNotNull(result);
        assertTrue(result.isPassed(), result.getMessage());
    }
    
    @Test
    void deleteRecursivelyHandlesMissingAndUndeletableFilesTest() {
        SkillSpectorPipelineService service = new SkillSpectorPipelineService("spector", null);
        service.deleteRecursively(null);
        File missing = mock(File.class);
        when(missing.exists()).thenReturn(false);
        service.deleteRecursively(missing);
        File undeletable = mock(File.class);
        when(undeletable.exists()).thenReturn(true);
        when(undeletable.isDirectory()).thenReturn(false);
        when(undeletable.delete()).thenReturn(false);
        when(undeletable.getAbsolutePath()).thenReturn("/undeletable");
        
        service.deleteRecursively(undeletable);
    }
    
    private SkillSpectorPipelineService createStubService(StubScanMode mode,
        SkillSpectorScanOptions options) {
        return new StubSkillSpectorPipelineService(mode, options);
    }
    
    private SkillSpectorScanOptions options(Properties properties) {
        return SkillSpectorPluginConfig.fromMap(PluginConfigTestUtils.toMap(properties))
            .getScanOptions();
    }
    
    private SkillPipelineContext createSkillContext(String name) {
        return createSkillContext(name, List.of(new ResourceFileContent("SKILL.md",
            "---\nname: " + name + "\n---\n\nhello")));
    }
    
    private SkillPipelineContext createSkillContext(String name, List<ResourceFileContent> files) {
        SkillPipelineContext context = new SkillPipelineContext();
        context.setResourceName(name);
        context.setNamespaceId("public");
        context.setVersion("v1");
        context.setFiles(files);
        return context;
    }
    
    private ResourceFilesPipelineContext createAgentSpecContext(String name) {
        ResourceFilesPipelineContext context = new ResourceFilesPipelineContext();
        context.setResourceType(PublishPipelineResourceType.AGENTSPEC);
        context.setResourceName(name);
        context.setNamespaceId("public");
        context.setVersion("v1");
        context.setFiles(Arrays.asList(
            new ResourceFileContent(null, "ignored"),
            new ResourceFileContent("manifest.json", "{\"name\":\"" + name + "\"}"),
            new ResourceFileContent("config/SOUL.md", "helpful assistant")));
        return context;
    }
    
    private ResourceFilesPipelineContext createPromptContext(String name) {
        ResourceFilesPipelineContext context = new ResourceFilesPipelineContext();
        context.setResourceType(PublishPipelineResourceType.PROMPT);
        context.setResourceName(name);
        context.setNamespaceId("public");
        context.setVersion("v1");
        context.setFiles(List.of(new ResourceFileContent("prompt-main.json",
            "{\"template\":\"helpful assistant\"}")));
        return context;
    }
    
    private enum StubScanMode {
        PASS_LOW_RISK,
        REJECT_HIGH_RISK,
        REJECT_MANY_FINDINGS,
        CLI_ERROR,
        PASS_AGENTSPEC,
        PASS_PROMPT,
        VERIFY_LLM_ENV,
        PASS_SKIPPED_FILES,
        CLI_EXIT_ONE_WITHOUT_REPORT,
        INTERRUPTED,
        PASS_SPARSE_FINDINGS
    }
    
    private static class StubSkillSpectorPipelineService extends SkillSpectorPipelineService {
        
        private final StubScanMode mode;
        
        private StubSkillSpectorPipelineService(StubScanMode mode,
            SkillSpectorScanOptions options) {
            super("stub-skillspector", options);
            this.mode = mode;
        }
        
        @Override
        List<String> buildScanCommand(Path tempDir, Path reportPath, String scannerCommand,
            SkillSpectorScanOptions scanOptions) {
            return Arrays.asList(currentJavaBinary(), "-cp",
                System.getProperty("java.class.path"),
                FakeSkillSpectorCli.class.getName(), mode.name(),
                tempDir.toAbsolutePath().toString(), reportPath.toAbsolutePath().toString());
        }
        
        @Override
        int waitForProcess(Process process) throws InterruptedException {
            if (mode == StubScanMode.INTERRUPTED) {
                throw new InterruptedException("test interruption");
            }
            return super.waitForProcess(process);
        }
        
        private static String currentJavaBinary() {
            String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java";
            return Path.of(System.getProperty("java.home"), "bin", executable).toString();
        }
    }
    
    public static final class FakeSkillSpectorCli {
        
        public static void main(String[] args) throws Exception {
            StubScanMode mode = StubScanMode.valueOf(args[0]);
            Path root = Path.of(args[1]);
            Path reportPath = Path.of(args[2]);
            switch (mode) {
                case PASS_LOW_RISK:
                    requireContains(root.resolve("SKILL.md"), "hello");
                    System.out.println("scan completed");
                    writeReport(reportPath, 20, "LOW", "SAFE", 1);
                    return;
                case REJECT_HIGH_RISK:
                    requireContains(root.resolve("SKILL.md"), "hello");
                    writeReport(reportPath, 70, "HIGH", "DO_NOT_INSTALL", 2);
                    System.exit(1);
                    return;
                case REJECT_MANY_FINDINGS:
                    requireContains(root.resolve("SKILL.md"), "hello");
                    writeReport(reportPath, 70, "HIGH", "DO_NOT_INSTALL", 6);
                    System.exit(1);
                    return;
                case CLI_ERROR:
                    System.out.println("boom");
                    System.exit(2);
                    return;
                case PASS_AGENTSPEC:
                    requireContains(root.resolve("SKILL.md"),
                        "Generated from AgentSpec pipeline context");
                    requireContains(root.resolve("SKILL.md"), "File: config/SOUL.md");
                    writeReport(reportPath, 10, "LOW", "SAFE", 0);
                    return;
                case PASS_PROMPT:
                    requireContains(root.resolve("SKILL.md"),
                        "Generated from Prompt pipeline context");
                    requireContains(root.resolve("SKILL.md"), "File: prompt-main.json");
                    writeReport(reportPath, 10, "LOW", "SAFE", 0);
                    return;
                case VERIFY_LLM_ENV:
                    requireEnv("SKILLSPECTOR_PROVIDER", "openai");
                    requireEnv("SKILLSPECTOR_MODEL", "gpt-test");
                    requireEnv("OPENAI_API_KEY", "test-key");
                    writeReport(reportPath, 20, "LOW", "SAFE", 0);
                    return;
                case PASS_SKIPPED_FILES:
                    requireEmpty(root.resolve("valid.txt"));
                    requireNotExists(root.resolve("../evil.txt").normalize());
                    writeReport(reportPath, 20, "LOW", "SAFE", 0);
                    return;
                case CLI_EXIT_ONE_WITHOUT_REPORT:
                    System.out.println("ModuleNotFoundError: No module named 'skillspector'");
                    System.exit(1);
                    return;
                case INTERRUPTED:
                    writeReport(reportPath, 20, "LOW", "SAFE", 0);
                    return;
                case PASS_SPARSE_FINDINGS:
                    writeSparseReport(reportPath);
                    return;
                default:
                    throw new IllegalStateException("Unsupported mode: " + mode);
            }
        }
        
        private static void writeReport(Path reportPath, int score, String severity,
            String recommendation, int issueCount) throws Exception {
            StringBuilder issues = new StringBuilder("[");
            for (int i = 0; i < issueCount; i++) {
                if (i > 0) {
                    issues.append(",");
                }
                issues.append("{\"id\":\"R").append(i)
                    .append("\",\"category\":\"prompt-injection\"")
                    .append(",\"severity\":\"HIGH\"")
                    .append(",\"location\":{\"file\":\"SKILL.md\"")
                    .append(",\"start_line\":3,\"end_line\":4}")
                    .append(",\"explanation\":\"explanation R").append(i).append("\"")
                    .append(",\"remediation\":\"fix R").append(i).append("\"")
                    .append(",\"code_snippet\":\"snippet R").append(i).append("\"}");
            }
            issues.append("]");
            String report = "{\"risk_assessment\":{\"score\":" + score
                + ",\"severity\":\"" + severity
                + "\",\"recommendation\":\"" + recommendation
                + "\"},\"issues\":" + issues + "}";
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        }
        
        private static void writeSparseReport(Path reportPath) throws Exception {
            String longExplanation = "x".repeat(260);
            String report = "{\"risk_assessment\":{\"score\":20,\"severity\":\"LOW\","
                + "\"recommendation\":\"SAFE\"},\"issues\":[{},"
                + "{\"location\":{\"file\":\"README.md\"}},"
                + "{\"id\":\"R2\",\"category\":\"category\","
                + "\"location\":{\"file\":\"SKILL.md\",\"start_line\":3,"
                + "\"end_line\":3},\"explanation\":\"" + longExplanation + "\"}]}";
            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        }
        
        private static void requireContains(Path path, String expected) throws Exception {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.contains(expected)) {
                throw new IllegalStateException(
                    "Expected '" + expected + "' in " + path + ", actual=" + content);
            }
        }
        
        private static void requireEmpty(Path path) throws Exception {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.isEmpty()) {
                throw new IllegalStateException(
                    "Expected empty content in " + path + ", actual=" + content);
            }
        }
        
        private static void requireNotExists(Path path) {
            if (Files.exists(path)) {
                throw new IllegalStateException("Expected path not to exist: " + path);
            }
        }
        
        private static void requireEnv(String key, String expected) {
            String actual = System.getenv(key);
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                    "Expected env " + key + "=" + expected + ", actual=" + actual);
            }
        }
    }
}
