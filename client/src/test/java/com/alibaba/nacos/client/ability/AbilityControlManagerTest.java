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

package com.alibaba.nacos.client.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ability.entity.AbilityTable;
import com.alibaba.nacos.common.ability.handler.HandlerMapping;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class AbilityControlManagerTest {
    
    private TestClientAbilityControlManager clientAbilityControlManager = new TestClientAbilityControlManager();
    
    private volatile int enabled = 0;
    
    private volatile LinkedList<String> testPriority = new LinkedList<>();
    
    @Before
    public void inject() {
        Map<AbilityKey, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.TEST_1, true);
        clientAbilityControlManager.setCurrentSupportingAbility(newTable);
    }
    
    @Test
    public void testClientAdd() {
        Map<AbilityKey, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.TEST_2, true);
        newTable.put(AbilityKey.TEST_1, true);
        AbilityTable table = new AbilityTable();
        table.setConnectionId("test-00001");
        table.setAbility(newTable);
        table.setServer(true);
        clientAbilityControlManager.addNewTable(table);
        Assert.assertEquals(AbilityStatus.NOT_SUPPORTED, clientAbilityControlManager.isSupport("test-00001", AbilityKey.TEST_2));
        Assert.assertEquals(AbilityStatus.SUPPORTED, clientAbilityControlManager.isSupport("test-00001", AbilityKey.TEST_1));
    }
    
    @Test
    public void testClientRemove() {
        Map<AbilityKey, Boolean> clientTa = new HashMap<>();
        clientTa.put(AbilityKey.TEST_2, true);
        clientTa.put(AbilityKey.TEST_1, false);
        AbilityTable clientTable = new AbilityTable();
        clientTable.setConnectionId("test-01111");
        clientTable.setAbility(clientTa);
        clientTable.setServer(true);
        clientAbilityControlManager.addNewTable(clientTable);
        Assert.assertTrue(clientAbilityControlManager.contains(clientTable.getConnectionId()));
        clientAbilityControlManager.removeTable("test-01111");
        Assert.assertFalse(clientAbilityControlManager.contains(clientTable.getConnectionId()));
    }
    
    @Test
    public void testComponent() throws InterruptedException {
        enabled = 0;
        // invoke enable() or disable() when registering
        clientAbilityControlManager.registerComponent(AbilityKey.TEST_1, new TestHandlerMapping(), -1);
        Assert.assertEquals(1, clientAbilityControlManager.handlerMappingCount());
    
        clientAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // nothing happens if it has enabled
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(clientAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
        
        // invoke disable()
        clientAbilityControlManager.disableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // disable will invoke handler
        Assert.assertEquals(enabled, 0);
        Assert.assertFalse(clientAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
    
        clientAbilityControlManager.disableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // nothing to do because it has disable
        Assert.assertEquals(enabled, 0);
        Assert.assertFalse(clientAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
    
        clientAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(clientAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
    
        clientAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_1);
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(clientAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
    }
    
    @Test
    public void testPriority() throws InterruptedException {
        TestClientAbilityControlManager testClientAbilityControlManager = new TestClientAbilityControlManager();
        AbilityKey key = AbilityKey.TEST_1;
        TestPriority clusterHandlerMapping1 = new TestPriority("1");
        TestPriority clusterHandlerMapping2 = new TestPriority("2");
        TestPriority clusterHandlerMapping3 = new TestPriority("3");
        // first one, invoke enable()
        testClientAbilityControlManager.registerComponent(key, clusterHandlerMapping2, 128);
        // last one, invoke enable()
        testClientAbilityControlManager.registerComponent(key, clusterHandlerMapping3);
        // second one, invoke enable()
        testClientAbilityControlManager.registerComponent(key, clusterHandlerMapping1, 12);
        // trigger cluster
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


