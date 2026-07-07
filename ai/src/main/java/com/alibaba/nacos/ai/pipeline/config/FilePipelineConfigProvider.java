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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * File-based (application.properties) implementation of {@link PipelineConfigProvider}.
 *
 * <p>Reads configuration from EnvUtil with the following keys:
 * <ul>
 *   <li>{@code nacos.plugin.ai-pipeline.enabled} - optional global switch for ai-pipeline plugin</li>
 *   <li>{@code nacos.plugin.ai-pipeline.type} - enabled implementation type(s), for example
 *   {@code skill-scanner}; default is {@code skill-spector,skill-scanner}</li>
 *   <li>{@code nacos.plugin.ai-pipeline.{type}.order} - optional execution order override;
 *   lower values execute first</li>
 *   <li>{@code nacos.plugin.ai-pipeline.{type}.{key}} - implementation properties passed to builder</li>
 * </ul>
 * <p>For example: {@code nacos.plugin.ai-pipeline.type=skill-scanner}.
 *
 * <p>Follows the singleton pattern like PushConfig.
 *
 * @author kiro
 * @since 3.2.0
 */
public class FilePipelineConfigProvider extends AbstractDynamicConfig
    implements PipelineConfigProvider {
    
    private static final String CONFIG_NAME = "PipelineConfig";
    
    private static final String KEY_PLUGIN_PREFIX = "nacos.plugin.ai-pipeline";
    
    private static final String KEY_ENABLED = KEY_PLUGIN_PREFIX + ".enabled";
    
    private static final String KEY_TYPE = KEY_PLUGIN_PREFIX + ".type";

    private static final String KEY_ORDER = "order";

    private static final List<String> DEFAULT_TYPES = Collections.unmodifiableList(
        Arrays.asList("skill-spector", "skill-scanner"));
    
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
    
    private List<PipelineNodeConfig> readEnabledPluginNodes() {
        Properties allProperties = EnvUtil.getProperties();
        if (allProperties == null || allProperties.isEmpty()) {
            return Collections.emptyList();
        }
        if (!isPluginEnabled(allProperties)) {
            return Collections.emptyList();
        }
        
        List<String> configuredTypes = readConfiguredTypes(allProperties);
        if (configuredTypes.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<PipelineNodeConfig> nodes = new ArrayList<>(configuredTypes.size());
        for (String typeName : configuredTypes) {
            Properties nodeProperties = readNodeProperties(typeName, allProperties);
            PipelineNodeConfig nodeConfig = new PipelineNodeConfig();
            nodeConfig.setPipelineId(typeName);
            nodeConfig.setProperties(nodeProperties);
            nodeConfig.setOrder(parseOrder(nodeProperties.getProperty(KEY_ORDER)));
            nodes.add(nodeConfig);
        }
        return nodes;
    }
    
    private boolean isPluginEnabled(Properties allProperties) {
        String enabledValue = allProperties.getProperty(KEY_ENABLED);
        if (StringUtils.isBlank(enabledValue)) {
            return true;
        }
        return Boolean.parseBoolean(enabledValue);
    }
    
    private List<String> readConfiguredTypes(Properties allProperties) {
        String typeValue = allProperties.getProperty(KEY_TYPE);
        if (StringUtils.isBlank(typeValue)) {
            return DEFAULT_TYPES;
        }
        Set<String> types = new LinkedHashSet<>();
        for (String each : typeValue.split(",")) {
            String normalized = each == null ? null : each.trim();
            if (StringUtils.isNotBlank(normalized)) {
                types.add(normalized);
            }
        }
        return new ArrayList<>(types);
    }
    
    private Properties readNodeProperties(String typeName, Properties allProperties) {
        Properties properties = new Properties();
        String propertyPrefix = KEY_PLUGIN_PREFIX + "." + typeName + ".";
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

    private Integer parseOrder(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
