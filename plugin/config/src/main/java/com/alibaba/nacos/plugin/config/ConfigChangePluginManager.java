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
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.spi.ConfigChangeService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All config change plugin manager.
 *
 * @author liyunfei
 */
public class ConfigChangePluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangePluginManager.class);
    
    private static final Integer PLUGIN_SERVICE_COUNT = ConfigChangeConstants.getPluginServiceCount();
    
    private static final Integer POINT_CUT_TYPE_COUNT = ConfigChangePointCutTypes.values().length;
    
    /**
     * The relationship of serviceType and  {@link ConfigChangeService} ,default capacity is the count of plugin
     * service.
     */
    private static Map<String, ConfigChangeService> configChangeServiceMap = new ConcurrentHashMap<>(
            PLUGIN_SERVICE_COUNT);
    
    /**
     * The relationship of config change pointcut type and the queue of {@link ConfigChangeService} will pointcut it,
     * default capacity is the count of pointcutTypes.
     */
    private static Map<ConfigChangePointCutTypes, PriorityQueue<ConfigChangeService>> priorityQueueMap = new ConcurrentHashMap<>(
            POINT_CUT_TYPE_COUNT);
    
    private static final ConfigChangePluginManager INSTANCE = new ConfigChangePluginManager();
    
    private ConfigChangePluginManager() {
        loadConfigChangeServices();
    }
    
    /**
     * Load all config change plugin services by spi.
     */
    private static void loadConfigChangeServices() {
        Collection<ConfigChangeService> configChangeServices = NacosServiceLoader.load(ConfigChangeService.class);
        for (ConfigChangeService each : configChangeServices) {
            if (StringUtils.isEmpty(each.getServiceType())) {
                LOGGER.warn("[ConfigChangePluginManager] Load {}({}) ConfigChangeServiceName(null/empty) fail. "
                                + "Please Add the Plugin Service ConfigChangeServiceName to resolve.",
                        each.getClass().getName(), each.getClass());
                continue;
            }
            configChangeServiceMap.put(each.getServiceType(), each);
            LOGGER.info("[ConfigChangePluginManager] Load {}({}) ConfigChangeServiceName({}) successfully.",
                    each.getClass().getName(), each.getClass(), each.getServiceType());
            ConfigChangePointCutTypes[] pointcutNames = each.pointcutMethodNames();
            for (ConfigChangePointCutTypes name : pointcutNames) {
                PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = priorityQueueMap.get(name);
                if (configChangeServicePriorityQueue == null) {
                    configChangeServicePriorityQueue = new PriorityQueue<>(PLUGIN_SERVICE_COUNT);
                }
                String configEnabled =
                        ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX + each.getServiceType() + ".enabled";
                Boolean enabled = EnvUtil.getProperty(configEnabled, Boolean.class);
                if (enabled != null && enabled) {
                    configChangeServicePriorityQueue.add(each);
                    priorityQueueMap.put(name, configChangeServicePriorityQueue);
                }
            }
        }
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
    public Optional<ConfigChangeService> findPluginServiceImpl(String serviceType) {
        return Optional.ofNullable(configChangeServiceMap.get(serviceType));
    }
    
    /**
     * Dynamic add new ConfigChangeService.
     *
     * @param configChangeService ConfigChangeService.
     * @return
     */
    public static synchronized boolean join(ConfigChangeService configChangeService) {
        configChangeServiceMap.putIfAbsent(configChangeService.getServiceType(), configChangeService);
        return true;
    }
    
    /**
     * Get the plugin service queue of the pointcut method.
     *
     * @param pointcutName pointcut method name,detail see {@link com.alibaba.nacos.plugin.config.apsect.ConfigChangeAspect}.
     * @return
     */
    public static PriorityQueue<ConfigChangeService> findPluginServiceQueueByPointcut(
            ConfigChangePointCutTypes pointcutName) {
        return priorityQueueMap.get(pointcutName);
    }
}
