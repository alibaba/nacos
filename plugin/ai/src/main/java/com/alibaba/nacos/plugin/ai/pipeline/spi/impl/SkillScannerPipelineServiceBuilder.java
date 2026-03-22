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

import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Builder for {@link SkillScannerPipelineService}. Checks if skill-scanner is installed
 * during initialization and logs installation instructions if not found.
 *
 * <p>Optional node properties (via {@code nacos.ai.pipeline.node.skill-scanner.*}):</p>
 * <ul>
 *   <li>{@code useLlm} — {@code true} to pass {@code --use-llm} (semantic analysis; requires API key in properties or parent env)</li>
 *   <li>{@code llmApiKey} — sets subprocess {@code SKILL_SCANNER_LLM_API_KEY}</li>
 *   <li>{@code llmModel} — sets subprocess {@code SKILL_SCANNER_LLM_MODEL}</li>
 *   <li>{@code llmProvider} — {@code anthropic} or {@code openai} for {@code --llm-provider}</li>
 *   <li>{@code enableMeta} — {@code true} to pass {@code --enable-meta}</li>
 * </ul>
 *
 * @author qiacheng.cxy
 */
public class SkillScannerPipelineServiceBuilder implements PublishPipelineServiceBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillScannerPipelineServiceBuilder.class);

    /**
     * Timeout in seconds for checking skill-scanner availability.
     */
    private static final int CHECK_TIMEOUT_SECONDS = 5;

    @Override
    public String pipelineId() {
        return "skill-scanner";
    }

    @Override
    public PublishPipelineService build(Properties properties) {
        boolean installed = checkSkillScannerInstalled();
        SkillScannerScanOptions scanOptions = SkillScannerScanOptions.fromProperties(properties);
        if (!installed) {
            LOGGER.warn("[SkillScannerPipeline] skill-scanner 未安装，插件将拒绝发布。{}", SkillScannerPipelineService.INSTALLATION_HINT);
        } else {
            if (scanOptions.isUseLlm()) {
                LOGGER.info("[SkillScannerPipeline] skill-scanner 已就绪，已启用 LLM 语义分析（--use-llm）");
            } else {
                LOGGER.info("[SkillScannerPipeline] skill-scanner 已就绪，插件已加载（静态扫描）");
            }
        }
        return new SkillScannerPipelineService(installed, scanOptions);
    }

    /**
     * Check if skill-scanner CLI is available in the system PATH.
     *
     * @return true if skill-scanner is installed and executable
     */
    private boolean checkSkillScannerInstalled() {
        ProcessBuilder pb = new ProcessBuilder("skill-scanner", "--version");
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            boolean finished = process.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            LOGGER.debug("[SkillScannerPipeline] skill-scanner 环境检查失败: {}", e.getMessage());
            return false;
        }
    }
}
