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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.plugin.ai.pipeline.model.Checkpoint;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineMessageType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.common.utils.StringUtils;
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
 * Publish pipeline service that integrates Cisco AI Defense skill-scanner for security scanning
 * of AI Agent Skills before publishing.
 *
 * <p>Uses <a href="https://github.com/cisco-ai-defense/skill-scanner">skill-scanner</a> to detect prompt
 * injection, data exfiltration, and malicious code patterns. Optional LLM semantic analysis via
 * node property {@code useLlm=true} and {@code llmApiKey}/{@code llmModel} (mapped to
 * {@code SKILL_SCANNER_LLM_*} in the subprocess environment). Rejects publishing if HIGH/CRITICAL
 * findings are detected.</p>
 *
 * <p>CLI uses {@code --format markdown --detailed} so stdout matches Cisco skill-scanner report
 * formats documented in the upstream project.</p>
 *
 * @author qiacheng.cxy
 */
public class SkillScannerPipelineService implements PublishPipelineService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillScannerPipelineService.class);
    
    /**
     * skill-scanner CLI command name.
     */
    static final String DEFAULT_SKILL_SCANNER_CMD = "skill-scanner";
    
    /**
     * Report format for subprocess stdout ({@code skill-scanner --format ...}).
     *
     * @see <a href="https://github.com/cisco-ai-defense/skill-scanner">skill-scanner</a> CLI {@code --format}
     */
    static final String SCAN_OUTPUT_FORMAT = "markdown";
    
    private static final String CHECKPOINT_AVAILABILITY = "skill-scanner 安装与可用性";
    
    private static final String CHECKPOINT_APPLICABILITY = "skill-scanner 扫描适用性";
    
    private static final String CHECKPOINT_CLI = "skill-scanner CLI 执行";
    
    /**
     * Installation hint when skill-scanner is not found.
     */
    static final String INSTALLATION_HINT =
        "skill-scanner 未安装。请先安装 Cisco AI skill-scanner 后再使用此插件。\n"
            + "安装命令（任选其一）：\n"
            + "  # 使用 uv（推荐）\n"
            + "  uv pip install cisco-ai-skill-scanner\n"
            + "  # 使用 pip\n"
            + "  pip install cisco-ai-skill-scanner";
    
    private final String scannerCommand;
    
    private final SkillScannerScanOptions scanOptions;
    
    public SkillScannerPipelineService(boolean installed) {
        this(installed ? DEFAULT_SKILL_SCANNER_CMD : null, SkillScannerScanOptions.none());
    }
    
    public SkillScannerPipelineService(String scannerCommand) {
        this(scannerCommand, SkillScannerScanOptions.none());
    }
    
    SkillScannerPipelineService(boolean installed, SkillScannerScanOptions scanOptions) {
        this(installed ? DEFAULT_SKILL_SCANNER_CMD : null, scanOptions);
    }
    
    SkillScannerPipelineService(String scannerCommand, SkillScannerScanOptions scanOptions) {
        this.scannerCommand = scannerCommand;
        this.scanOptions = scanOptions != null ? scanOptions : SkillScannerScanOptions.none();
    }
    
    @Override
    public String pipelineId() {
        return "skill-scanner";
    }
    
    @Override
    public PublishPipelineResult execute(PublishPipelineContext context) {
        if (scannerCommand == null || scannerCommand.isBlank()) {
            return PublishPipelineResult.reject(INSTALLATION_HINT,
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_AVAILABILITY, false)));
        }
        
        if (!(context instanceof ResourceFilesPipelineContext)) {
            return PublishPipelineResult.pass("资源不包含可扫描文件，跳过 skill-scanner 扫描",
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_APPLICABILITY, true)));
        }
        
        ResourceFilesPipelineContext resourceContext = (ResourceFilesPipelineContext) context;
        List<ResourceFileContent> files = resourceContext.getFiles();
        if (files == null || files.isEmpty()) {
            return PublishPipelineResult.pass("资源无文件内容，跳过扫描", PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_APPLICABILITY, true)));
        }
        
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("nacos-skill-scanner-");
            writeResourceFiles(tempDir, normalizeFilesForScanner(context, files));
            
            List<String> command = buildScanCommand(tempDir);
            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();
            scanOptions.applyLlmEnvironment(env);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = waitForProcess(process);
            
            if (exitCode == 0) {
                LOGGER.info("[SkillScannerPipeline] {} {} 扫描通过", context.getResourceType(),
                    resourceContext.getResourceName());
                return PublishPipelineResult.pass("skill-scanner 扫描通过，未发现 HIGH/CRITICAL 级别风险",
                    PublishPipelineMessageType.MARKDOWN,
                    SkillScannerMarkdownFindingParser.buildPassCheckpoints(scanOptions));
            } else {
                String scanOutput = output.toString();
                LOGGER.warn(
                    "[SkillScannerPipeline] {} {} 扫描发现风险, command={}, exitCode={}, output={} ",
                    context.getResourceType(), resourceContext.getResourceName(), scannerCommand,
                    exitCode,
                    scanOutput);
                return PublishPipelineResult.reject(
                    "skill-scanner 检测到安全风险（HIGH/CRITICAL 级别），发布被拒绝。\n扫描结果:\n" + scanOutput,
                    PublishPipelineMessageType.MARKDOWN,
                    SkillScannerMarkdownFindingParser.buildRejectCheckpoints(scanOutput));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("[SkillScannerPipeline] 扫描被中断", e);
            return PublishPipelineResult.reject("skill-scanner 扫描被中断: " + e.getMessage(),
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_CLI, false)));
        } catch (IOException e) {
            LOGGER.warn("[SkillScannerPipeline] 执行 skill-scanner 失败, command={}: {}",
                scannerCommand, e.getMessage());
            return PublishPipelineResult.reject("执行 skill-scanner 失败: " + e.getMessage(),
                PublishPipelineMessageType.MARKDOWN,
                List.of(new Checkpoint(CHECKPOINT_CLI, false)));
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir.toFile());
            }
        }
    }
    
    List<String> buildScanCommand(Path tempDir) {
        List<String> command = new ArrayList<>();
        command.add(scannerCommand);
        command.add("scan");
        command.add(tempDir.toAbsolutePath().toString());
        command.add("--fail-on-severity");
        command.add("high");
        command.add("--lenient");
        command.add("--format");
        command.add(SCAN_OUTPUT_FORMAT);
        command.add("--detailed");
        if (scanOptions.isUseLlm()) {
            command.add("--use-llm");
            if (StringUtils.isNotBlank(scanOptions.getLlmProvider())) {
                command.add("--llm-provider");
                command.add(scanOptions.getLlmProvider());
            }
        }
        if (scanOptions.isEnableMeta()) {
            command.add("--enable-meta");
        }
        return command;
    }
    
    int waitForProcess(Process process) throws InterruptedException {
        return process.waitFor();
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
                LOGGER.warn("[SkillScannerPipeline] 跳过非法路径: {}", filePath);
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
            result.add(
                new ResourceFileContent("SKILL.md", buildAgentSpecSkillMarkdown(context, files)));
            result.addAll(files);
            return result;
        }
        
        if (context.getResourceType() == PublishPipelineResourceType.PROMPT) {
            List<ResourceFileContent> result = new ArrayList<>(files.size() + 1);
            result
                .add(new ResourceFileContent("SKILL.md", buildPromptSkillMarkdown(context, files)));
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
    
    private String buildAgentSpecSkillMarkdown(PublishPipelineContext context,
        List<ResourceFileContent> files) {
        StringBuilder builder = new StringBuilder();
        builder.append("# AgentSpec ").append(context.getResourceName()).append("\n\n");
        builder
            .append("Generated from AgentSpec pipeline context for skill-scanner compatibility.\n");
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
    
    private String buildPromptSkillMarkdown(PublishPipelineContext context,
        List<ResourceFileContent> files) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Prompt ").append(context.getResourceName()).append("\n\n");
        builder.append("Generated from Prompt pipeline context for skill-scanner compatibility.\n");
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
            LOGGER.debug("[SkillScannerPipeline] 无法删除临时文件: {}", file.getAbsolutePath());
        }
    }
    
    @Override
    public int getPreferOrder() {
        return 100;
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
