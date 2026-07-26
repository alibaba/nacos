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
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Publish pipeline service that integrates Cisco AI Defense skill-scanner for security scanning
 * of AI Agent Skills before publishing.
 *
 * <p>Uses <a href="https://github.com/cisco-ai-defense/skill-scanner">skill-scanner</a> to detect prompt
 * injection, data exfiltration, and malicious code patterns. Optional LLM semantic analysis via
 * plugin config item {@code use-llm=true} and {@code llm-api-key}/{@code llm-model} (mapped to
 * {@code SKILL_SCANNER_LLM_*} in the subprocess environment). Rejects publishing if HIGH/CRITICAL
 * findings are detected.</p>
 *
 * <p>CLI uses {@code --format markdown --detailed} so stdout matches Cisco skill-scanner report
 * formats documented in the upstream project.</p>
 *
 * @author qiacheng.cxy
 */
public class SkillScannerPipelineService
    extends AbstractCommandPipelineService<SkillScannerScanOptions> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillScannerPipelineService.class);
    
    /**
     * skill-scanner CLI command name.
     */
    static final String DEFAULT_SKILL_SCANNER_CMD = SkillScannerPluginConfig.DEFAULT_COMMAND;
    
    private static final List<ConfigItemDefinition> CONFIG_DEFINITIONS = buildConfigDefinitions();
    
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
    
    public SkillScannerPipelineService() {
        this(SkillScannerCommandResolver::resolve);
    }
    
    SkillScannerPipelineService(Function<String, String> commandResolver) {
        super(toPipelineServiceConfig(
            SkillScannerPluginConfig.fromMap(Collections.emptyMap())), commandResolver);
    }
    
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
        super(scannerCommand, scanOptions != null ? scanOptions : SkillScannerScanOptions.none(),
            SkillScannerPluginConfig.DEFAULT_ORDER, SkillScannerCommandResolver::resolve);
    }
    
    SkillScannerPipelineService(SkillScannerPluginConfig config,
        Function<String, String> commandResolver) {
        super(toPipelineServiceConfig(config), commandResolver);
        initializeRuntime();
    }
    
    private static PipelineServiceConfig<SkillScannerScanOptions> toPipelineServiceConfig(
        SkillScannerPluginConfig config) {
        return new PipelineServiceConfig<>(config.toMap(), config.getCommand(),
            config.getScanOptions(), config.getOrder());
    }
    
    private static List<ConfigItemDefinition> buildConfigDefinitions() {
        ConfigItemDefinition order = new ConfigItemDefinition.Builder(
            SkillScannerPluginConfig.ORDER, "Execution order", ConfigItemType.NUMBER)
            .description("Pipeline execution order; lower values execute first")
            .defaultValue(Integer.toString(SkillScannerPluginConfig.DEFAULT_ORDER))
            .effectMode(ConfigItemEffectMode.RUNTIME).build();
        ConfigItemDefinition command = restartDefinition(SkillScannerPluginConfig.COMMAND,
            "Skill scanner command", ConfigItemType.STRING,
            DEFAULT_SKILL_SCANNER_CMD,
            "CLI command or executable path resolved during server startup",
            SkillScannerPluginConfig.COMMAND_ALIAS_EXECUTABLE,
            SkillScannerPluginConfig.COMMAND_ALIAS_PATH);
        ConfigItemDefinition useLlm = restartDefinition(SkillScannerPluginConfig.USE_LLM,
            "Use LLM analysis", ConfigItemType.BOOLEAN, Boolean.FALSE.toString(),
            "Enable LLM semantic analysis during skill scanning",
            SkillScannerPluginConfig.USE_LLM_ALIAS);
        ConfigItemDefinition llmApiKey = restartDefinition(
            SkillScannerPluginConfig.LLM_API_KEY, "LLM API key", ConfigItemType.STRING, "",
            "API key passed to the skill-scanner subprocess",
            SkillScannerPluginConfig.LLM_API_KEY_ALIAS);
        llmApiKey.setSensitive(true);
        ConfigItemDefinition llmModel = restartDefinition(SkillScannerPluginConfig.LLM_MODEL,
            "LLM model", ConfigItemType.STRING, "",
            "LLM model passed to the skill-scanner subprocess",
            SkillScannerPluginConfig.LLM_MODEL_ALIAS);
        ConfigItemDefinition llmProvider = restartDefinition(
            SkillScannerPluginConfig.LLM_PROVIDER, "LLM provider", ConfigItemType.STRING, "",
            "LLM provider passed to the skill-scanner CLI",
            SkillScannerPluginConfig.LLM_PROVIDER_ALIAS);
        ConfigItemDefinition enableMeta = restartDefinition(
            SkillScannerPluginConfig.ENABLE_META, "Enable meta checks", ConfigItemType.BOOLEAN,
            Boolean.FALSE.toString(), "Enable skill-scanner meta checks",
            SkillScannerPluginConfig.ENABLE_META_ALIAS);
        return Collections.unmodifiableList(Arrays.asList(order, command, useLlm, llmApiKey,
            llmModel, llmProvider, enableMeta));
    }
    
    @Override
    public String pipelineId() {
        return "skill-scanner";
    }
    
    @Override
    public List<ConfigItemDefinition> getConfigDefinitions() {
        return CONFIG_DEFINITIONS;
    }
    
    @Override
    protected PipelineServiceConfig<SkillScannerScanOptions> parseConfig(
        Map<String, String> config) {
        return toPipelineServiceConfig(SkillScannerPluginConfig.fromMap(config));
    }
    
    @Override
    protected void logRuntimeStatus(String resolvedCommand, SkillScannerScanOptions options) {
        if (StringUtils.isBlank(resolvedCommand)) {
            LOGGER.warn("[SkillScannerPipeline] skill-scanner 未安装，插件将拒绝发布。{}",
                INSTALLATION_HINT);
        } else if (options.isUseLlm()) {
            LOGGER.info(
                "[SkillScannerPipeline] skill-scanner 已就绪，已启用 LLM 语义分析（--use-llm），command={}",
                resolvedCommand);
        } else {
            LOGGER.info(
                "[SkillScannerPipeline] skill-scanner 已就绪，插件已加载（静态扫描），command={}",
                resolvedCommand);
        }
    }
    
    @Override
    public PublishPipelineResult execute(PublishPipelineContext context) {
        String scannerCommand = getRuntimeCommand();
        SkillScannerScanOptions scanOptions = getRuntimeOptions();
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
            writeResourceFiles(tempDir,
                normalizeFilesForScanner(context, files, "skill-scanner"));
            
            List<String> command = buildScanCommand(tempDir, scannerCommand, scanOptions);
            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();
            applyPythonStdoutEncoding(env);
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
        return buildScanCommand(tempDir, getRuntimeCommand(), getRuntimeOptions());
    }
    
    List<String> buildScanCommand(Path tempDir, String scannerCommand,
        SkillScannerScanOptions scanOptions) {
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
    
    private void applyPythonStdoutEncoding(Map<String, String> env) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            env.put("PYTHONIOENCODING", "utf-8");
        }
    }
    
}
