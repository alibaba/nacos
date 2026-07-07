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
 * Builder for {@link SkillSpectorPipelineService}.
 *
 * @author nacos
 */
public class SkillSpectorPipelineServiceBuilder implements PublishPipelineServiceBuilder {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SkillSpectorPipelineServiceBuilder.class);

    private static final String PROPERTY_COMMAND = "command";

    private static final String PROPERTY_EXECUTABLE = "executable";

    private static final String PROPERTY_PATH = "path";

    @Override
    public String pipelineId() {
        return SkillSpectorPipelineService.PIPELINE_ID;
    }

    @Override
    public PublishPipelineService build(Properties properties) {
        SkillSpectorScanOptions scanOptions = SkillSpectorScanOptions.fromProperties(properties);
        String resolvedCommand = resolveSkillSpectorCommand(properties);
        if (StringUtils.isBlank(resolvedCommand)) {
            LOGGER.warn("[SkillSpectorPipeline] SkillSpector runtime 未安装，插件将拒绝发布。{}",
                    SkillSpectorPipelineService.INSTALLATION_HINT);
        } else {
            LOGGER.info("[SkillSpectorPipeline] SkillSpector runtime 已就绪，runtime={}",
                    resolvedCommand);
        }
        return new SkillSpectorPipelineService(resolvedCommand, scanOptions);
    }

    private String resolveSkillSpectorCommand(Properties properties) {
        for (String configured : getConfiguredCandidates(properties)) {
            String resolved = resolveCandidate(configured);
            if (StringUtils.isNotBlank(resolved)) {
                return resolved;
            }
        }
        return null;
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
        if (!containsPathSeparator(expanded)) {
            String pathResolved = findExecutableInPath(expanded);
            if (StringUtils.isNotBlank(pathResolved)) {
                return pathResolved;
            }
            LOGGER.debug("[SkillSpectorPipeline] 在 PATH 中未找到命令: {}", expanded);
            return null;
        }

        Path path = Paths.get(expanded).toAbsolutePath().normalize();
        if (Files.isRegularFile(path) && Files.isExecutable(path)) {
            return path.toString();
        }
        LOGGER.debug("[SkillSpectorPipeline] SkillSpector 路径不存在或不可执行: {}", path);
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
            Path path = Paths.get(expandHome(each), command).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                return path.toString();
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
