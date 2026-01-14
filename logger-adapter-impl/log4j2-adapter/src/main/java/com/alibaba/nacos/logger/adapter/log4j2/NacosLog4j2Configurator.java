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

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Custom Log4j2 Configurator for Nacos logging.
 * 
 * <p>This class provides a framework-compliant way to load Nacos logging configuration
 * without interfering with user's application logging setup. It follows the same design
 * pattern as Logback's NacosLogbackConfiguratorAdapterV1.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Uses {@link Configuration#initialize()} instead of {@link Configuration#start()}
 *       to avoid ClassUnload issue (#13940)</li>
 *   <li>Additively merges Nacos configuration into existing LoggerContext</li>
 *   <li>Non-invasive: does not replace user's logging configuration</li>
 * </ul>
 *
 * @author xiweng.yy
 * @see <a href="https://github.com/alibaba/nacos/issues/13940">#13940</a>
 * @since 3.2.0
 */
public class NacosLog4j2Configurator {
    
    private static final String NACOS_LOGGER_PREFIX = "com.alibaba.nacos";
    
    /**
     * Configure LoggerContext by loading Nacos configuration from URI.
     * This method additively merges Nacos appenders and loggers into the existing configuration.
     *
     * @param loggerContext The LoggerContext to configure
     * @param configLocation URI of the Nacos Log4j2 configuration file
     * @throws IOException if configuration file cannot be read
     */
    public void configure(LoggerContext loggerContext, URI configLocation) throws IOException {
        Configuration nacosConfig = loadConfiguration(loggerContext, configLocation);
        
        // Key fix for issue #13940: Use initialize() instead of start()
        // initialize() sets up the configuration without triggering plugin reinitialization
        nacosConfig.initialize();
        
        // Get the current active configuration
        Configuration currentConfig = loggerContext.getConfiguration();
        
        // Additively merge Nacos appenders (non-invasive approach for middleware)
        // Note: Appenders are started individually and added to currentConfig
        // They are NOT removed from nacosConfig to avoid lifecycle issues
        nacosConfig.getAppenders().values().forEach(appender -> {
            if (!appender.isStarted()) {
                appender.start();
            }
            currentConfig.addAppender(appender);
        });
        
        // Add only Nacos-specific loggers to avoid interfering with user configuration
        nacosConfig.getLoggers().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(NACOS_LOGGER_PREFIX))
                .forEach(entry -> currentConfig.addLogger(entry.getKey(), entry.getValue()));
        
        // Apply the merged configuration
        loggerContext.updateLoggers();
        
        // Important: Do NOT call nacosConfig.stop() here!
        // The appenders and loggers have been transferred to currentConfig.
        // Calling stop() would shut down the appenders that are now owned by currentConfig.
        // nacosConfig will be garbage collected naturally, and since we only called initialize()
        // (not start()), there are no active background threads or resources to clean up.
    }
    
    /**
     * Load Log4j2 configuration from URI using ConfigurationFactory.
     * This is the standard Log4j2 way to parse configuration files.
     *
     * @param ctx LoggerContext
     * @param configLocation URI of configuration file
     * @return Parsed Configuration object
     * @throws IOException if configuration cannot be loaded
     */
    private Configuration loadConfiguration(LoggerContext ctx, URI configLocation) throws IOException {
        try (InputStream stream = configLocation.toURL().openStream()) {
            ConfigurationSource source = new ConfigurationSource(stream, configLocation.toURL());
            return ConfigurationFactory.getInstance().getConfiguration(ctx, source);
        }
    }
}
