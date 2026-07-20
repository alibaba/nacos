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

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.Checkpoint;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineMessageType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Publish pipeline service that integrates SkillSpector for AI resource security scanning.
 *
 * @author nacos
 */
public class SkillSpectorPipelineService
    extends AbstractCommandPipelineService<SkillSpectorScanOptions> {
    
    static final String PIPELINE_ID = "skill-spector";
    
    static final String DEFAULT_SKILL_SPECTOR_CMD = SkillSpectorPluginConfig.DEFAULT_COMMAND;
    
    private static final List<ConfigItemDefinition> CONFIG_DEFINITIONS = buildConfigDefinitions();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillSpectorPipelineService.class);
    
    private static final String CHECKPOINT_AVAILABILITY = "SkillSpector runtime 安装状态";
    
    private static final String CHECKPOINT_APPLICABILITY = "SkillSpector 扫描适用性";
    
    private static final String CHECKPOINT_RUNTIME = "SkillSpector 扫描运行时";
    
    private static final String CHECKPOINT_RISK_SCORE = "SkillSpector risk_score 阈值";
    
    private static final int MAX_FIELD_LENGTH = 240;
    
    static final String INSTALLATION_HINT =
        "SkillSpector runtime 未安装。请先通过 nacos-setup skill-spector install 安装，"
            + "默认路径为 ~/ai-infra/ai-pipeline/bin/skill-spector；"
            + "如需自定义路径，请配置 nacos.plugin.ai-pipeline.skill-spector.command。";
    
    public SkillSpectorPipelineService() {
        this(SkillSpectorCommandResolver::resolve);
    }
    
    SkillSpectorPipelineService(Function<String, String> commandResolver) {
        super(toPipelineServiceConfig(
            SkillSpectorPluginConfig.fromMap(Collections.emptyMap())), commandResolver);
    }
    
    SkillSpectorPipelineService(String scannerCommand, SkillSpectorScanOptions scanOptions) {
        super(scannerCommand, scanOptions != null ? scanOptions : SkillSpectorScanOptions.none(),
            SkillSpectorPluginConfig.DEFAULT_ORDER, SkillSpectorCommandResolver::resolve);
    }
    
    SkillSpectorPipelineService(SkillSpectorPluginConfig config,
        Function<String, String> commandResolver) {
        super(toPipelineServiceConfig(config), commandResolver);
        initializeRuntime();
    }
    
    private static PipelineServiceConfig<SkillSpectorScanOptions> toPipelineServiceConfig(
        SkillSpectorPluginConfig config) {
        return new PipelineServiceConfig<>(config.toMap(), config.getCommand(),
            config.getScanOptions(), config.getOrder());
    }
    
    private static List<ConfigItemDefinition> buildConfigDefinitions() {
        ConfigItemDefinition order = new ConfigItemDefinition.Builder(
            SkillSpectorPluginConfig.ORDER, "Execution order", ConfigItemType.NUMBER)
            .description("Pipeline execution order; lower values execute first")
            .defaultValue(Integer.toString(SkillSpectorPluginConfig.DEFAULT_ORDER))
            .effectMode(ConfigItemEffectMode.RUNTIME).build();
        ConfigItemDefinition command = restartDefinition(SkillSpectorPluginConfig.COMMAND,
            "SkillSpector command", ConfigItemType.STRING, DEFAULT_SKILL_SPECTOR_CMD,
            "CLI command or executable path resolved during server startup",
            SkillSpectorPluginConfig.COMMAND_ALIAS_EXECUTABLE,
            SkillSpectorPluginConfig.COMMAND_ALIAS_PATH);
        ConfigItemDefinition useLlm = restartDefinition(SkillSpectorPluginConfig.USE_LLM,
            "Use LLM analysis", ConfigItemType.BOOLEAN, Boolean.FALSE.toString(),
            "Enable SkillSpector LLM analysis", SkillSpectorPluginConfig.USE_LLM_ALIAS);
        ConfigItemDefinition provider = restartDefinition(SkillSpectorPluginConfig.PROVIDER,
            "LLM provider", ConfigItemType.STRING, "",
            "LLM provider passed to the SkillSpector subprocess");
        ConfigItemDefinition model = restartDefinition(SkillSpectorPluginConfig.MODEL,
            "LLM model", ConfigItemType.STRING, "",
            "LLM model passed to the SkillSpector subprocess");
        ConfigItemDefinition apiKey = restartDefinition(SkillSpectorPluginConfig.API_KEY,
            "LLM API key", ConfigItemType.STRING, "",
            "API key passed to the configured SkillSpector LLM provider",
            SkillSpectorPluginConfig.API_KEY_ALIAS);
        apiKey.setSensitive(true);
        ConfigItemDefinition baseUrl = restartDefinition(SkillSpectorPluginConfig.BASE_URL,
            "LLM base URL", ConfigItemType.STRING, "",
            "OpenAI-compatible endpoint passed to the SkillSpector subprocess",
            SkillSpectorPluginConfig.BASE_URL_ALIAS);
        ConfigItemDefinition logLevel = restartDefinition(SkillSpectorPluginConfig.LOG_LEVEL,
            "Runtime log level", ConfigItemType.STRING,
            SkillSpectorPluginConfig.DEFAULT_LOG_LEVEL,
            "SkillSpector subprocess log level", SkillSpectorPluginConfig.LOG_LEVEL_ALIAS);
        ConfigItemDefinition riskScoreThreshold = restartDefinition(
            SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD, "Risk score threshold",
            ConfigItemType.NUMBER,
            Integer.toString(SkillSpectorPluginConfig.DEFAULT_RISK_SCORE_THRESHOLD),
            "Reject reports above this threshold; integer values are clamped to 0..100",
            SkillSpectorPluginConfig.RISK_SCORE_THRESHOLD_ALIAS);
        ConfigItemDefinition maxFindings = restartDefinition(
            SkillSpectorPluginConfig.MAX_FINDINGS, "Maximum findings", ConfigItemType.NUMBER,
            Integer.toString(SkillSpectorPluginConfig.DEFAULT_MAX_FINDINGS),
            "Maximum findings in the review message; integer values are capped at 100",
            SkillSpectorPluginConfig.MAX_FINDINGS_ALIAS);
        return Collections.unmodifiableList(Arrays.asList(order, command, useLlm, provider,
            model, apiKey, baseUrl, logLevel, riskScoreThreshold, maxFindings));
    }
    
    @Override
    public String pipelineId() {
        return PIPELINE_ID;
    }
    
    @Override
    public List<ConfigItemDefinition> getConfigDefinitions() {
        return CONFIG_DEFINITIONS;
    }
    
    @Override
    protected PipelineServiceConfig<SkillSpectorScanOptions> parseConfig(
        Map<String, String> config) {
        return toPipelineServiceConfig(SkillSpectorPluginConfig.fromMap(config));
    }
    
    @Override
    protected void logRuntimeStatus(String resolvedCommand, SkillSpectorScanOptions options) {
        if (StringUtils.isBlank(resolvedCommand)) {
            LOGGER.warn("[SkillSpectorPipeline] SkillSpector runtime 未安装，插件将拒绝发布。{}",
                INSTALLATION_HINT);
        } else {
            LOGGER.info("[SkillSpectorPipeline] SkillSpector runtime 已就绪，runtime={}",
                resolvedCommand);
        }
    }
    
    @Override
    public PublishPipelineResult execute(PublishPipelineContext context) {
        String scannerCommand = getRuntimeCommand();
        SkillSpectorScanOptions scanOptions = getRuntimeOptions();
        if (scannerCommand == null || scannerCommand.isBlank()) {
            return PublishPipelineResult.reject(INSTALLATION_HINT,
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_AVAILABILITY, false)));
        }
        if (!(context instanceof ResourceFilesPipelineContext)) {
            return PublishPipelineResult.pass("资源不包含可扫描文件，跳过 SkillSpector 扫描",
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_APPLICABILITY, true)));
        }
        ResourceFilesPipelineContext resourceContext = (ResourceFilesPipelineContext) context;
        List<ResourceFileContent> files = resourceContext.getFiles();
        if (files == null || files.isEmpty()) {
            return PublishPipelineResult.pass("资源无文件内容，跳过扫描",
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_APPLICABILITY, true)));
        }
        
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("nacos-skillspector-");
            writeResourceFiles(tempDir,
                normalizeFilesForScanner(context, files, "SkillSpector"));
            Path reportPath = tempDir.resolve("skillspector-report.json");
            
            Process process = startProcess(
                buildScanCommand(tempDir, reportPath, scannerCommand, scanOptions), scanOptions);
            String scanOutput = readOutput(process);
            int exitCode = waitForProcess(process);
            logScanOutput(context, resourceContext, exitCode, scanOutput);
            if (exitCode != 0 && exitCode != 1) {
                return rejectRuntimeFailure("SkillSpector 扫描失败，exitCode=" + exitCode
                    + "\n输出:\n" + scanOutput);
            }
            if (!Files.isRegularFile(reportPath)) {
                return rejectRuntimeFailure("SkillSpector 未生成扫描报告，exitCode=" + exitCode
                    + "\n输出:\n" + scanOutput);
            }
            
            SkillSpectorScanReport report = parseReport(reportPath);
            if (report.getRiskScore() > scanOptions.getRiskScoreThreshold()) {
                LOGGER.warn("[SkillSpectorPipeline] {} {} risk_score={} 超过阈值 {}",
                    context.getResourceType(), resourceContext.getResourceName(),
                    report.getRiskScore(), scanOptions.getRiskScoreThreshold());
                return PublishPipelineResult.reject(buildRejectMessage(report, scanOptions),
                    PublishPipelineMessageType.MARKDOWN,
                    List.of(new Checkpoint(CHECKPOINT_RISK_SCORE, false)));
            }
            return PublishPipelineResult.pass(buildPassMessage(report, scanOptions),
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_RISK_SCORE, true)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("[SkillSpectorPipeline] 扫描被中断", e);
            return rejectRuntimeFailure("SkillSpector 扫描被中断: " + e.getMessage());
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("[SkillSpectorPipeline] 执行 SkillSpector runtime 失败, runtime={}: {}",
                scannerCommand, e.getMessage());
            return rejectRuntimeFailure("执行 SkillSpector runtime 失败: " + e.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir.toFile());
            }
        }
    }
    
    List<String> buildScanCommand(Path tempDir, Path reportPath, String scannerCommand,
        SkillSpectorScanOptions scanOptions) {
        List<String> command = new ArrayList<>();
        command.add(scannerCommand);
        command.add("scan");
        command.add(tempDir.toAbsolutePath().toString());
        command.add("--format");
        command.add("json");
        command.add("--output");
        command.add(reportPath.toAbsolutePath().toString());
        if (!scanOptions.isUseLlm()) {
            command.add("--no-llm");
        }
        return command;
    }
    
    Process startProcess(List<String> command, SkillSpectorScanOptions scanOptions)
        throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();
        scanOptions.applyLlmEnvironment(env);
        pb.redirectErrorStream(true);
        return pb.start();
    }
    
    int waitForProcess(Process process) throws InterruptedException {
        return process.waitFor();
    }
    
    SkillSpectorScanReport parseReport(Path reportPath) throws IOException {
        return SkillSpectorScanReport.parse(Files.readString(reportPath, StandardCharsets.UTF_8));
    }
    
    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
    
    private void logScanOutput(PublishPipelineContext context,
        ResourceFilesPipelineContext resourceContext, int exitCode, String scanOutput) {
        if (!isNotBlank(scanOutput)) {
            return;
        }
        if (exitCode == 0) {
            LOGGER.info("[SkillSpectorPipeline] SkillSpector runtime 输出, resourceType={}, "
                + "resourceName={}, exitCode={}:\n{}", context.getResourceType(),
                resourceContext.getResourceName(), exitCode, scanOutput);
            return;
        }
        LOGGER.warn("[SkillSpectorPipeline] SkillSpector runtime 输出, resourceType={}, "
            + "resourceName={}, exitCode={}:\n{}", context.getResourceType(),
            resourceContext.getResourceName(), exitCode, scanOutput);
    }
    
    private PublishPipelineResult rejectRuntimeFailure(String message) {
        return PublishPipelineResult.reject(message, PublishPipelineMessageType.MARKDOWN,
            List.of(new Checkpoint(CHECKPOINT_RUNTIME, false)));
    }
    
    private String buildPassMessage(SkillSpectorScanReport report,
        SkillSpectorScanOptions scanOptions) {
        return "SkillSpector 扫描通过，risk_score=" + report.getRiskScore()
            + "，阈值=" + scanOptions.getRiskScoreThreshold()
            + "，风险等级=" + report.getRiskSeverity()
            + "，问题数=" + report.getIssueCount()
            + buildFindingsMessage(report, scanOptions);
    }
    
    private String buildRejectMessage(SkillSpectorScanReport report,
        SkillSpectorScanOptions scanOptions) {
        return "SkillSpector 检测到安全风险，发布被拒绝。\n\n"
            + "- risk_score: " + report.getRiskScore() + "\n"
            + "- threshold: " + scanOptions.getRiskScoreThreshold() + "\n"
            + "- severity: " + report.getRiskSeverity() + "\n"
            + "- recommendation: " + report.getRiskRecommendation() + "\n"
            + "- issues: " + report.getIssueCount()
            + buildFindingsMessage(report, scanOptions);
    }
    
    private String buildFindingsMessage(SkillSpectorScanReport report,
        SkillSpectorScanOptions scanOptions) {
        if (report.getIssueCount() == 0) {
            return "";
        }
        List<SkillSpectorScanReport.Finding> findings = report.getFindings();
        StringBuilder builder = new StringBuilder("\n\n## 扫描结果\n\n");
        int limit = Math.min(findings.size(), scanOptions.getMaxFindings());
        for (int i = 0; i < limit; i++) {
            appendFinding(builder, i + 1, findings.get(i));
        }
        if (findings.size() > limit) {
            builder.append("\n仅展示前 ").append(limit).append(" 条，共 ")
                .append(findings.size()).append(" 条问题。");
        }
        return builder.toString();
    }
    
    private void appendFinding(StringBuilder builder, int index,
        SkillSpectorScanReport.Finding finding) {
        builder.append(index).append(". **")
            .append(defaultText(finding.getSeverity(), "UNKNOWN"))
            .append(" / ")
            .append(defaultText(finding.getId(), "UNKNOWN"))
            .append("**");
        if (isNotBlank(finding.getCategory())) {
            builder.append(" - ").append(compact(finding.getCategory()));
        }
        builder.append("\n");
        String location = formatLocation(finding);
        if (isNotBlank(location)) {
            builder.append("   - 位置: `").append(location).append("`\n");
        }
        appendFindingField(builder, "说明", finding.getExplanation());
        appendFindingField(builder, "修复建议", finding.getRemediation());
        appendFindingField(builder, "代码片段", finding.getCodeSnippet());
    }
    
    private void appendFindingField(StringBuilder builder, String name, String value) {
        if (isNotBlank(value)) {
            builder.append("   - ").append(name).append(": ")
                .append(compact(value)).append("\n");
        }
    }
    
    private String formatLocation(SkillSpectorScanReport.Finding finding) {
        if (!isNotBlank(finding.getFile())) {
            return null;
        }
        if (finding.getStartLine() <= 0) {
            return finding.getFile();
        }
        if (finding.getEndLine() > 0 && finding.getEndLine() != finding.getStartLine()) {
            return finding.getFile() + ":" + finding.getStartLine() + "-"
                + finding.getEndLine();
        }
        return finding.getFile() + ":" + finding.getStartLine();
    }
    
    private String defaultText(String value, String defaultValue) {
        return isNotBlank(value) ? compact(value) : defaultValue;
    }
    
    private String compact(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= MAX_FIELD_LENGTH) {
            return compact;
        }
        return compact.substring(0, MAX_FIELD_LENGTH) + "...";
    }
    
    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
    
}
