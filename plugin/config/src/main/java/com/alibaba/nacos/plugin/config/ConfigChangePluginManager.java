/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.spi.ConfigChangePluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

/**
 * All config change plugin manager.
 *
 * @author liyunfei
 */
public class ConfigChangePluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangePluginManager.class);
    
    private static final Integer PLUGIN_SERVICE_COUNT = 4;
    
    private static final Integer POINT_CUT_TYPE_COUNT = ConfigChangePointCutTypes.values().length;
    
    /**
     * The relationship of serviceType and  {@link ConfigChangePluginService} ,default capacity is the count of plugin
     * service.
     */
    private static final Map<String, ConfigChangePluginService> CONFIG_CHANGE_PLUGIN_SERVICE_MAP = new ConcurrentHashMap<>(
            PLUGIN_SERVICE_COUNT);
    
    /**
     * The relationship of config change pointcut type and the list of {@link ConfigChangePluginService} will pointcut
     * it, default capacity is the count of pointcutTypes.
     */
    private static final Map<ConfigChangePointCutTypes, List<ConfigChangePluginService>> CONFIG_CHANGE_PLUGIN_SERVICES_MAP = new ConcurrentHashMap<>(
            POINT_CUT_TYPE_COUNT);
    
    private static final ConfigChangePluginManager INSTANCE = new ConfigChangePluginManager();
    
    private ConfigChangePluginManager() {
        loadConfigChangeServices();
    }
    
    /**
     * Load all config change plugin services by spi.
     */
    private static void loadConfigChangeServices() {
        Collection<ConfigChangePluginService> configChangePluginServices = NacosServiceLoader
                .load(ConfigChangePluginService.class);
        // load all config change plugin by spi
        for (ConfigChangePluginService each : configChangePluginServices) {
            if (StringUtils.isEmpty(each.getServiceType())) {
                LOGGER.warn("[ConfigChangePluginManager] Load {}({}) ConfigChangeServiceName(null/empty) fail. "
                                + "Please Add the Plugin Service ConfigChangeServiceName to resolve.",
                        each.getClass().getName(), each.getClass());
                continue;
            }
            CONFIG_CHANGE_PLUGIN_SERVICE_MAP.put(each.getServiceType(), each);
            LOGGER.info("[ConfigChangePluginManager] Load {}({}) ConfigChangeServiceName({}) successfully.",
                    each.getClass().getName(), each.getClass(), each.getServiceType());
            // map the relationship of pointcut and plugin service
            addPluginServiceByPointCut(each);
        }
        // sort plugin service
        sortPluginServiceByPointCut();
    }

    public static ConfigChangePluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Dynamic get any pluginServiceImpl.
     *
     * @param serviceType plugin service type.
     * @return
     */
    public Optional<ConfigChangePluginService> findPluginServiceImpl(String serviceType) {
        return Optional.ofNullable(CONFIG_CHANGE_PLUGIN_SERVICE_MAP.get(serviceType));
    }
    
    /**
     * Dynamic add new ConfigChangeService.
     *
     * @param configChangePluginService ConfigChangeService.
     * @return
     */
    public static synchronized boolean join(ConfigChangePluginService configChangePluginService) {
        CONFIG_CHANGE_PLUGIN_SERVICE_MAP
                .putIfAbsent(configChangePluginService.getServiceType(), configChangePluginService);
        addPluginServiceByPointCut(configChangePluginService);
        return true;
    }
    
    /**
     * Get the plugin service queue of the pointcut method.
     *
     * @param pointcutName pointcut method name,detail see {@link ConfigChangePointCutTypes}.
     * @return
     */
    public static List<ConfigChangePluginService> findPluginServicesByPointcut(
            ConfigChangePointCutTypes pointcutName) {
        return CONFIG_CHANGE_PLUGIN_SERVICES_MAP.getOrDefault(pointcutName, new ArrayList<>());
    }
    
    private static void addPluginServiceByPointCut(ConfigChangePluginService configChangePluginService) {
        ConfigChangePointCutTypes[] pointcutNames = configChangePluginService.pointcutMethodNames();
        for (ConfigChangePointCutTypes name : pointcutNames) {
            List<ConfigChangePluginService> configChangePluginServiceList = CONFIG_CHANGE_PLUGIN_SERVICES_MAP
                    .get(name);
            if (configChangePluginServiceList == null) {
                configChangePluginServiceList = new ArrayList<>(PLUGIN_SERVICE_COUNT);
            }
            configChangePluginServiceList.add(configChangePluginService);
            CONFIG_CHANGE_PLUGIN_SERVICES_MAP.put(name, configChangePluginServiceList);
        }
    }

    private static void sortPluginServiceByPointCut() {
        CONFIG_CHANGE_PLUGIN_SERVICES_MAP.forEach((type, pluginServices) -> {
            List<ConfigChangePluginService> sortedList = new ArrayList<>(pluginServices);
            sortedList.sort(Comparator.comparingInt(ConfigChangePluginService::getOrder));
            CONFIG_CHANGE_PLUGIN_SERVICES_MAP.put(type, sortedList);
        });
    }
}
