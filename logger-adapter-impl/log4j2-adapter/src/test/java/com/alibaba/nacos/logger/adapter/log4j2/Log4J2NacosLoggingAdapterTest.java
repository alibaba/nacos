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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(7, nacosClientLoggers.size());
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
            throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException, URISyntaxException {
        Method getConfigurationSourceMethod = Log4J2NacosLoggingAdapter.class.getDeclaredMethod("getConfigurationSource", URL.class);
        getConfigurationSourceMethod.setAccessible(true);
        URL url = mock(URL.class);
        URI uri = mock(URI.class);
        InputStream inputStream = mock(InputStream.class);
        when(uri.toURL()).thenReturn(url);
        when(url.toURI()).thenReturn(uri);
        when(url.openStream()).thenReturn(inputStream);
        when(url.getProtocol()).thenReturn("http");
        ConfigurationSource actual = (ConfigurationSource) getConfigurationSourceMethod.invoke(log4J2NacosLoggingAdapter, url);
        assertEquals(inputStream, actual.getInputStream());
        assertEquals(url, actual.getURL());
    }
    
    // ========== Additional tests for Bug #13940 fix ==========
    
    /**
     * Test MD5 detection logic - no reload when config hasn't changed.
     * 
     * Scenario:
     * 1. First check should return true (need initial load)
     * 2. After loading, hasLoadedOnce = true
     * 3. Second check with unchanged config should return false (no reload)
     * 4. Third check with unchanged config should return false (no reload)
     */
    @Test
    void testIsNeedReloadConfigurationWithMd5CheckNoChange() {
        // First check - Layer 3 should return true
        assertTrue(log4J2NacosLoggingAdapter.isNeedReloadConfiguration(), 
                "First check should return true for initial load");
        
        // Load configuration
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        verify(propertyChangeListener).propertyChange(any());
        
        // Second check - Layer 2 detects no change, should return false
        assertFalse(log4J2NacosLoggingAdapter.isNeedReloadConfiguration(), 
                "Second check should return false when config hasn't changed");
        
        // Third check - Layer 2 still detects no change, should return false
        assertFalse(log4J2NacosLoggingAdapter.isNeedReloadConfiguration(), 
                "Third check should return false when config still hasn't changed");
    }
    
    /**
     * Test config change detection.
     * 
     * Scenario:
     * 1. Load config without ASYNC_NAMING (disabled config)
     * 2. Simulate config file change (by modifying lastConfigMd5)
     * 3. Check should return true (need reload)
     * 
     * Note: We use disabled config to avoid Layer 1 fast path (ASYNC_NAMING check)
     */
    @Test
    void testIsNeedReloadConfigurationConfigChanged() throws Exception {
        // Use disabled config (no ASYNC_NAMING appender)
        System.setProperty("nacos.logging.default.config.enabled", "false");
        NacosLoggingProperties disabledProperties = new NacosLoggingProperties("classpath:nacos-log4j2.xml", System.getProperties());
        
        // Load config (actually won't load because disabled)
        log4J2NacosLoggingAdapter.loadConfiguration(disabledProperties);
        
        // First check - hasLoadedOnce=true, config unchanged, should return false
        assertFalse(log4J2NacosLoggingAdapter.isNeedReloadConfiguration());
        
        // Simulate config change - modify lastConfigMd5 via reflection
        java.lang.reflect.Field lastConfigMd5Field = Log4J2NacosLoggingAdapter.class.getDeclaredField("lastConfigMd5");
        lastConfigMd5Field.setAccessible(true);
        lastConfigMd5Field.set(log4J2NacosLoggingAdapter, "old-md5-value");
        
        // Check - config changed, Layer 2 should detect and return true
        assertTrue(log4J2NacosLoggingAdapter.isNeedReloadConfiguration());
    }
    
    /**
     * Test disabled config scenario.
     */
    @Test
    void testIsNeedReloadConfigurationConfigDisabled() {
        System.setProperty("nacos.logging.default.config.enabled", "false");
        nacosLoggingProperties = new NacosLoggingProperties("classpath:nacos-log4j2.xml", System.getProperties());
        
        // Load config (actually location is null, won't load)
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        
        // Check - config disabled, Layer 2 should detect null == null, return false
        assertFalse(log4J2NacosLoggingAdapter.isNeedReloadConfiguration(), 
                "Should not reload when config is disabled");
    }
    
    /**
     * Test calculateConfigMd5 method - success case.
     */
    @Test
    void testCalculateConfigMd5Success() throws Exception {
        Method calculateConfigMd5Method = Log4J2NacosLoggingAdapter.class
                .getDeclaredMethod("calculateConfigMd5", String.class);
        calculateConfigMd5Method.setAccessible(true);
        
        // Test normal case - use Nacos default config file
        String md5 = (String) calculateConfigMd5Method.invoke(
                log4J2NacosLoggingAdapter, "classpath:nacos-log4j2.xml");
        
        assertNotNull(md5);
        assertEquals(32, md5.length());
        assertTrue(md5.matches("[0-9a-f]{32}"));
    }
    
    /**
     * Test calculateConfigMd5 method - null input.
     */
    @Test
    void testCalculateConfigMd5NullLocation() throws Exception {
        Method calculateConfigMd5Method = Log4J2NacosLoggingAdapter.class
                .getDeclaredMethod("calculateConfigMd5", String.class);
        calculateConfigMd5Method.setAccessible(true);
        
        // Test null input
        String md5 = (String) calculateConfigMd5Method.invoke(
                log4J2NacosLoggingAdapter, (String) null);
        
        assertNull(md5);
    }
    
    /**
     * Test calculateConfigMd5 method - empty string input.
     */
    @Test
    void testCalculateConfigMd5EmptyLocation() throws Exception {
        Method calculateConfigMd5Method = Log4J2NacosLoggingAdapter.class
                .getDeclaredMethod("calculateConfigMd5", String.class);
        calculateConfigMd5Method.setAccessible(true);
        
        // Test empty string
        String md5 = (String) calculateConfigMd5Method.invoke(
                log4J2NacosLoggingAdapter, "");
        
        assertNull(md5);
    }
    
    /**
     * Test calculateConfigMd5 method - invalid file path.
     */
    @Test
    void testCalculateConfigMd5InvalidLocation() throws Exception {
        Method calculateConfigMd5Method = Log4J2NacosLoggingAdapter.class
                .getDeclaredMethod("calculateConfigMd5", String.class);
        calculateConfigMd5Method.setAccessible(true);
        
        // Test non-existent file - should return null, not throw exception
        String md5 = (String) calculateConfigMd5Method.invoke(
                log4J2NacosLoggingAdapter, "file:///not-exist-file.xml");
        
        assertNull(md5);
    }
    
    /**
     * Test Layer 1 fast path with ASYNC_NAMING appender.
     */
    @Test
    void testIsNeedReloadConfigurationWithAsyncNamingAppender() {
        // Load config (contains ASYNC_NAMING)
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        
        // Check - Layer 1 should detect ASYNC_NAMING and return false quickly
        assertFalse(log4J2NacosLoggingAdapter.isNeedReloadConfiguration(), 
                "Should not reload when ASYNC_NAMING appender exists");
        
        // Verify ASYNC_NAMING appender exists in Log4j2 context
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration contextConfiguration = loggerContext.getConfiguration();
        assertTrue(contextConfiguration.getAppenders().containsKey("ASYNC_NAMING"), 
                "ASYNC_NAMING appender should exist in Log4j2 context");
    }
    
    /**
     * Test initial state of fields.
     */
    @Test
    void testInitialState() throws Exception {
        Log4J2NacosLoggingAdapter adapter = new Log4J2NacosLoggingAdapter();
        
        // Check initial state via reflection
        java.lang.reflect.Field hasLoadedOnceField = Log4J2NacosLoggingAdapter.class.getDeclaredField("hasLoadedOnce");
        hasLoadedOnceField.setAccessible(true);
        assertFalse((Boolean) hasLoadedOnceField.get(adapter));
        
        java.lang.reflect.Field lastConfigLocationField = Log4J2NacosLoggingAdapter.class.getDeclaredField("lastConfigLocation");
        lastConfigLocationField.setAccessible(true);
        assertNull(lastConfigLocationField.get(adapter));
        
        java.lang.reflect.Field lastConfigMd5Field = Log4J2NacosLoggingAdapter.class.getDeclaredField("lastConfigMd5");
        lastConfigMd5Field.setAccessible(true);
        assertNull(lastConfigMd5Field.get(adapter));
    }
    
    /**
     * Test multiple loads of same config.
     */
    @Test
    void testLoadConfigurationMultiple() throws Exception {
        // First load
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        
        // Get first MD5
        java.lang.reflect.Field lastConfigMd5Field = Log4J2NacosLoggingAdapter.class.getDeclaredField("lastConfigMd5");
        lastConfigMd5Field.setAccessible(true);
        String firstMd5 = (String) lastConfigMd5Field.get(log4J2NacosLoggingAdapter);
        
        // Second load of same config
        log4J2NacosLoggingAdapter.loadConfiguration(nacosLoggingProperties);
        
        // Get second MD5
        String secondMd5 = (String) lastConfigMd5Field.get(log4J2NacosLoggingAdapter);
        
        // MD5 should be the same (same config content)
        assertEquals(firstMd5, secondMd5);
        
        // Check should return false (config unchanged)
        assertFalse(log4J2NacosLoggingAdapter.isNeedReloadConfiguration());
    }
}