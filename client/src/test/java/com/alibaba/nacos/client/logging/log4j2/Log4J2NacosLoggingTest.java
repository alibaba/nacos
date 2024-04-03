/*
 *
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.client.logging.log4j2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class Log4J2NacosLoggingTest {
    
    private static final String NACOS_LOGGER_PREFIX = "com.alibaba.nacos";
    
    @Mock
    PropertyChangeListener propertyChangeListener;
    
    Log4J2NacosLogging log4J2NacosLogging;
    
    @Before
    public void setUp() throws Exception {
        log4J2NacosLogging = new Log4J2NacosLogging();
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerContext.addPropertyChangeListener(propertyChangeListener);
    }
    
    @After
    public void tearDown() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerContext.removePropertyChangeListener(propertyChangeListener);
        System.clearProperty("nacos.logging.default.config.enabled");
        System.clearProperty("nacos.logging.config");
    }
    
    @Test
    public void testLoadConfiguration() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration contextConfiguration = loggerContext.getConfiguration();
        assertEquals(0, contextConfiguration.getLoggers().size());
        log4J2NacosLogging.loadConfiguration();
        //then
        verify(propertyChangeListener).propertyChange(any());
        loggerContext = (LoggerContext) LogManager.getContext(false);
        contextConfiguration = loggerContext.getConfiguration();
        Map<String, LoggerConfig> nacosClientLoggers = contextConfiguration.getLoggers();
        assertEquals(5, nacosClientLoggers.size());
        for (Map.Entry<String, LoggerConfig> loggerEntry : nacosClientLoggers.entrySet()) {
            String loggerName = loggerEntry.getKey();
            Assert.assertTrue(loggerName.startsWith(NACOS_LOGGER_PREFIX));
        }
    }
    
    @Test
    public void testLoadConfigurationWithoutLocation() {
        System.setProperty("nacos.logging.default.config.enabled", "false");
        log4J2NacosLogging = new Log4J2NacosLogging();
        log4J2NacosLogging.loadConfiguration();
        verify(propertyChangeListener, never()).propertyChange(any());
    }
    
    @Test(expected = IllegalStateException.class)
    public void testLoadConfigurationWithWrongLocation() {
        System.setProperty("nacos.logging.config", "http://localhost");
        log4J2NacosLogging = new Log4J2NacosLogging();
        log4J2NacosLogging.loadConfiguration();
        verify(propertyChangeListener, never()).propertyChange(any());
    }
    
    @Test
    public void testGetConfigurationSourceForNonFileProtocol()
            throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException {
        Method getConfigurationSourceMethod = Log4J2NacosLogging.class
                .getDeclaredMethod("getConfigurationSource", URL.class);
        getConfigurationSourceMethod.setAccessible(true);
        URL url = mock(URL.class);
        InputStream inputStream = mock(InputStream.class);
        when(url.openStream()).thenReturn(inputStream);
        when(url.getProtocol()).thenReturn("http");
        ConfigurationSource actual = (ConfigurationSource) getConfigurationSourceMethod.invoke(log4J2NacosLogging, url);
        assertEquals(inputStream, actual.getInputStream());
        assertEquals(url, actual.getURL());
    }
}