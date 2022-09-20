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
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

@SpringBootTest
public class AbilityControlManagerTest {

    private TestServerAbilityControlManager serverAbilityControlManager = new TestServerAbilityControlManager();

    private volatile int enabled = 0;
    
    private volatile LinkedList<String> testPriority = new LinkedList<>();

    @Before
    public void inject() {
        Map<String, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.TEST_1.getName(), true);
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
        Set<String> keySet = serverAbilityControlManager.getCurrentNodeAbilities().keySet();
        // diable all
        keySet.forEach(key -> serverAbilityControlManager.disableCurrentNodeAbility(AbilityKey.getEnum(key)));
        // get all
        keySet.forEach(key -> {
            Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.getEnum(key)));
        });
        // enable all
        keySet.forEach(key -> serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.getEnum(key)));
        // get all
        keySet.forEach(key -> {
            Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.getEnum(key)));
        });
    }
    
    @Test
    public void testPriority() throws InterruptedException {
        TestServerAbilityControlManager testClientAbilityControlManager = new TestServerAbilityControlManager();
        AbilityKey key = AbilityKey.TEST_1;
        TestPriority handlerMapping1 = new TestPriority("1");
        TestPriority handlerMapping2 = new TestPriority("2");
        TestPriority handlerMapping3 = new TestPriority("3");
        // first one, invoke enable()
        testClientAbilityControlManager.registerComponent(key, handlerMapping2, 128);
        // last one, invoke enable()
        testClientAbilityControlManager.registerComponent(key, handlerMapping3);
        // second one, invoke enable()
        testClientAbilityControlManager.registerComponent(key, handlerMapping1, 12);
        // trigger
        testClientAbilityControlManager.trigger(key);
        Assert.assertEquals(3, testClientAbilityControlManager.getHandlerMapping(key).size());
        // wait for invoking
        Thread.sleep(200L);
        Assert.assertEquals("2", testPriority.poll());
        Assert.assertEquals("3", testPriority.poll());
        Assert.assertEquals("1", testPriority.poll());
        // here are priority
        Assert.assertEquals("2", testPriority.poll());
        Assert.assertEquals("1", testPriority.poll());
        Assert.assertEquals("3", testPriority.poll());
        
        // remove
        testClientAbilityControlManager.registerComponent(key, new TestHandlerMapping(), -1);
        Assert.assertEquals(4, testClientAbilityControlManager.getHandlerMapping(key).size());
        Assert.assertEquals(1, testClientAbilityControlManager.removeComponent(key, TestHandlerMapping.class));
        Assert.assertEquals(3, testClientAbilityControlManager.getHandlerMapping(key).size());
        testClientAbilityControlManager.removeAll(key);
        Assert.assertNull(testClientAbilityControlManager.getHandlerMapping(key));
    }
    
    class TestPriority implements HandlerMapping {
        
        String mark;
        
        public TestPriority(String mark) {
            // unique one
            this.mark = mark.intern();
        }
        
        @Override
        public void enable() {
            testPriority.offer(mark);
        }
        
        @Override
        public void disable() {
            testPriority.offer(mark);
        }
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


