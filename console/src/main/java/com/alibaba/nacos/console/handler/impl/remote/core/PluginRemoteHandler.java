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

package com.alibaba.nacos.console.handler.impl.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.console.handler.core.PluginHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Remote implementation of PluginHandler that handles plugin-related operations via HTTP.
 *
 * @author WangzJi
 */
@Service
@EnabledRemoteHandler
public class PluginRemoteHandler implements PluginHandler {
    
    private static final String FIELD_PLUGIN_ID = "pluginId";
    
    private static final String FIELD_PLUGIN_TYPE = "pluginType";
    
    private static final String FIELD_PLUGIN_NAME = "pluginName";
    
    private static final String FIELD_ENABLED = "enabled";
    
    private static final String FIELD_CRITICAL = "critical";
    
    private static final String FIELD_CONFIGURABLE = "configurable";
    
    private static final String FIELD_EXCLUSIVE = "exclusive";
    
    private static final String FIELD_TOTAL_NODE_COUNT = "totalNodeCount";
    
    private static final String FIELD_AVAILABLE_NODE_COUNT = "availableNodeCount";
    
    private static final String FIELD_CONFIG = "config";
    
    private static final String FIELD_CONFIG_DEFINITIONS = "configDefinitions";
    
    private static final String FIELD_KEY = "key";
    
    private static final String FIELD_NAME = "name";
    
    private static final String FIELD_DESCRIPTION = "description";
    
    private static final String FIELD_DEFAULT_VALUE = "defaultValue";
    
    private static final String FIELD_REQUIRED = "required";
    
    private static final String FIELD_TYPE = "type";
    
    private static final String FIELD_ENUM_VALUES = "enumValues";
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public PluginRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public List<PluginInfoVO> listPlugins(String pluginType) throws NacosException {
        List<Map<String, Object>> rawList =
            clientHolder.getNamingMaintainerService().listPlugins(pluginType);
        List<PluginInfoVO> result = new ArrayList<>(rawList.size());
        for (Map<String, Object> raw : rawList) {
            result.add(convertToPluginInfoVO(raw));
        }
        return result;
    }
    
    @Override
    public PluginDetailVO getPluginDetail(String pluginType, String pluginName)
        throws NacosException {
        Map<String, Object> raw =
            clientHolder.getNamingMaintainerService().getPluginDetail(pluginType, pluginName);
        return convertToPluginDetailVO(raw);
    }
    
    @Override
    public void updatePluginStatus(String pluginType, String pluginName, boolean enabled,
        boolean localOnly)
        throws NacosException {
        clientHolder.getNamingMaintainerService().updatePluginStatus(pluginType, pluginName,
            enabled, localOnly);
    }
    
    @Override
    public void updatePluginConfig(String pluginType, String pluginName, Map<String, String> config,
        boolean localOnly) throws NacosException {
        clientHolder.getNamingMaintainerService().updatePluginConfig(pluginType, pluginName, config,
            localOnly);
    }
    
    @Override
    public Map<String, Boolean> getPluginAvailability(String pluginType, String pluginName)
        throws NacosException {
        return clientHolder.getNamingMaintainerService().getPluginAvailability(pluginType,
            pluginName);
    }
    
    private PluginInfoVO convertToPluginInfoVO(Map<String, Object> raw) {
        PluginInfoVO vo = new PluginInfoVO();
        vo.setPluginId((String) raw.get(FIELD_PLUGIN_ID));
        vo.setPluginType((String) raw.get(FIELD_PLUGIN_TYPE));
        vo.setPluginName((String) raw.get(FIELD_PLUGIN_NAME));
        vo.setEnabled(Boolean.TRUE.equals(raw.get(FIELD_ENABLED)));
        vo.setCritical(Boolean.TRUE.equals(raw.get(FIELD_CRITICAL)));
        vo.setConfigurable(Boolean.TRUE.equals(raw.get(FIELD_CONFIGURABLE)));
        vo.setExclusive(Boolean.TRUE.equals(raw.get(FIELD_EXCLUSIVE)));
        if (raw.get(FIELD_TOTAL_NODE_COUNT) != null) {
            vo.setTotalNodeCount(((Number) raw.get(FIELD_TOTAL_NODE_COUNT)).intValue());
        }
        if (raw.get(FIELD_AVAILABLE_NODE_COUNT) != null) {
            vo.setAvailableNodeCount(((Number) raw.get(FIELD_AVAILABLE_NODE_COUNT)).intValue());
        }
        return vo;
    }
    
    @SuppressWarnings("unchecked")
    private PluginDetailVO convertToPluginDetailVO(Map<String, Object> raw) {
        PluginDetailVO vo = new PluginDetailVO();
        vo.setPluginId((String) raw.get(FIELD_PLUGIN_ID));
        vo.setPluginType((String) raw.get(FIELD_PLUGIN_TYPE));
        vo.setPluginName((String) raw.get(FIELD_PLUGIN_NAME));
        vo.setEnabled(Boolean.TRUE.equals(raw.get(FIELD_ENABLED)));
        vo.setCritical(Boolean.TRUE.equals(raw.get(FIELD_CRITICAL)));
        vo.setConfigurable(Boolean.TRUE.equals(raw.get(FIELD_CONFIGURABLE)));
        if (raw.get(FIELD_CONFIG) != null) {
            vo.setConfig((Map<String, String>) raw.get(FIELD_CONFIG));
        }
        if (raw.get(FIELD_CONFIG_DEFINITIONS) != null) {
            List<Map<String, Object>> rawDefinitions =
                (List<Map<String, Object>>) raw.get(FIELD_CONFIG_DEFINITIONS);
            vo.setConfigDefinitions(convertToConfigItemDefinitions(rawDefinitions));
        }
        return vo;
    }
    
    @SuppressWarnings("unchecked")
    private List<ConfigItemDefinition> convertToConfigItemDefinitions(
        List<Map<String, Object>> rawList) {
        List<ConfigItemDefinition> result = new ArrayList<>(rawList.size());
        for (Map<String, Object> raw : rawList) {
            ConfigItemDefinition definition = new ConfigItemDefinition();
            definition.setKey((String) raw.get(FIELD_KEY));
            definition.setName((String) raw.get(FIELD_NAME));
            definition.setDescription((String) raw.get(FIELD_DESCRIPTION));
            definition.setDefaultValue((String) raw.get(FIELD_DEFAULT_VALUE));
            definition.setRequired(Boolean.TRUE.equals(raw.get(FIELD_REQUIRED)));
            if (raw.get(FIELD_TYPE) != null) {
                String typeStr = raw.get(FIELD_TYPE).toString();
                try {
                    definition.setType(ConfigItemType.valueOf(typeStr));
                } catch (IllegalArgumentException e) {
                    definition.setType(ConfigItemType.STRING);
                }
            }
            if (raw.get(FIELD_ENUM_VALUES) != null) {
                definition.setEnumValues((List<String>) raw.get(FIELD_ENUM_VALUES));
            }
            result.add(definition);
        }
        return result;
    }
}
