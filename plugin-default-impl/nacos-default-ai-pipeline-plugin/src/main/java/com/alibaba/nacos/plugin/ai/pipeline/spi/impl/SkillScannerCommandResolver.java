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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves the configured skill-scanner command against the server environment.
 *
 * @author Nacos
 */
final class SkillScannerCommandResolver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillScannerCommandResolver.class);
    
    private SkillScannerCommandResolver() {
    }
    
    static String resolve(String configuredCommand) {
        return resolve(configuredCommand, System.getenv("PATH"),
            System.getProperty("user.home", ""));
    }
    
    static String resolve(String configuredCommand, String pathEnv, String userHome) {
        String command = StringUtils.isBlank(configuredCommand)
            ? SkillScannerPluginConfig.DEFAULT_COMMAND : configuredCommand.trim();
        String resolved = resolveCandidate(command, pathEnv, userHome);
        if (StringUtils.isNotBlank(resolved)
            || SkillScannerPluginConfig.DEFAULT_COMMAND.equals(command)) {
            return resolved;
        }
        return resolveCandidate(SkillScannerPluginConfig.DEFAULT_COMMAND, pathEnv, userHome);
    }
    
    private static String resolveCandidate(String candidate, String pathEnv, String userHome) {
        String expanded = expandHome(candidate, userHome);
        if (containsPathSeparator(expanded)) {
            Path path = Paths.get(expanded).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                return path.toString();
            }
            LOGGER.debug("[SkillScannerPipeline] skill-scanner 路径不存在或不可执行: {}", path);
            return null;
        }
        String resolved = findExecutableInPath(expanded, pathEnv, userHome);
        if (StringUtils.isBlank(resolved)) {
            LOGGER.debug("[SkillScannerPipeline] 在 PATH 中未找到命令: {}", expanded);
        }
        return resolved;
    }
    
    private static String findExecutableInPath(String command, String pathEnv, String userHome) {
        if (StringUtils.isBlank(pathEnv)) {
            return null;
        }
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
            Path candidate = Paths.get(expandHome(each, userHome), command).toAbsolutePath()
                .normalize();
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }
    
    private static boolean containsPathSeparator(String candidate) {
        return candidate.contains(File.separator) || candidate.contains("/")
            || candidate.contains("\\");
    }
    
    private static String expandHome(String candidate, String userHome) {
        if (candidate.startsWith("~/") && StringUtils.isNotBlank(userHome)) {
            return Paths.get(userHome, candidate.substring(2)).toString();
        }
        return candidate;
    }
}
