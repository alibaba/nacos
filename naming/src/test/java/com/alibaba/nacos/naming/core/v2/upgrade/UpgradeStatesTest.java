/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link UpgradeStates} unit tests.
 *
 * @author chenglu
 * @date 2021-09-01 22:04
 */
public class UpgradeStatesTest {
    
    private UpgradeStates upgradeStates = new UpgradeStates();
    
    @Test
    public void testOnEvent() {
        UpgradeStates.UpgradeStateChangedEvent changedEvent = new UpgradeStates.UpgradeStateChangedEvent(true);
        upgradeStates.onEvent(changedEvent);
    
        Assert.assertTrue(upgradeStates.isUpgraded());
    }
}
