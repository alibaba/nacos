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
import com.alibaba.nacos.plugin.config.spi.ConfigChangeService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * all config change plugin manager.
 *
 * @author liyunfei
 */
public class ConfigChangePluginManager {
    
    /**
     * the relationship of serviceName and ConfigChangeService.
     */
    private static Map<String, ConfigChangeService> configChangeServiceMap = new ConcurrentHashMap<>();
    
    /**
     * the relationship of pointcut method name and the ConfigChangeService (should assure to load the only
     * way(nacos/userDefine) of every ConfigChangePluginService by sys config ,if dont config,load nacos default
     * implements way.) will pointcut it.
     */
    private static Map<String, PriorityQueue<ConfigChangeService>> priorityQueueMap = new ConcurrentHashMap<>();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangePluginManager.class);
    
    private static final ConfigChangePluginManager INSTANCE = new ConfigChangePluginManager();
    
    private ConfigChangePluginManager() {
        loadConfigChangeServices();
    }
    
    private static void loadConfigChangeServices() {
        Collection<ConfigChangeService> configChangeServices = NacosServiceLoader.load(ConfigChangeService.class);
        for (ConfigChangeService each : configChangeServices) {
            if (StringUtils.isEmpty(each.getServiceName())) {
                LOGGER.warn("[ConfigChangePluginManager] Load {}({}) ConfigChangeServiceName(null/empty) fail. "
                                + "Please Add the Plugin Service ConfigChangeServiceName to resolve.",
                        each.getClass().getName(), each.getClass());
                continue;
            }
            configChangeServiceMap.put(each.getServiceName(), each);
            LOGGER.info("[ConfigChangePluginManager] Load {}({}) ConfigChangeServiceName({}) successfully.",
                    each.getClass().getName(), each.getClass(), each.getServiceName());
            Set<String> pointcutNames = each.pointcutMethodNames();
            for (String name : pointcutNames) {
                PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = priorityQueueMap.get(name);
                if (configChangeServicePriorityQueue == null) {
                    configChangeServicePriorityQueue = new PriorityQueue<>(8);
                }
                String configEnabled =
                        ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX + each.getServiceType() + ".enabled";
                String configImplType =
                        ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX + each.getServiceType() + ".way";
                Boolean enabled = EnvUtil.getProperty(configEnabled, Boolean.class);
                if (enabled != null && enabled) {
                    String implWayType = EnvUtil.getProperty(configImplType);
                    if (implWayType == null) {
                        implWayType = ConfigChangeConstants.NACOS_IMPL_WAY;
                    }
                    if (implWayType.equals(each.getImplWay())) {
                        configChangeServicePriorityQueue.add(each);
                    }
                    priorityQueueMap.put(name, configChangeServicePriorityQueue);
                }
            }
        }
    }
    
    public static ConfigChangePluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * dynamic get any pluginServiceImpl.
     *
     * @param serviceName plugin service name.
     * @return
     */
    public Optional<ConfigChangeService> findPluginServiceImpl(String serviceName) {
        return Optional.ofNullable(configChangeServiceMap.get(serviceName));
    }
    
    /**
     * dynamic add new ConfigChangeService.
     *
     * @param configChangeService ConfigChangeService.
     * @return
     */
    public static synchronized boolean join(ConfigChangeService configChangeService) {
        configChangeServiceMap.putIfAbsent(configChangeService.getServiceName(), configChangeService);
        return true;
    }
    
    /**
     * get the plugin service queue of the pointcut method.
     *
     * @param pointcutName pointcut method name,detail see {@link com.alibaba.nacos.plugin.config.apsect.ConfigChangeAspect}.
     * @return
     */
    public static PriorityQueue<ConfigChangeService> findPluginServiceQueueByPointcut(String pointcutName) {
        return priorityQueueMap.get(pointcutName);
    }
}
