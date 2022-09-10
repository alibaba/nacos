/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.common.ability.handler.HandlerMapping;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SpringBootTest
public class AbilityControlManagerTest {

    private TestServerAbilityControlManager serverAbilityControlManager = new TestServerAbilityControlManager();

    private volatile int enabled = 0;

    @Before
    public void inject() {
        Map<AbilityKey, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.TEST_1, true);
        serverAbilityControlManager.setCurrentSupportingAbility(newTable);
    }
    
    @Test
    public void testComponent() throws InterruptedException {
        enabled = 0;
        // invoke enable() or disable() when registering
        serverAbilityControlManager.registerComponent(AbilityKey.TEST_1, new TestHandlerMapping(), -1);
        Assert.assertEquals(1, serverAbilityControlManager.handlerMappingCount());

        serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // nothing happens if it has enabled
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));

        // invoke disable()
        serverAbilityControlManager.disableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // disable will invoke handler
        Assert.assertEquals(enabled, 0);
        Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));

        serverAbilityControlManager.disableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // nothing to do because it has disable
        Assert.assertEquals(enabled, 0);
        Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));

        serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));

        serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
    }
    
    @Test
    public void testCurrentNodeAbility() {
        Set<AbilityKey> keySet = serverAbilityControlManager.getCurrentNodeAbilities().keySet();
        // diable all
        keySet.forEach(key -> serverAbilityControlManager.disableCurrentNodeAbility(key));
        // get all
        keySet.forEach(key -> {
            Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning(key));
        });
        // enable all
        keySet.forEach(key -> serverAbilityControlManager.enableCurrentNodeAbility(key));
        // get all
        keySet.forEach(key -> {
            Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(key));
        });
    }
    
    class TestHandlerMapping implements HandlerMapping {

        @Override
        public void enable() {
            enabled++;
        }

        @Override
        public void disable() {
            enabled--;
        }

    }
    
}


