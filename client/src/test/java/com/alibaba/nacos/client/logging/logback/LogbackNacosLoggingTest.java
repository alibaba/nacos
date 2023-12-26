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

package com.alibaba.nacos.client.logging.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.ReconfigureOnChangeTask;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.CoreConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class LogbackNacosLoggingTest {
    
    LogbackNacosLogging logbackNacosLogging;
    
    @Mock
    LoggerContextListener loggerContextListener;
    
    ILoggerFactory cachedLoggerFactory;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        logbackNacosLogging = new LogbackNacosLogging();
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
    }
    
    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
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
    public void testLoadConfigurationSuccess() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLogging.loadConfiguration();
        for (Logger each : loggerContext.getLoggerList()) {
            if (!"com.alibaba.nacos.client.naming".equals(each.getName())) {
                continue;
            }
            assertNotNull(each.getAppender("ASYNC-NAMING"));
        }
        boolean containListener = false;
        LoggerContextListener listener = null;
        for (LoggerContextListener each : loggerContext.getCopyOfListenerList()) {
            if (each instanceof LogbackNacosLogging.NacosLoggerContextListener) {
                containListener = true;
                listener = each;
                break;
            }
        }
        assertTrue(containListener);
        // reload duplicate, without exception, listener not add again
        logbackNacosLogging.loadConfiguration();
        containListener = false;
        for (LoggerContextListener each : loggerContext.getCopyOfListenerList()) {
            if (each instanceof LogbackNacosLogging.NacosLoggerContextListener) {
                assertEquals(listener, each);
                containListener = true;
            }
        }
        assertTrue(containListener);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testLoadConfigurationFailure() {
        System.setProperty("nacos.logging.config", "http://localhost");
        logbackNacosLogging.loadConfiguration();
    }
    
    @Test
    public void testLoadConfigurationReload() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLogging.loadConfiguration();
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
    public void testLoadConfigurationStart() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLogging.loadConfiguration();
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
    public void testLoadConfigurationStop() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(CoreConstants.RECONFIGURE_ON_CHANGE_TASK, new ReconfigureOnChangeTask());
        logbackNacosLogging.loadConfiguration();
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