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

import com.alibaba.nacos.plugin.ai.pipeline.model.Checkpoint;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineMessageType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Publish pipeline service that integrates SkillSpector for AI resource security scanning.
 *
 * @author nacos
 */
public class SkillSpectorPipelineService implements PublishPipelineService {
    
    static final String PIPELINE_ID = "skill-spector";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillSpectorPipelineService.class);
    
    private static final String CHECKPOINT_AVAILABILITY = "SkillSpector runtime 安装状态";
    
    private static final String CHECKPOINT_APPLICABILITY = "SkillSpector 扫描适用性";
    
    private static final String CHECKPOINT_RUNTIME = "SkillSpector 扫描运行时";
    
    private static final String CHECKPOINT_RISK_SCORE = "SkillSpector risk_score 阈值";
    
    private static final int MAX_FIELD_LENGTH = 240;
    
    static final String INSTALLATION_HINT =
            "SkillSpector runtime 未安装。请先通过 nacos-setup skill-spector install 安装，"
                    + "或手动解压 runtime 到 "
                    + "runtimes/ai-pipeline/skill-spector/runtime/<os-arch>/。";
    
    private final String scannerCommand;
    
    private final SkillSpectorScanOptions scanOptions;
    
    SkillSpectorPipelineService(String scannerCommand, SkillSpectorScanOptions scanOptions) {
        this.scannerCommand = scannerCommand;
        this.scanOptions = scanOptions != null ? scanOptions : SkillSpectorScanOptions.none();
    }
    
    @Override
    public String pipelineId() {
        return PIPELINE_ID;
    }
    
    @Override
    public PublishPipelineResult execute(PublishPipelineContext context) {
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
            writeResourceFiles(tempDir, normalizeFilesForScanner(context, files));
            Path reportPath = tempDir.resolve("skillspector-report.json");
            
            Process process = startProcess(buildScanCommand(tempDir, reportPath));
            String scanOutput = readOutput(process);
            int exitCode = waitForProcess(process);
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
                return PublishPipelineResult.reject(buildRejectMessage(report),
                        PublishPipelineMessageType.MARKDOWN,
                        List.of(new Checkpoint(CHECKPOINT_RISK_SCORE, false)));
            }
            return PublishPipelineResult.pass(buildPassMessage(report),
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
    
    List<String> buildScanCommand(Path tempDir, Path reportPath) {
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
    
    Process startProcess(List<String> command) throws IOException {
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
    
    private PublishPipelineResult rejectRuntimeFailure(String message) {
        return PublishPipelineResult.reject(message, PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_RUNTIME, false)));
    }
    
    private String buildPassMessage(SkillSpectorScanReport report) {
        return "SkillSpector 扫描通过，risk_score=" + report.getRiskScore()
                + "，阈值=" + scanOptions.getRiskScoreThreshold()
                + "，风险等级=" + report.getRiskSeverity()
                + "，问题数=" + report.getIssueCount()
                + buildFindingsMessage(report);
    }
    
    private String buildRejectMessage(SkillSpectorScanReport report) {
        return "SkillSpector 检测到安全风险，发布被拒绝。\n\n"
                + "- risk_score: " + report.getRiskScore() + "\n"
                + "- threshold: " + scanOptions.getRiskScoreThreshold() + "\n"
                + "- severity: " + report.getRiskSeverity() + "\n"
                + "- recommendation: " + report.getRiskRecommendation() + "\n"
                + "- issues: " + report.getIssueCount()
                + buildFindingsMessage(report);
    }
    
    private String buildFindingsMessage(SkillSpectorScanReport report) {
        if (report.getIssueCount() == 0) {
            return "";
        }
        List<SkillSpectorScanReport.Finding> findings = report.getFindings();
        if (findings.isEmpty()) {
            return "\n\n## 扫描结果\n\nSkillSpector 返回了 " + report.getIssueCount()
                    + " 个问题，但报告中没有包含可展示的明细。";
        }
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
    
    private void writeResourceFiles(Path baseDir, List<ResourceFileContent> files)
            throws IOException {
        for (ResourceFileContent file : files) {
            String filePath = file.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                continue;
            }
            Path targetPath = baseDir.resolve(filePath).normalize();
            if (!targetPath.startsWith(baseDir)) {
                LOGGER.warn("[SkillSpectorPipeline] 跳过非法路径: {}", filePath);
                continue;
            }
            Files.createDirectories(targetPath.getParent());
            String content = file.getContent();
            Files.writeString(targetPath, content != null ? content : "", StandardCharsets.UTF_8);
        }
    }
    
    private List<ResourceFileContent> normalizeFilesForScanner(PublishPipelineContext context,
            List<ResourceFileContent> files) {
        if (containsSkillMarkdown(files)) {
            return files;
        }
        if (context.getResourceType() == PublishPipelineResourceType.AGENTSPEC) {
            List<ResourceFileContent> result = new ArrayList<>(files.size() + 1);
            result.add(new ResourceFileContent("SKILL.md",
                    buildWrappedSkillMarkdown("AgentSpec", context, files)));
            result.addAll(files);
            return result;
        }
        if (context.getResourceType() == PublishPipelineResourceType.PROMPT) {
            List<ResourceFileContent> result = new ArrayList<>(files.size() + 1);
            result.add(new ResourceFileContent("SKILL.md",
                    buildWrappedSkillMarkdown("Prompt", context, files)));
            result.addAll(files);
            return result;
        }
        return files;
    }
    
    private boolean containsSkillMarkdown(List<ResourceFileContent> files) {
        for (ResourceFileContent each : files) {
            if (each != null && "SKILL.md".equals(each.getFilePath())) {
                return true;
            }
        }
        return false;
    }
    
    private String buildWrappedSkillMarkdown(String type, PublishPipelineContext context,
            List<ResourceFileContent> files) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(type).append(" ").append(context.getResourceName())
                .append("\n\n");
        builder.append("Generated from ").append(type)
                .append(" pipeline context for SkillSpector compatibility.\n");
        for (ResourceFileContent file : files) {
            if (file == null || file.getFilePath() == null) {
                continue;
            }
            builder.append("\n## File: ").append(file.getFilePath()).append("\n\n");
            String content = file.getContent();
            if (content != null) {
                builder.append(content);
            }
            builder.append("\n");
        }
        return builder.toString();
    }
    
    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            LOGGER.debug("[SkillSpectorPipeline] 无法删除临时文件: {}", file.getAbsolutePath());
        }
    }
    
    @Override
    public int getPreferOrder() {
        return 90;
    }
    
    @Override
    public PublishPipelineResourceType[] pipelineResourceTypes() {
        return new PublishPipelineResourceType[] {
            PublishPipelineResourceType.SKILL,
            PublishPipelineResourceType.AGENTSPEC,
            PublishPipelineResourceType.PROMPT
        };
    }
}
