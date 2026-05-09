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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NacosWebStartUp} unit test.
 */
@ExtendWith(MockitoExtension.class)
class NacosWebStartUpTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosWebStartUpTest.class);
    
    @Test
    void startUpPhaseReturnsWeb() {
        NacosWebStartUp startUp = new NacosWebStartUp();
        assertEquals(NacosStartUp.WEB_START_UP_PHASE, startUp.startUpPhase());
    }
    
    @Test
    void getPhaseNameInStartingInfoReturnsApiName() throws Exception {
        NacosWebStartUp startUp = new NacosWebStartUp();
        String name =
            (String) ReflectionTestUtils.invokeMethod(startUp, "getPhaseNameInStartingInfo");
        assertEquals("Nacos Server API", name);
    }
    
    @Test
    void startingThenStartedStopsScheduler() {
        NacosWebStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        assertTrue((Boolean) ReflectionTestUtils.getField(startUp, "starting"));
        startUp.started();
        assertFalse((Boolean) ReflectionTestUtils.getField(startUp, "starting"));
    }
    
    @Test
    void logStartedLogsWithCost() {
        NacosWebStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        startUp.logStarted(LOGGER);
        startUp.started();
    }
    
    @Test
    void failedClosesContext() {
        NacosWebStartUp startUp = new NacosWebStartUp();
        startUp.starting();
        ConfigurableApplicationContext context =
            org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        startUp.failed(new RuntimeException("test"), context);
        org.mockito.Mockito.verify(context).close();
    }
}
