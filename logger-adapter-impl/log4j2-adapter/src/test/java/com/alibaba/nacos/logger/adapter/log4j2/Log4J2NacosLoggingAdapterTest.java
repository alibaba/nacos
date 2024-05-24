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

import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Log4J2NacosLoggingAdapterTest {
    
    private static final String NACOS_LOGGER_PREFIX = "com.alibaba.nacos";
    
    @Mock
    PropertyChangeListener propertyChangeListener;
    
    NacosLoggingProperties nacosLoggingProperties;
    
    Log4J2NacosLoggingAdapter log4J2NacosLoggingAdapter;
    
    @BeforeEach
    void setUp() throws Exception {
        log4J2NacosLoggingAdapter = new Log4J2NacosLoggingAdapter();
        nacosLoggingProperties = new NacosLoggingProperties("classpath:nacos-log4j2.xml", System.getProperties());
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerContext.addPropertyChangeListener(propertyChangeListener);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerContext.removePropertyChangeListener(propertyChangeListener);
        loggerContext.setConfigLocation(loggerContext.getConfigLocation());
        System.clearProperty("nacos.logging.default.config.enabled");
        System.clearProperty("nacos.logging.config");
    }
    
    @Test
    void testIsAdaptedLogger() {
        assertTrue(log4J2NacosLoggingAdapter.isAdaptedLogger(org.apache.logging.slf4j.Log4jLogger.class));
        assertFalse(log4J2NacosLoggingAdapter.isAdaptedLogger(Logger.class));
    }
    
    @Test
    void testIsNeedReloadConfiguration() {
        assertTrue(log4J2NacosLoggingAdapter.isNeedReloadConfiguration());
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        assertFalse(log4J2NacosLoggingAdapter.isNeedReloadConfiguration());
    }
    
    @Test
    void testGetDefaultConfigLocation() {
        assertEquals("classpath:nacos-log4j2.xml", log4J2NacosLoggingAdapter.getDefaultConfigLocation());
    }
    
    @Test
    void testLoadConfiguration() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration contextConfiguration = loggerContext.getConfiguration();
        assertEquals(0, contextConfiguration.getLoggers().size());
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        //then
        verify(propertyChangeListener).propertyChange(any());
        loggerContext = (LoggerContext) LogManager.getContext(false);
        contextConfiguration = loggerContext.getConfiguration();
        Map<String, LoggerConfig> nacosClientLoggers = contextConfiguration.getLoggers();
        assertEquals(6, nacosClientLoggers.size());
        for (Map.Entry<String, LoggerConfig> loggerEntry : nacosClientLoggers.entrySet()) {
            String loggerName = loggerEntry.getKey();
            assertTrue(loggerName.startsWith(NACOS_LOGGER_PREFIX));
        }
    }
    
    @Test
    void testLoadConfigurationWithoutLocation() {
        System.setProperty("nacos.logging.default.config.enabled", "false");
        nacosLoggingProperties = new NacosLoggingProperties("classpath:nacos-log4j2.xml", System.getProperties());
        log4J2NacosLoggingAdapter = new Log4J2NacosLoggingAdapter();
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        verify(propertyChangeListener, never()).propertyChange(any());
    }
    
    @Test
    void testLoadConfigurationWithWrongLocation() {
        assertThrows(IllegalStateException.class, () -> {
            System.setProperty("nacos.logging.config", "http://localhost");
            nacosLoggingProperties = new NacosLoggingProperties("classpath:nacos-log4j2.xml", System.getProperties());
            log4J2NacosLoggingAdapter = new Log4J2NacosLoggingAdapter();
            log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
            verify(propertyChangeListener, never()).propertyChange(any());
        });
    }
    
    @Test
    void testGetConfigurationSourceForNonFileProtocol()
            throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException {
        Method getConfigurationSourceMethod = Log4J2NacosLoggingAdapter.class.getDeclaredMethod("getConfigurationSource", URL.class);
        getConfigurationSourceMethod.setAccessible(true);
        URL url = mock(URL.class);
        InputStream inputStream = mock(InputStream.class);
        when(url.openStream()).thenReturn(inputStream);
        when(url.getProtocol()).thenReturn("http");
        ConfigurationSource actual = (ConfigurationSource) getConfigurationSourceMethod.invoke(log4J2NacosLoggingAdapter, url);
        assertEquals(inputStream, actual.getInputStream());
        assertEquals(url, actual.getURL());
    }
}