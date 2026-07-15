/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.visibility.spi;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for loading and accessing {@link VisibilityService} implementations.
 *
 * @author xiweng.yy
 */
public class VisibilityPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(VisibilityPluginManager.class);
    
    private static final VisibilityPluginManager INSTANCE = new VisibilityPluginManager();
    
    private static final String PROPERTIES_PREFIX = "nacos.plugin.visibility.";
    
    private static final String ENABLED_PROPERTY = PROPERTIES_PREFIX + "enabled";
    
    private final Map<String, VisibilityService> visibilityServiceMap = new ConcurrentHashMap<>();
    
    private volatile boolean initialized;
    
    private VisibilityPluginManager() {
        initVisibilityServices();
    }
    
    private synchronized void initVisibilityServices() {
        if (initialized) {
            return;
        }
        Properties allProperties = resolveInitProperties();
        ServiceLoader<VisibilityService> serviceLoader =
            ServiceLoader.load(VisibilityService.class);
        Iterator<VisibilityService> iterator = serviceLoader.iterator();
        while (true) {
            VisibilityService each;
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                each = iterator.next();
            } catch (ServiceConfigurationError | RuntimeException ex) {
                LOGGER.warn(
                    "[VisibilityPluginManager] Failed to load one VisibilityService from SPI, skip it.",
                    ex);
                continue;
            }
            registerVisibilityService(each, allProperties);
        }
        initialized = true;
    }
    
    private void registerVisibilityService(VisibilityService service, Properties allProperties) {
        String serviceName;
        try {
            serviceName = service.getVisibilityServiceName();
        } catch (Throwable ex) {
            LOGGER.warn(
                "[VisibilityPluginManager] VisibilityService({}) resolve name failed, skip.",
                service.getClass(), ex);
            return;
        }
        if (StringUtils.isEmpty(serviceName)) {
            LOGGER.warn(
                "[VisibilityPluginManager] VisibilityService({}) has empty serviceName, skip.",
                service.getClass());
            return;
        }
        Properties serviceProperties = resolveServiceProperties(allProperties, serviceName);
        try {
            service.init(serviceProperties);
        } catch (Throwable ex) {
            LOGGER.warn(
                "[VisibilityPluginManager] Initialize VisibilityService({}:{}) failed, skip.",
                service.getClass(), serviceName, ex);
            return;
        }
        visibilityServiceMap.put(serviceName, service);
        LOGGER.info("[VisibilityPluginManager] Loaded VisibilityService({}:{}) successfully.",
            service.getClass(), serviceName);
    }
    
    private Properties resolveServiceProperties(Properties allProperties, String serviceName) {
        Properties result = new Properties();
        if (allProperties.isEmpty()) {
            return result;
        }
        String legacyPrefix = PROPERTIES_PREFIX + serviceName + ".";
        for (String key : allProperties.stringPropertyNames()) {
            if (key.startsWith(legacyPrefix)) {
                result.setProperty(key.substring(legacyPrefix.length()),
                    allProperties.getProperty(key));
            }
        }
        return result;
    }
    
    public static VisibilityPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Find a visibility service by name.
     *
     * @param serviceName service name
     * @return optional visibility service
     */
    public Optional<VisibilityService> findVisibilityService(String serviceName) {
        if (!isVisibilityPluginEnabled()) {
            LOGGER.debug("[VisibilityPluginManager] Plugin VISIBILITY is disabled by {}",
                ENABLED_PROPERTY);
            return Optional.empty();
        }
        if (!PluginStateCheckerHolder.isPluginEnabled(PluginType.VISIBILITY.getType(),
            serviceName)) {
            LOGGER.debug("[VisibilityPluginManager] Plugin VISIBILITY:{} is disabled", serviceName);
            return Optional.empty();
        }
        return Optional.ofNullable(visibilityServiceMap.get(serviceName));
    }
    
    private boolean isVisibilityPluginEnabled() {
        Properties allProperties = resolveInitProperties();
        String enabledValue = allProperties.getProperty(ENABLED_PROPERTY);
        if (StringUtils.isBlank(enabledValue)) {
            return true;
        }
        return Boolean.parseBoolean(enabledValue);
    }
    
    public Map<String, VisibilityService> getAllPlugins() {
        return Collections.unmodifiableMap(visibilityServiceMap);
    }
    
    private Properties resolveInitProperties() {
        // TODO: Replace reflection with direct EnvUtil.getProperties() after plugin/visibility can depend on nacos-sys.
        try {
            Class<?> envUtilClass = Class.forName("com.alibaba.nacos.sys.env.EnvUtil");
            Method method = envUtilClass.getMethod("getProperties");
            Object result = method.invoke(null);
            if (result instanceof Properties) {
                return (Properties) result;
            }
        } catch (Throwable ex) {
            LOGGER.debug(
                "[VisibilityPluginManager] Cannot load EnvUtil properties, fallback to system properties.",
                ex);
        }
        Properties fallback = new Properties();
        fallback.putAll(System.getProperties());
        return fallback;
    }
}
