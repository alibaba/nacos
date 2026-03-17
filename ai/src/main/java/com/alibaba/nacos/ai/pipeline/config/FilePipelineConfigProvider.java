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
import java.util.List;
import java.util.Properties;

/**
 * File-based (application.properties) implementation of {@link PipelineConfigProvider}.
 *
 * <p>Reads configuration from EnvUtil with the following keys:
 * <ul>
 *   <li>{@code nacos.ai.pipeline.enabled} - boolean, default false</li>
 *   <li>{@code nacos.ai.pipeline.nodes} - comma-separated pipeline IDs</li>
 *   <li>{@code nacos.ai.pipeline.node.{pipelineId}.props} - comma-separated property key names</li>
 *   <li>{@code nacos.ai.pipeline.node.{pipelineId}.{key}} - individual property values</li>
 * </ul>
 *
 * <p>Follows the singleton pattern like PushConfig.
 *
 * @author kiro
 * @since 3.2.0
 */
public class FilePipelineConfigProvider extends AbstractDynamicConfig implements PipelineConfigProvider {
    
    private static final String CONFIG_NAME = "PipelineConfig";
    
    private static final String KEY_ENABLED = "nacos.ai.pipeline.enabled";
    
    private static final String KEY_NODES = "nacos.ai.pipeline.nodes";
    
    private static final String KEY_NODE_PREFIX = "nacos.ai.pipeline.node.";
    
    private static final String KEY_PROPS_SUFFIX = ".props";
    
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
            boolean enabled = EnvUtil.getProperty(KEY_ENABLED, Boolean.class, false);
            String nodesStr = EnvUtil.getProperty(KEY_NODES, "");
            
            List<PipelineNodeConfig> nodes;
            if (StringUtils.isBlank(nodesStr)) {
                nodes = Collections.emptyList();
            } else {
                String[] nodeIds = nodesStr.split(",");
                nodes = new ArrayList<>(nodeIds.length);
                for (String nodeId : nodeIds) {
                    String trimmedId = nodeId.trim();
                    if (StringUtils.isNotBlank(trimmedId)) {
                        PipelineNodeConfig nodeConfig = new PipelineNodeConfig();
                        nodeConfig.setPipelineId(trimmedId);
                        nodeConfig.setProperties(readNodeProperties(trimmedId));
                        nodes.add(nodeConfig);
                    }
                }
            }
            
            PipelineConfig config = new PipelineConfig();
            config.setEnabled(enabled);
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
     * Read custom properties for a specific pipeline node.
     *
     * <p>Reads {@code nacos.ai.pipeline.node.{pipelineId}.props} as a comma-separated list of property key names,
     * then reads each {@code nacos.ai.pipeline.node.{pipelineId}.{key}} value.
     *
     * @param pipelineId the pipeline node ID
     * @return properties for this node, empty if no props configured
     */
    private Properties readNodeProperties(String pipelineId) {
        Properties properties = new Properties();
        String propsKey = KEY_NODE_PREFIX + pipelineId + KEY_PROPS_SUFFIX;
        String propsStr = EnvUtil.getProperty(propsKey, "");
        if (StringUtils.isBlank(propsStr)) {
            return properties;
        }
        String[] propKeys = propsStr.split(",");
        for (String propKey : propKeys) {
            String trimmedKey = propKey.trim();
            if (StringUtils.isNotBlank(trimmedKey)) {
                String fullKey = KEY_NODE_PREFIX + pipelineId + "." + trimmedKey;
                String value = EnvUtil.getProperty(fullKey);
                if (value != null) {
                    properties.setProperty(trimmedKey, value);
                }
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
