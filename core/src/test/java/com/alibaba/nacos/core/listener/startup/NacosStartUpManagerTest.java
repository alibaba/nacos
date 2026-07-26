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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NacosStartUpManager} unit test.
 */
class NacosStartUpManagerTest {
    
    @Test
    void startUnknownPhaseThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> NacosStartUpManager.start("unknown-phase"));
    }
    
    @Test
    void startCoreThenGetCurrentStartUpReturnsCoreStartUp() {
        NacosStartUpManager.start(NacosStartUp.CORE_START_UP_PHASE);
        NacosStartUp current = NacosStartUpManager.getCurrentStartUp();
        assertNotNull(current);
        assertEquals(NacosStartUp.CORE_START_UP_PHASE, current.startUpPhase());
    }
    
    @Test
    void getCurrentStartUpThrowsWhenNotStarted() {
        NacosStartUpManager manager = (NacosStartUpManager) ReflectionTestUtils
            .getField(NacosStartUpManager.class, "INSTANCE");
        ReflectionTestUtils.setField(manager, "currentStartUpPhase", null);
        assertThrows(IllegalStateException.class, NacosStartUpManager::getCurrentStartUp);
    }
    
    @Test
    void getReverseStartedListReturnsReversedOrder() {
        NacosStartUpManager.start(NacosStartUp.CORE_START_UP_PHASE);
        NacosStartUpManager.start(NacosStartUp.WEB_START_UP_PHASE);
        List<NacosStartUp> reversed = NacosStartUpManager.getReverseStartedList();
        assertNotNull(reversed);
        assertTrue(reversed.size() >= 2);
        assertEquals(NacosStartUp.WEB_START_UP_PHASE, reversed.get(0).startUpPhase());
        assertEquals(NacosStartUp.CORE_START_UP_PHASE, reversed.get(1).startUpPhase());
    }
}
