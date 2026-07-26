/*
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
 */

package com.alibaba.nacos.core.listener.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link AbstractNacosStartUp} unit test (via NacosWebStartUp).
 */
@ExtendWith(MockitoExtension.class)
class AbstractNacosStartUpTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNacosStartUpTest.class);
    
    @Test
    void startUpPhaseFromConstructor() {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        assertEquals(NacosStartUp.WEB_START_UP_PHASE, startUp.startUpPhase());
    }
    
    @Test
    void startingSetsTimestampAndScheduler() {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        assertNotNull(ReflectionTestUtils.getField(startUp, "startTimestamp"));
        assertNotNull(ReflectionTestUtils.getField(startUp, "startLoggingScheduledExecutor"));
        assertTrue((Boolean) ReflectionTestUtils.getField(startUp, "starting"));
    }
    
    @Test
    void logStartingInfoSchedulesTask() {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        startUp.logStartingInfo(LOGGER);
        assertNotNull(ReflectionTestUtils.getField(startUp, "startLoggingScheduledExecutor"));
        startUp.started();
    }
    
    @Test
    void startedClearsStartingAndClosesExecutor() {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        startUp.started();
        assertFalse((Boolean) ReflectionTestUtils.getField(startUp, "starting"));
    }
    
    @Test
    void failedClosesContextAndClearsStarting() {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        ConfigurableApplicationContext context =
            org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        startUp.failed(new RuntimeException("fail"), context);
        assertFalse((Boolean) ReflectionTestUtils.getField(startUp, "starting"));
        org.mockito.Mockito.verify(context).close();
    }
    
    @Test
    void getStartTimestampReturnsAfterStarting() {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        long ts = (Long) ReflectionTestUtils.getField(startUp, "startTimestamp");
        assertTrue(ts > 0);
    }
    
    @Test
    void logStartingInfoScheduledTaskLogsWhenStarting() throws InterruptedException {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        Logger mockLogger = mock(Logger.class);
        startUp.starting();
        startUp.logStartingInfo(mockLogger);
        TimeUnit.MILLISECONDS.sleep(1500);
        verify(mockLogger, atLeastOnce()).info(org.mockito.ArgumentMatchers.anyString());
        startUp.started();
    }
    
    @Test
    void logStartingInfoScheduledTaskDoesNotLogWhenStartingFalse() throws InterruptedException {
        AbstractNacosStartUp startUp = new NacosWebStartUp();
        Logger mockLogger = mock(Logger.class);
        startUp.starting();
        startUp.logStartingInfo(mockLogger);
        TimeUnit.MILLISECONDS.sleep(1200);
        ReflectionTestUtils.setField(startUp, "starting", false);
        TimeUnit.MILLISECONDS.sleep(1200);
        verify(mockLogger, atLeastOnce()).info(org.mockito.ArgumentMatchers.anyString());
        startUp.started();
    }
}
