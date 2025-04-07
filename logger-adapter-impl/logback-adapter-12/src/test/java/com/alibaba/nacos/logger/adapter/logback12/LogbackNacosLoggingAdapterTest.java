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

package com.alibaba.nacos.logger.adapter.logback12;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.ReconfigureOnChangeTask;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.CoreConstants;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LogbackNacosLoggingAdapterTest {
    
    LogbackNacosLoggingAdapter logbackNacosLoggingAdapter;
    
    @Mock
    LoggerContextListener loggerContextListener;
    
    ILoggerFactory cachedLoggerFactory;
    
    NacosLoggingProperties loggingProperties;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        logbackNacosLoggingAdapter = new LogbackNacosLoggingAdapter();
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
            LoggerContext loggerContext = (LoggerContext) loggerFactory;
            loggerContext.addListener(loggerContextListener);
        } else {
            cachedLoggerFactory = loggerFactory;
            LoggerContext loggerContext = new LoggerContext();
            loggerContext.addListener(loggerContextListener);
            setLoggerFactory(loggerContext);
        }
        loggingProperties = new NacosLoggingProperties("classpath:nacos-logback12.xml", new Properties());
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.removeListener(loggerContextListener);
        if (null != cachedLoggerFactory) {
            setLoggerFactory(cachedLoggerFactory);
            assertEquals(cachedLoggerFactory, LoggerFactory.getILoggerFactory());
        }
        System.clearProperty("nacos.logging.default.config.enabled");
        System.clearProperty("nacos.logging.config");
    }
    
    public void setLoggerFactory(ILoggerFactory loggerFactory) throws NoSuchFieldException, IllegalAccessException {
        Field loggerFactoryField = StaticLoggerBinder.getSingleton().getClass().getDeclaredField("loggerFactory");
        loggerFactoryField.setAccessible(true);
        loggerFactoryField.set(StaticLoggerBinder.getSingleton(), loggerFactory);
    }
    
    @Test
    void testLoadConfigurationSuccess() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLoggingAdapter.loadConfiguration(loggingProperties);
        for (Logger each : loggerContext.getLoggerList()) {
            if (!"com.alibaba.nacos.client.naming".equals(each.getName())) {
                continue;
            }
            assertNotNull(each.getAppender("ASYNC-NAMING"));
        }
        boolean containListener = false;
        LoggerContextListener listener = null;
        for (LoggerContextListener each : loggerContext.getCopyOfListenerList()) {
            if (each instanceof LogbackNacosLoggingAdapter.NacosLoggerContextListener) {
                containListener = true;
                listener = each;
                break;
            }
        }
        assertTrue(containListener);
        // reload duplicate, without exception, listener not add again
        logbackNacosLoggingAdapter.loadConfiguration(loggingProperties);
        containListener = false;
        for (LoggerContextListener each : loggerContext.getCopyOfListenerList()) {
            if (each instanceof LogbackNacosLoggingAdapter.NacosLoggerContextListener) {
                assertEquals(listener, each);
                containListener = true;
            }
        }
        assertTrue(containListener);
    }
    
    @Test
    void testIsAdaptedLogger() {
        assertTrue(logbackNacosLoggingAdapter.isAdaptedLogger(Logger.class));
        assertFalse(logbackNacosLoggingAdapter.isAdaptedLogger(java.util.logging.Logger.class));
    }
    
    @Test
    void testLoadConfigurationFailure() {
        assertThrows(IllegalStateException.class, () -> {
            System.setProperty("nacos.logging.config", "http://localhost");
            loggingProperties = new NacosLoggingProperties("classpath:nacos-logback12.xml", System.getProperties());
            logbackNacosLoggingAdapter.loadConfiguration(loggingProperties);
        });
    }
    
    @Test
    void testIsNeedReloadConfiguration() {
        assertFalse(logbackNacosLoggingAdapter.isNeedReloadConfiguration());
    }
    
    @Test
    void testGetDefaultConfigLocation() {
        assertEquals("classpath:nacos-logback12.xml", logbackNacosLoggingAdapter.getDefaultConfigLocation());
    }
    
    @Test
    void testLoadConfigurationReload() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLoggingAdapter.loadConfiguration(loggingProperties);
        loggerContext.reset();
        verify(loggerContextListener).onReset(loggerContext);
        for (Logger each : loggerContext.getLoggerList()) {
            if (!"com.alibaba.nacos.client.naming".equals(each.getName())) {
                continue;
            }
            assertNotNull(each.getAppender("ASYNC-NAMING"));
        }
    }
    
    @Test
    void testLoadConfigurationStart() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLoggingAdapter.loadConfiguration(loggingProperties);
        loggerContext.start();
        verify(loggerContextListener).onStart(loggerContext);
        for (Logger each : loggerContext.getLoggerList()) {
            if (!"com.alibaba.nacos.client.naming".equals(each.getName())) {
                continue;
            }
            assertNotNull(each.getAppender("ASYNC-NAMING"));
        }
    }
    
    @Test
    void testLoadConfigurationStop() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLoggingAdapter.loadConfiguration(loggingProperties);
        loggerContext.stop();
        verify(loggerContextListener).onReset(loggerContext);
        verify(loggerContextListener, never()).onStop(loggerContext);
        for (Logger each : loggerContext.getLoggerList()) {
            if (!"com.alibaba.nacos.client.naming".equals(each.getName())) {
                continue;
            }
            assertNotNull(each.getAppender("ASYNC-NAMING"));
        }
        assertTrue(loggerContext.getCopyOfListenerList().isEmpty());
    }
}