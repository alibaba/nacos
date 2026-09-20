/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.remote.capability;

import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class A2aModeSelectorTest {
    
    @Test
    void shouldKeepLegacyThroughUpgradeAndChooseRadInNewInstance() {
        A2aModeSelector selector = new A2aModeSelector();
        assertEquals(A2aModeSelector.Mode.LEGACY,
            selector.select(AbilityStatus.NOT_SUPPORTED, AbilityStatus.SUPPORTED));
        assertEquals(A2aModeSelector.Mode.LEGACY,
            selector.select(AbilityStatus.SUPPORTED, AbilityStatus.SUPPORTED));
        assertEquals(A2aModeSelector.Mode.RAD,
            new A2aModeSelector().select(AbilityStatus.SUPPORTED, AbilityStatus.SUPPORTED));
    }
    
    @Test
    void shouldNotFixModeUntilEvidenceExists() {
        A2aModeSelector selector = new A2aModeSelector();
        assertEquals(A2aModeSelector.Mode.UNDECIDED,
            selector.select(AbilityStatus.UNKNOWN, AbilityStatus.UNKNOWN));
        assertEquals(A2aModeSelector.Mode.UNDECIDED, selector.select(null, null));
        assertEquals(A2aModeSelector.Mode.LEGACY,
            selector.select(AbilityStatus.UNKNOWN, AbilityStatus.SUPPORTED));
    }
    
    @Test
    void shouldNotFallBackAfterSelectingRad() {
        A2aModeSelector selector = new A2aModeSelector();
        assertEquals(A2aModeSelector.Mode.RAD,
            selector.select(AbilityStatus.SUPPORTED, AbilityStatus.UNKNOWN));
        assertEquals(A2aModeSelector.Mode.RAD,
            selector.select(AbilityStatus.NOT_SUPPORTED, AbilityStatus.SUPPORTED));
    }
}
