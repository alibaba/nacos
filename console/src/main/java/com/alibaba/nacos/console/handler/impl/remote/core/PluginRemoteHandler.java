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
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.console.handler.core.PluginHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Remote implementation of PluginHandler that handles plugin-related operations via HTTP.
 *
 * @author WangzJi
 */
@Service
@EnabledRemoteHandler
public class PluginRemoteHandler implements PluginHandler {
    
    private static final String FIELD_CONFIG_DEFINITIONS = "configDefinitions";
    
    private static final String FIELD_CONFIG_VALUE_METAS = "configValueMetas";
    
    private static final String FIELD_TYPE = "type";
    
    private static final String FIELD_EFFECT_MODE = "effectMode";
    
    private static final String FIELD_SOURCE = "source";
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public PluginRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public List<PluginInfoVO> listPlugins(String pluginType) throws NacosException {
        List<Map<String, Object>> rawList =
            clientHolder.getNamingMaintainerService().listPlugins(pluginType);
        if (rawList == null) {
            return Collections.emptyList();
        }
        return rawList.stream().map(raw -> convertToVO(raw, PluginInfoVO.class))
            .collect(Collectors.toList());
    }
    
    @Override
    public PluginDetailVO getPluginDetail(String pluginType, String pluginName)
        throws NacosException {
        Map<String, Object> raw =
            clientHolder.getNamingMaintainerService().getPluginDetail(pluginType, pluginName);
        return convertToVO(raw, PluginDetailVO.class);
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
    
    private <T> T convertToVO(Map<String, Object> raw, Class<T> type) {
        return JsonUtils.toObj(JsonUtils.toJson(sanitizeEnumFields(raw)), type);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeEnumFields(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>(raw);
        sanitizeConfigDefinitions(result);
        sanitizeConfigValueMetas(result);
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private void sanitizeConfigDefinitions(Map<String, Object> raw) {
        Object definitions = raw.get(FIELD_CONFIG_DEFINITIONS);
        if (!(definitions instanceof List)) {
            return;
        }
        List<Object> sanitizedDefinitions = new ArrayList<>(((List<?>) definitions).size());
        for (Object each : (List<?>) definitions) {
            if (each instanceof Map) {
                Map<String, Object> definition = new LinkedHashMap<>((Map<String, Object>) each);
                sanitizeEnum(definition, FIELD_TYPE, ConfigItemType.class,
                    ConfigItemType.STRING.name());
                sanitizeEnum(definition, FIELD_EFFECT_MODE, ConfigItemEffectMode.class,
                    ConfigItemEffectMode.RESTART.name());
                sanitizedDefinitions.add(definition);
            } else {
                sanitizedDefinitions.add(each);
            }
        }
        raw.put(FIELD_CONFIG_DEFINITIONS, sanitizedDefinitions);
    }
    
    @SuppressWarnings("unchecked")
    private void sanitizeConfigValueMetas(Map<String, Object> raw) {
        Object valueMetas = raw.get(FIELD_CONFIG_VALUE_METAS);
        if (!(valueMetas instanceof Map)) {
            return;
        }
        Map<String, Object> sanitizedValueMetas =
            new LinkedHashMap<>(((Map<?, ?>) valueMetas).size());
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) valueMetas).entrySet()) {
            Object each = entry.getValue();
            if (each instanceof Map) {
                Map<String, Object> valueMeta = new LinkedHashMap<>((Map<String, Object>) each);
                sanitizeEnum(valueMeta, FIELD_SOURCE, PluginConfigSourceType.class,
                    PluginConfigSourceType.DEFAULT.name());
                sanitizedValueMetas.put(entry.getKey(), valueMeta);
            } else {
                sanitizedValueMetas.put(entry.getKey(), each);
            }
        }
        raw.put(FIELD_CONFIG_VALUE_METAS, sanitizedValueMetas);
    }
    
    private <T extends Enum<T>> void sanitizeEnum(Map<String, Object> raw, String field,
        Class<T> enumType, String defaultValue) {
        Object value = raw.get(field);
        if (value == null) {
            return;
        }
        try {
            Enum.valueOf(enumType, value.toString());
        } catch (IllegalArgumentException e) {
            raw.put(field, defaultValue);
        }
    }
}
