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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Builder for {@link SkillSpectorPipelineService}.
 *
 * @author nacos
 */
public class SkillSpectorPipelineServiceBuilder implements PublishPipelineServiceBuilder {
    
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SkillSpectorPipelineServiceBuilder.class);
    
    private static final String NACOS_HOME_PROPERTY = "nacos.home";
    
    private static final String PRIMARY_RUNTIME_DIR = "skill-spector";
    
    @Override
    public String pipelineId() {
        return SkillSpectorPipelineService.PIPELINE_ID;
    }
    
    @Override
    public PublishPipelineService build(Properties properties) {
        SkillSpectorScanOptions scanOptions = SkillSpectorScanOptions.fromProperties(properties);
        String resolvedCommand = resolveSkillSpectorCommand();
        if (StringUtils.isBlank(resolvedCommand)) {
            LOGGER.warn("[SkillSpectorPipeline] SkillSpector runtime 未安装，插件将拒绝发布。{}",
                    SkillSpectorPipelineService.INSTALLATION_HINT);
        } else {
            LOGGER.info("[SkillSpectorPipeline] SkillSpector runtime 已就绪，runtime={}",
                    resolvedCommand);
        }
        return new SkillSpectorPipelineService(resolvedCommand, scanOptions);
    }
    
    private String resolveSkillSpectorCommand() {
        for (Path candidate : getBuiltinCandidates()) {
            String resolved = resolveCandidate(candidate.toString());
            if (StringUtils.isNotBlank(resolved)) {
                return resolved;
            }
        }
        return null;
    }
    
    List<Path> getBuiltinCandidates() {
        Set<Path> candidates = new LinkedHashSet<>();
        addBuiltinCandidates(candidates, System.getProperty(NACOS_HOME_PROPERTY));
        addBuiltinCandidates(candidates, System.getProperty("user.dir"));
        return List.copyOf(candidates);
    }
    
    private void addBuiltinCandidates(Set<Path> candidates, String baseDir) {
        if (StringUtils.isBlank(baseDir)) {
            return;
        }
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path pluginRoot = base.resolve("plugins").resolve("ai-pipeline");
        addRuntimeRootCandidates(candidates, pluginRoot.resolve(PRIMARY_RUNTIME_DIR));
    }
    
    private void addRuntimeRootCandidates(Set<Path> candidates, Path root) {
        if (!hasPlatformRuntime(root)) {
            LOGGER.debug("[SkillSpectorPipeline] SkillSpector 平台 runtime 不存在或不可执行: {}",
                    root.resolve("runtime").resolve(platformKey()));
            return;
        }
        candidates.add(root.resolve("bin").resolve(executableName("skill-spector")));
    }
    
    boolean hasPlatformRuntime(Path root) {
        Path runtimeRoot = root.resolve("runtime").resolve(platformKey());
        for (Path candidate : runtimePythonCandidates(runtimeRoot)) {
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }
    
    private List<Path> runtimePythonCandidates(Path runtimeRoot) {
        if (isWindows()) {
            return List.of(
                    runtimeRoot.resolve("python").resolve("python.exe"),
                    runtimeRoot.resolve("python").resolve("bin").resolve("python.exe"),
                    runtimeRoot.resolve("venv").resolve("Scripts").resolve("python.exe"),
                    runtimeRoot.resolve("bin").resolve("python.exe"));
        }
        return List.of(
                runtimeRoot.resolve("python").resolve("bin").resolve("python3"),
                runtimeRoot.resolve("python").resolve("bin").resolve("python"),
                runtimeRoot.resolve("venv").resolve("bin").resolve("python3"),
                runtimeRoot.resolve("bin").resolve("python3"));
    }
    
    String platformKey() {
        return osKey() + "-" + archKey();
    }
    
    private String osKey() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "darwin";
        }
        if (osName.contains("linux")) {
            return "linux";
        }
        return osName.replaceAll("[^a-z0-9]+", "-");
    }
    
    private String archKey() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if ("amd64".equals(arch) || "x86_64".equals(arch)) {
            return "x86_64";
        }
        if ("aarch64".equals(arch) || "arm64".equals(arch)) {
            return "aarch64";
        }
        return arch.replaceAll("[^a-z0-9_]+", "-");
    }
    
    private String resolveCandidate(String candidate) {
        if (StringUtils.isBlank(candidate)) {
            return null;
        }
        String expanded = expandHome(candidate.trim());
        Path path = Paths.get(expanded).toAbsolutePath().normalize();
        if (Files.isRegularFile(path) && Files.isExecutable(path)) {
            return path.toString();
        }
        LOGGER.debug("[SkillSpectorPipeline] SkillSpector 路径不存在或不可执行: {}", path);
        return null;
    }
    
    private String executableName(String baseName) {
        if (isWindows()) {
            return baseName + ".exe";
        }
        return baseName;
    }
    
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
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
}
