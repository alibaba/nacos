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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CustomEnvironment Plugin Management.
 *
 * @author : huangtianhui
 */
public class CustomEnvironmentPluginManager {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(CustomEnvironmentPluginManager.class);
    
    private static final CustomEnvironmentPluginManager INSTANCE =
        new CustomEnvironmentPluginManager();
    
    private volatile List<CustomEnvironmentPluginService> services = Collections.emptyList();
    
    /**
     * Replace the services with instances initialized by the pre-context plugin flow.
     *
     * @param customEnvironmentPluginServices initialized services
     */
    public synchronized void initialize(
        Collection<CustomEnvironmentPluginService> customEnvironmentPluginServices) {
        List<CustomEnvironmentPluginService> initializedServices = new ArrayList<>();
        if (customEnvironmentPluginServices != null) {
            for (CustomEnvironmentPluginService service : customEnvironmentPluginServices) {
                if (service == null) {
                    continue;
                }
                if (StringUtils.isBlank(service.pluginName())) {
                    LOGGER.warn(
                        "[CustomEnvironmentPluginManager] Ignore environment plugin {} with "
                            + "blank plugin name.",
                        service.getClass().getName());
                    continue;
                }
                LOGGER.info(
                    "[CustomEnvironmentPluginManager] Load environment plugin '{}' ({})",
                    service.pluginName(), service.getClass().getName());
                initializedServices.add(service);
            }
        }
        initializedServices.sort(
            Comparator.comparingInt(CustomEnvironmentPluginService::order));
        services = Collections.unmodifiableList(initializedServices);
    }
    
    public static CustomEnvironmentPluginManager getInstance() {
        return INSTANCE;
    }
    
    public Set<String> getPropertyKeys() {
        Set<String> keys = new HashSet<>();
        for (CustomEnvironmentPluginService customEnvironmentPluginService : services) {
            keys.addAll(customEnvironmentPluginService.propertyKey());
        }
        return keys;
    }
    
    public Map<String, Object> getCustomValues(Map<String, Object> sourceProperty) {
        Map<String, Object> customValuesMap = new HashMap<>(1);
        for (CustomEnvironmentPluginService customEnvironmentPluginService : services) {
            Set<String> keys = customEnvironmentPluginService.propertyKey();
            Map<String, Object> propertyMap = new HashMap<>(keys.size());
            for (String key : keys) {
                propertyMap.put(key, sourceProperty.get(key));
            }
            Map<String, Object> targetPropertyMap =
                customEnvironmentPluginService.customValue(propertyMap);
            //Only the current plugin key is allowed
            Set<String> targetKeys = new HashSet<>(targetPropertyMap.keySet());
            targetKeys.removeAll(keys);
            for (String key : targetKeys) {
                targetPropertyMap.remove(key);
            }
            customValuesMap.putAll(targetPropertyMap);
        }
        // [issue 13367] fix ConcurrentModificationException
        customValuesMap.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));
        return customValuesMap;
    }
    
    /**
     * Injection realization.
     *
     * @param customEnvironmentPluginService customEnvironmentPluginService implementation
     * @deprecated environment services are initialized by the pre-context plugin flow
     */
    @Deprecated
    public static synchronized void join(
        CustomEnvironmentPluginService customEnvironmentPluginService) {
        if (Objects.isNull(customEnvironmentPluginService)) {
            return;
        }
        List<CustomEnvironmentPluginService> updatedServices =
            new ArrayList<>(INSTANCE.services);
        updatedServices.add(customEnvironmentPluginService);
        INSTANCE.initialize(updatedServices);
        LOGGER.info("[CustomEnvironmentPluginService] join successfully.");
    }
}
