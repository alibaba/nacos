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

package com.alibaba.nacos.ai.pipeline.config;

import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeConfig;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * File-based (application.properties) implementation of {@link PipelineConfigProvider}.
 *
 * <p>Reads configuration from EnvUtil with the following keys:
 * <ul>
 *   <li>{@code nacos.plugin.{pluginName}.enabled} - whether this pipeline plugin is enabled</li>
 *   <li>{@code nacos.plugin.{pluginName}.type} - property namespace under this plugin, for example {@code nacos}</li>
 *   <li>{@code nacos.plugin.{pluginName}.{type}.{key}} - individual property values passed to the builder</li>
 * </ul>
 *
 * <p>Follows the singleton pattern like PushConfig.
 *
 * @author kiro
 * @since 3.2.0
 */
public class FilePipelineConfigProvider extends AbstractDynamicConfig implements PipelineConfigProvider {
    
    private static final String CONFIG_NAME = "PipelineConfig";
    
    private static final String KEY_PLUGIN_PREFIX = "nacos.plugin.";
    
    private static final String KEY_ENABLED_SUFFIX = ".enabled";
    
    private static final String KEY_TYPE_SUFFIX = ".type";
    
    private static final FilePipelineConfigProvider INSTANCE = new FilePipelineConfigProvider();
    
    private volatile PipelineConfig currentConfig;
    
    private FilePipelineConfigProvider() {
        super(CONFIG_NAME);
        resetConfig();
    }
    
    public static FilePipelineConfigProvider getInstance() {
        return INSTANCE;
    }
    
    @Override
    public PipelineConfig getConfig() {
        return currentConfig;
    }
    
    @Override
    public String type() {
        return "file";
    }
    
    @Override
    protected void getConfigFromEnv() {
        try {
            List<PipelineNodeConfig> nodes = readEnabledPluginNodes();
            
            PipelineConfig config = new PipelineConfig();
            config.setEnabled(!nodes.isEmpty());
            config.setNodes(nodes);
            this.currentConfig = config;
        } catch (Exception e) {
            PipelineConfig defaultConfig = new PipelineConfig();
            defaultConfig.setEnabled(false);
            defaultConfig.setNodes(Collections.emptyList());
            this.currentConfig = defaultConfig;
            throw e;
        }
    }
    
    /**
     * Read enabled pipeline plugin definitions from {@code nacos.plugin.*} properties.
     *
     * <p>The plugin name is treated as the pipeline ID. Only plugins with
     * {@code nacos.plugin.{pluginName}.enabled=true} are included.
     * The property namespace is selected by {@code nacos.plugin.{pluginName}.type}, and all
     * nested keys under {@code nacos.plugin.{pluginName}.{type}.} are passed to the builder.
     *
     * @return enabled pipeline plugin configurations sorted by plugin name for deterministic behavior
     */
    private List<PipelineNodeConfig> readEnabledPluginNodes() {
        Properties allProperties = EnvUtil.getProperties();
        if (allProperties == null || allProperties.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Properties> pluginPropertyMap = new TreeMap<>();
        for (String propertyName : allProperties.stringPropertyNames()) {
            if (!propertyName.startsWith(KEY_PLUGIN_PREFIX) || !propertyName.endsWith(KEY_ENABLED_SUFFIX)) {
                continue;
            }
            String pluginName = propertyName.substring(KEY_PLUGIN_PREFIX.length(),
                    propertyName.length() - KEY_ENABLED_SUFFIX.length());
            if (StringUtils.isBlank(pluginName)) {
                continue;
            }
            boolean enabled = Boolean.parseBoolean(allProperties.getProperty(propertyName));
            if (!enabled) {
                continue;
            }
            pluginPropertyMap.put(pluginName, readNodeProperties(pluginName, allProperties));
        }
        if (pluginPropertyMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<PipelineNodeConfig> nodes = new ArrayList<>(pluginPropertyMap.size());
        pluginPropertyMap.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> {
                    PipelineNodeConfig nodeConfig = new PipelineNodeConfig();
                    nodeConfig.setPipelineId(entry.getKey());
                    nodeConfig.setProperties(entry.getValue());
                    nodes.add(nodeConfig);
                });
        return nodes;
    }
    
    private Properties readNodeProperties(String pluginName, Properties allProperties) {
        Properties properties = new Properties();
        String pluginPrefix = KEY_PLUGIN_PREFIX + pluginName;
        String type = allProperties.getProperty(pluginPrefix + KEY_TYPE_SUFFIX);
        if (StringUtils.isBlank(type)) {
            return properties;
        }
        String propertyPrefix = pluginPrefix + "." + type + ".";
        for (String propertyName : allProperties.stringPropertyNames()) {
            if (!propertyName.startsWith(propertyPrefix)) {
                continue;
            }
            String propertyKey = propertyName.substring(propertyPrefix.length());
            if (StringUtils.isNotBlank(propertyKey)) {
                properties.setProperty(propertyKey, allProperties.getProperty(propertyName));
            }
        }
        return properties;
    }
    
    @Override
    protected String printConfig() {
        PipelineConfig config = this.currentConfig;
        if (config == null) {
            return "PipelineConfig{null}";
        }
        StringBuilder sb = new StringBuilder("PipelineConfig{enabled=");
        sb.append(config.isEnabled());
        sb.append(", nodes=[");
        List<PipelineNodeConfig> nodes = config.getNodes();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(nodes.get(i).getPipelineId());
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}
