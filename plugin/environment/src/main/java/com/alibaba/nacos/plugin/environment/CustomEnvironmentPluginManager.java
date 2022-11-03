/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.environment;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CustomEnvironment Plugin Management.
 *
 * @author : huangtianhui
 */
public class CustomEnvironmentPluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomEnvironmentPluginManager.class);

    private static final List<CustomEnvironmentPluginService> SERVICE_LIST = new LinkedList<>();

    private static final CustomEnvironmentPluginManager INSTANCE = new CustomEnvironmentPluginManager();

    public CustomEnvironmentPluginManager() {
        loadInitial();
    }

    private void loadInitial() {
        Collection<CustomEnvironmentPluginService> customEnvironmentPluginServices = NacosServiceLoader.load(
                CustomEnvironmentPluginService.class);
        for (CustomEnvironmentPluginService customEnvironmentPluginService : customEnvironmentPluginServices) {
            if (StringUtils.isBlank(customEnvironmentPluginService.pluginName())) {
                LOGGER.warn("[customEnvironmentPluginService] Load customEnvironmentPluginService({}) customEnvironmentPluginName(null/empty) fail."
                        + " Please Add customEnvironmentPluginName to resolve.", customEnvironmentPluginService.getClass());
                continue;
            }
            LOGGER.info("[CustomEnvironmentPluginManager] Load customEnvironmentPluginService({}) customEnvironmentPluginName({}) successfully.",
                    customEnvironmentPluginService.getClass(), customEnvironmentPluginService.pluginName());
        }
        SERVICE_LIST.addAll(customEnvironmentPluginServices.stream()
                .sorted(Comparator.comparingInt(CustomEnvironmentPluginService::order))
                .collect(Collectors.toList()));
    }

    public static CustomEnvironmentPluginManager getInstance() {
        return INSTANCE;
    }

    public Set<String> getPropertyKeys() {
        Set<String> keys = new HashSet<>();
        for (CustomEnvironmentPluginService customEnvironmentPluginService : SERVICE_LIST) {
            keys.addAll(customEnvironmentPluginService.propertyKey());
        }
        return keys;
    }

    public Map<String, Object> getCustomValues(Map<String, Object> sourceProperty) {
        Map<String, Object> customValuesMap = new HashMap<>(1);
        for (CustomEnvironmentPluginService customEnvironmentPluginService : SERVICE_LIST) {
            Set<String> keys = customEnvironmentPluginService.propertyKey();
            Map<String, Object> propertyMap = new HashMap<>(keys.size());
            for (String key : keys) {
                propertyMap.put(key, sourceProperty.get(key));
            }
            Map<String, Object> targetPropertyMap = customEnvironmentPluginService.customValue(propertyMap);
            //Only the current plugin key is allowed
            Set<String> targetKeys = new HashSet<>(targetPropertyMap.keySet());
            targetKeys.removeAll(keys);
            for (String key : targetKeys) {
                targetPropertyMap.remove(key);
            }
            customValuesMap.putAll(targetPropertyMap);
        }
        for (Map.Entry<String, Object> entry : customValuesMap.entrySet()) {
            if (Objects.isNull(entry.getValue())) {
                customValuesMap.remove(entry.getKey());
            }
        }
        return customValuesMap;
    }

    /**
     * Injection realization.
     *
     * @param customEnvironmentPluginService customEnvironmentPluginService implementation
     */
    public static synchronized void join(CustomEnvironmentPluginService customEnvironmentPluginService) {
        if (Objects.isNull(customEnvironmentPluginService)) {
            return;
        }
        SERVICE_LIST.add(customEnvironmentPluginService);
        LOGGER.info("[CustomEnvironmentPluginService] join successfully.");
    }
}
