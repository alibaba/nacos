/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.logger.adapter.log4j2;

import com.alibaba.nacos.common.logging.NacosLoggingAdapter;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.ResourceUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Objects;

/**
 * Support for Log4j version 2.7 or higher
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @author xiweng.yy
 * @since 0.9.0
 */
public class Log4J2NacosLoggingAdapter implements NacosLoggingAdapter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Log4J2NacosLoggingAdapter.class);
    
    private static final String NACOS_LOG4J2_LOCATION = "classpath:nacos-log4j2.xml";
    
    private static final String FILE_PROTOCOL = "file";
    
    private static final String NACOS_LOGGER_PREFIX = "com.alibaba.nacos";
    
    private static final String APPENDER_MARK = "ASYNC_NAMING";
    
    private static final String LOG4J2_CLASSES = "org.apache.logging.slf4j.Log4jLogger";
    
    /**
     * Whether configuration has been loaded at least once.
     */
    private volatile boolean hasLoadedOnce = false;
    
    /**
     * Last loaded configuration location.
     */
    private volatile String lastConfigLocation = null;
    
    /**
     * MD5 hash of last loaded configuration content.
     */
    private volatile String lastConfigMd5 = null;
    
    @Override
    public boolean isAdaptedLogger(Class<?> loggerClass) {
        Class<?> expectedLoggerClass = getExpectedLoggerClass();
        return null != expectedLoggerClass && expectedLoggerClass.isAssignableFrom(loggerClass);
    }
    
    private Class<?> getExpectedLoggerClass() {
        try {
            return Class.forName(LOG4J2_CLASSES);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    @Override
    public boolean isNeedReloadConfiguration() {
        // Layer 1: Fast path - check if Nacos-specific appender exists
        // This indicates Nacos configuration has been successfully loaded
        final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final Configuration contextConfiguration = loggerContext.getConfiguration();
        for (Map.Entry<String, Appender> entry : contextConfiguration.getAppenders().entrySet()) {
            if (APPENDER_MARK.equals(entry.getValue().getName())) {
                return false;  // Nacos configuration is active, no reload needed
            }
        }
        
        // Layer 2: Check if configuration has been loaded before
        if (hasLoadedOnce) {
            // Configuration was loaded before but appender not found
            // This means either:
            // 1. User disabled Nacos default config
            // 2. User provided custom config without ASYNC_NAMING appender
            // 3. Configuration loading failed
            
            // Check if configuration file has changed by comparing MD5
            String currentLocation = getCurrentConfigLocation();
            String currentMd5 = calculateConfigMd5(currentLocation);
            
            // Only reload if location or content has changed
            boolean locationChanged = !Objects.equals(currentLocation, lastConfigLocation);
            boolean contentChanged = !Objects.equals(currentMd5, lastConfigMd5);
            
            if (locationChanged || contentChanged) {
                LOGGER.info("Nacos logging configuration changed, will reload. Location changed: {}, Content changed: {}",
                        locationChanged, contentChanged);
                return true;
            }
            
            // Configuration hasn't changed, no reload needed
            return false;
        }
        
        // Layer 3: First time loading
        return true;
    }
    
    @Override
    public String getDefaultConfigLocation() {
        return NACOS_LOG4J2_LOCATION;
    }
    
    @Override
    public void loadConfiguration(NacosLoggingProperties loggingProperties) {
        Log4j2NacosLoggingPropertiesHolder.setProperties(loggingProperties);
        String location = loggingProperties.getLocation();
        loadConfiguration(location);
        
        // Record loading state to prevent unnecessary reloads
        hasLoadedOnce = true;
        lastConfigLocation = location;
        lastConfigMd5 = calculateConfigMd5(location);
        
        LOGGER.info("Nacos logging configuration loaded from: {}", location);
    }
    
    private void loadConfiguration(String location) {
        if (StringUtils.isBlank(location)) {
            return;
        }
        final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final Configuration contextConfiguration = loggerContext.getConfiguration();
        
        // load and start nacos configuration
        Configuration configuration = loadConfiguration(loggerContext, location);
        configuration.start();
        
        // append loggers and appenders to contextConfiguration
        Map<String, Appender> appenders = configuration.getAppenders();
        for (Appender appender : appenders.values()) {
            contextConfiguration.addAppender(appender);
        }
        Map<String, LoggerConfig> loggers = configuration.getLoggers();
        for (String name : loggers.keySet()) {
            if (name.startsWith(NACOS_LOGGER_PREFIX)) {
                contextConfiguration.addLogger(name, loggers.get(name));
            }
        }
        
        loggerContext.updateLoggers();
    }
    
    private Configuration loadConfiguration(LoggerContext loggerContext, String location) {
        try {
            URL url = ResourceUtils.getResourceUrl(location);
            ConfigurationSource source = getConfigurationSource(url);
            // since log4j 2.7 getConfiguration(LoggerContext loggerContext, ConfigurationSource source)
            return ConfigurationFactory.getInstance().getConfiguration(loggerContext, source);
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize Log4J2 logging from " + location, e);
        }
    }
    
    private ConfigurationSource getConfigurationSource(URL url) throws IOException {
        InputStream stream = url.openStream();
        if (FILE_PROTOCOL.equals(url.getProtocol())) {
            return new ConfigurationSource(stream, ResourceUtils.getResourceAsFile(url));
        }
        return new ConfigurationSource(stream, url);
    }
    
    /**
     * Get current configuration location.
     * Since we already stored it in lastConfigLocation, we can just return it.
     *
     * @return current configuration location
     */
    private String getCurrentConfigLocation() {
        return lastConfigLocation;
    }
    
    /**
     * Calculate MD5 hash of configuration file content.
     * This method follows the same pattern as TlsFileWatcher in Nacos framework.
     *
     * @param location configuration file location
     * @return MD5 hash string, or null if calculation fails
     */
    private String calculateConfigMd5(String location) {
        if (StringUtils.isBlank(location)) {
            return null;
        }
        
        InputStream in = null;
        try {
            URL url = ResourceUtils.getResourceUrl(location);
            in = url.openStream();
            String content = IoUtils.toString(in, "UTF-8");
            return MD5Utils.md5Hex(content, "UTF-8");
        } catch (Exception e) {
            // Don't log error for expected cases (e.g., config disabled)
            LOGGER.debug("Failed to calculate MD5 for config location {}: {}", location, e.getMessage());
            return null;
        } finally {
            IoUtils.closeQuietly(in);
        }
    }
}
