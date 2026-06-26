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
    
    @Override
    public String pipelineId() {
        return SkillSpectorPipelineService.PIPELINE_ID;
    }
    
    @Override
    public PublishPipelineService build(Properties properties) {
        SkillSpectorScanOptions scanOptions = SkillSpectorScanOptions.fromProperties(properties);
        String resolvedCommand = resolveSkillSpectorCommand(properties);
        if (StringUtils.isBlank(resolvedCommand)) {
            LOGGER.warn("[SkillSpectorPipeline] SkillSpector 内置运行时不可用，插件将拒绝发布。{}",
                    SkillSpectorPipelineService.INSTALLATION_HINT);
        } else {
            LOGGER.info("[SkillSpectorPipeline] SkillSpector 内置运行时已就绪，runtime={}",
                    resolvedCommand);
        }
        return new SkillSpectorPipelineService(resolvedCommand, scanOptions);
    }
    
    private String resolveSkillSpectorCommand(Properties properties) {
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
        Path root = base.resolve("plugins").resolve("ai-pipeline").resolve("skillspector");
        candidates.add(root.resolve("bin").resolve(executableName("skillspector")));
        candidates.add(root.resolve(executableName("skillspector-runner")));
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
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return baseName + ".exe";
        }
        return baseName;
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
