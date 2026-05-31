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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Builder for {@link SkillScannerPipelineService}. Checks if skill-scanner is installed
 * during initialization and logs installation instructions if not found.
 *
 * <p>Optional node properties (via {@code nacos.plugin.ai-pipeline.skill-scanner.*}):</p>
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
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(SkillScannerPipelineServiceBuilder.class);
    
    /**
     * Property key to override the scanner executable path or command.
     */
    private static final String PROPERTY_COMMAND = "command";
    
    /**
     * Legacy alias for scanner executable path.
     */
    private static final String PROPERTY_EXECUTABLE = "executable";
    
    /**
     * Legacy alias for scanner executable path.
     */
    private static final String PROPERTY_PATH = "path";
    
    @Override
    public String pipelineId() {
        return "skill-scanner";
    }
    
    @Override
    public PublishPipelineService build(Properties properties) {
        SkillScannerScanOptions scanOptions = SkillScannerScanOptions.fromProperties(properties);
        String resolvedCommand = resolveSkillScannerCommand(properties);
        if (StringUtils.isBlank(resolvedCommand)) {
            LOGGER.warn("[SkillScannerPipeline] skill-scanner 未安装，插件将拒绝发布。{}",
                SkillScannerPipelineService.INSTALLATION_HINT);
        } else {
            if (scanOptions.isUseLlm()) {
                LOGGER.info(
                    "[SkillScannerPipeline] skill-scanner 已就绪，已启用 LLM 语义分析（--use-llm），command={}",
                    resolvedCommand);
            } else {
                LOGGER.info("[SkillScannerPipeline] skill-scanner 已就绪，插件已加载（静态扫描），command={}",
                    resolvedCommand);
            }
        }
        return new SkillScannerPipelineService(resolvedCommand, scanOptions);
    }
    
    /**
     * Resolve skill-scanner executable path from properties or PATH.
     *
     * @param properties pipeline node properties
     * @return resolved command path, or {@code null} if not found
     */
    private String resolveSkillScannerCommand(Properties properties) {
        for (String configured : getConfiguredCandidates(properties)) {
            String resolved = resolveCandidate(configured);
            if (StringUtils.isNotBlank(resolved)) {
                return resolved;
            }
        }
        
        return resolveCandidate(SkillScannerPipelineService.DEFAULT_SKILL_SCANNER_CMD);
    }
    
    private List<String> getConfiguredCandidates(Properties properties) {
        Set<String> result = new LinkedHashSet<>();
        addConfiguredCandidate(result, properties.getProperty(PROPERTY_COMMAND));
        addConfiguredCandidate(result, properties.getProperty(PROPERTY_EXECUTABLE));
        addConfiguredCandidate(result, properties.getProperty(PROPERTY_PATH));
        return new ArrayList<>(result);
    }
    
    private void addConfiguredCandidate(Set<String> candidates, String value) {
        if (StringUtils.isNotBlank(value)) {
            candidates.add(value.trim());
        }
    }
    
    private String resolveCandidate(String candidate) {
        if (StringUtils.isBlank(candidate)) {
            return null;
        }
        
        String expanded = expandHome(candidate.trim());
        if (containsPathSeparator(expanded)) {
            Path path = Paths.get(expanded).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                return path.toString();
            }
            LOGGER.debug("[SkillScannerPipeline] skill-scanner 路径不存在或不可执行: {}", path);
            return null;
        }
        
        String pathResolved = findExecutableInPath(expanded);
        if (StringUtils.isNotBlank(pathResolved)) {
            return pathResolved;
        }
        
        LOGGER.debug("[SkillScannerPipeline] 在 PATH 中未找到命令: {}", expanded);
        return null;
    }
    
    private String findExecutableInPath(String command) {
        String pathEnv = getPathEnv();
        if (StringUtils.isBlank(pathEnv)) {
            return null;
        }
        
        String userHome = System.getProperty("user.home", "");
        Set<String> directories = new LinkedHashSet<>();
        for (String each : pathEnv.split(File.pathSeparator)) {
            if (StringUtils.isNotBlank(each)) {
                directories.add(each.trim());
            }
        }
        if (StringUtils.isNotBlank(userHome)) {
            directories.add(Paths.get(userHome, ".local", "bin").toString());
        }
        
        for (String each : directories) {
            Path candidate = Paths.get(expandHome(each), command).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }
    
    private boolean containsPathSeparator(String candidate) {
        return candidate.contains(File.separator) || candidate.contains("/")
            || candidate.contains("\\");
    }
    
    private String expandHome(String candidate) {
        if (candidate.startsWith("~/")) {
            String userHome = System.getProperty("user.home", "");
            if (StringUtils.isNotBlank(userHome)) {
                return Paths.get(userHome, candidate.substring(2)).toString();
            }
        }
        return candidate;
    }
    
    String getPathEnv() {
        return System.getenv("PATH");
    }
}
