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
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Common configuration lifecycle for command-backed publish pipeline services.
 *
 * @param <T> immutable command options type
 * @author Nacos
 */
abstract class AbstractCommandPipelineService<T> implements PublishPipelineService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AbstractCommandPipelineService.class);
    
    private static final String ORDER_KEY = "order";
    
    private final Function<String, String> commandResolver;
    
    private volatile RuntimeContext<T> runtime;
    
    AbstractCommandPipelineService(PipelineServiceConfig<T> initialConfig,
        Function<String, String> commandResolver) {
        this.commandResolver = Objects.requireNonNull(commandResolver,
            "Command resolver cannot be null");
        this.runtime = RuntimeContext.uninitialized(initialConfig);
    }
    
    AbstractCommandPipelineService(String command, T options, int order,
        Function<String, String> commandResolver) {
        this.commandResolver = Objects.requireNonNull(commandResolver,
            "Command resolver cannot be null");
        this.runtime = RuntimeContext.direct(command, options, order);
    }
    
    protected final void initializeRuntime() {
        runtime = buildRuntime(runtime.config);
    }
    
    @Override
    public final synchronized void applyConfig(Map<String, String> config) {
        runtime = buildRuntime(parseConfig(config));
    }
    
    @Override
    public final Map<String, String> getCurrentConfig() {
        return new LinkedHashMap<>(runtime.config.values);
    }
    
    @Override
    public final int getPreferOrder() {
        return runtime.config.order;
    }
    
    @Override
    public final PublishPipelineResourceType[] pipelineResourceTypes() {
        return new PublishPipelineResourceType[] {
            PublishPipelineResourceType.SKILL,
            PublishPipelineResourceType.AGENTSPEC,
            PublishPipelineResourceType.PROMPT
        };
    }
    
    protected final String getRuntimeCommand() {
        return runtime.command;
    }
    
    protected final T getRuntimeOptions() {
        return runtime.config.options;
    }
    
    protected final void writeResourceFiles(Path baseDir, List<ResourceFileContent> files)
        throws IOException {
        for (ResourceFileContent file : files) {
            String filePath = file.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                continue;
            }
            Path targetPath = baseDir.resolve(filePath).normalize();
            if (!targetPath.startsWith(baseDir)) {
                LOGGER.warn("[{}] 跳过非法路径: {}", pipelineId(), filePath);
                continue;
            }
            Files.createDirectories(targetPath.getParent());
            String content = file.getContent();
            Files.writeString(targetPath, content != null ? content : "", StandardCharsets.UTF_8);
        }
    }
    
    protected final List<ResourceFileContent> normalizeFilesForScanner(
        PublishPipelineContext context, List<ResourceFileContent> files, String scannerName) {
        if (containsSkillMarkdown(files)) {
            return files;
        }
        if (context.getResourceType() == PublishPipelineResourceType.AGENTSPEC) {
            return prependSkillMarkdown("AgentSpec", context, files, scannerName);
        }
        if (context.getResourceType() == PublishPipelineResourceType.PROMPT) {
            return prependSkillMarkdown("Prompt", context, files, scannerName);
        }
        return files;
    }
    
    protected final void deleteRecursively(File file) {
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
            LOGGER.debug("[{}] 无法删除临时文件: {}", pipelineId(), file.getAbsolutePath());
        }
    }
    
    protected abstract PipelineServiceConfig<T> parseConfig(Map<String, String> config);
    
    protected abstract void logRuntimeStatus(String resolvedCommand, T options);
    
    protected static ConfigItemDefinition restartDefinition(String key, String name,
        ConfigItemType type, String defaultValue, String description, String... aliases) {
        return new ConfigItemDefinition.Builder(key, name, type).description(description)
            .defaultValue(defaultValue).aliases(Arrays.asList(aliases))
            .effectMode(ConfigItemEffectMode.RESTART).build();
    }
    
    private RuntimeContext<T> buildRuntime(PipelineServiceConfig<T> config) {
        RuntimeContext<T> previous = runtime;
        if (previous.initialized && hasSameResourceConfig(previous.config.values, config.values)) {
            return new RuntimeContext<>(config, previous.command, true);
        }
        String resolvedCommand = commandResolver.apply(config.command);
        logRuntimeStatus(resolvedCommand, config.options);
        return new RuntimeContext<>(config, resolvedCommand, true);
    }
    
    private boolean hasSameResourceConfig(Map<String, String> current,
        Map<String, String> updated) {
        Map<String, String> currentResourceConfig = new LinkedHashMap<>(current);
        Map<String, String> updatedResourceConfig = new LinkedHashMap<>(updated);
        currentResourceConfig.remove(ORDER_KEY);
        updatedResourceConfig.remove(ORDER_KEY);
        return currentResourceConfig.equals(updatedResourceConfig);
    }
    
    private boolean containsSkillMarkdown(List<ResourceFileContent> files) {
        for (ResourceFileContent each : files) {
            if (each != null && "SKILL.md".equals(each.getFilePath())) {
                return true;
            }
        }
        return false;
    }
    
    private List<ResourceFileContent> prependSkillMarkdown(String resourceType,
        PublishPipelineContext context, List<ResourceFileContent> files, String scannerName) {
        List<ResourceFileContent> result = new ArrayList<>(files.size() + 1);
        result.add(new ResourceFileContent("SKILL.md",
            buildWrappedSkillMarkdown(resourceType, context, files, scannerName)));
        result.addAll(files);
        return result;
    }
    
    private String buildWrappedSkillMarkdown(String resourceType, PublishPipelineContext context,
        List<ResourceFileContent> files, String scannerName) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(resourceType).append(" ").append(context.getResourceName())
            .append("\n\n");
        builder.append("Generated from ").append(resourceType)
            .append(" pipeline context for ").append(scannerName).append(" compatibility.\n");
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
    
    protected static final class PipelineServiceConfig<T> {
        
        private final Map<String, String> values;
        
        private final String command;
        
        private final T options;
        
        private final int order;
        
        PipelineServiceConfig(Map<String, String> values, String command, T options, int order) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.command = command;
            this.options = options;
            this.order = order;
        }
    }
    
    private static final class RuntimeContext<T> {
        
        private final PipelineServiceConfig<T> config;
        
        private final String command;
        
        private final boolean initialized;
        
        private RuntimeContext(PipelineServiceConfig<T> config, String command,
            boolean initialized) {
            this.config = config;
            this.command = command;
            this.initialized = initialized;
        }
        
        private static <T> RuntimeContext<T> direct(String command, T options, int order) {
            PipelineServiceConfig<T> config = new PipelineServiceConfig<>(
                Collections.emptyMap(), command, options, order);
            return new RuntimeContext<>(config, command, true);
        }
        
        private static <T> RuntimeContext<T> uninitialized(PipelineServiceConfig<T> config) {
            return new RuntimeContext<>(config, null, false);
        }
    }
}
