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
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ability.entity.AbilityTable;
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

    private volatile int clusterEnabled = 0;

    private volatile int enabled = 0;
    
    private volatile LinkedList<String> testPriority = new LinkedList<>();

    @Before
    public void inject() {
        Map<AbilityKey, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.TEST_1, true);
        serverAbilityControlManager.setCurrentSupportingAbility(newTable);

        Map<AbilityKey, Boolean> table = new HashMap<>();
        table.put(AbilityKey.TEST_1, true);
        serverAbilityControlManager.setCurrentSupportingAbility(table);

        Map<AbilityKey, Boolean> cluster = new HashMap<>();
        cluster.put(AbilityKey.TEST_1, true);
        serverAbilityControlManager.setClusterAbility(cluster);
        serverAbilityControlManager.setCurrentSupportingAbility(newTable);
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
        serverAbilityControlManager.addNewTable(table);
        Assert.assertEquals(AbilityStatus.NOT_SUPPORTED, serverAbilityControlManager.isSupport("test-00001", AbilityKey.TEST_2));
        Assert.assertEquals(AbilityStatus.SUPPORTED, serverAbilityControlManager.isSupport("test-00001", AbilityKey.TEST_1));
    }
    
    @Test
    public void testServerAdd() {
        Map<AbilityKey, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.TEST_2, true);
        newTable.put(AbilityKey.TEST_1, true);
        AbilityTable table = new AbilityTable();
        table.setConnectionId("test-00001");
        table.setAbility(newTable);
        table.setServer(true);
        serverAbilityControlManager.addNewTable(table);
        Assert.assertEquals(AbilityStatus.NOT_SUPPORTED, serverAbilityControlManager.isSupport("test-00001", AbilityKey.TEST_2));
        Assert.assertEquals(AbilityStatus.SUPPORTED, serverAbilityControlManager.isSupport("test-00001", AbilityKey.TEST_1));
        Assert.assertTrue(serverAbilityControlManager.isClusterEnableAbility(AbilityKey.TEST_1));

        Map<AbilityKey, Boolean> otherServer = new HashMap<>();
        otherServer.put(AbilityKey.TEST_2, true);
        otherServer.put(AbilityKey.TEST_1, false);
        AbilityTable otherServerTable = new AbilityTable();
        otherServerTable.setConnectionId("test-00000");
        otherServerTable.setAbility(otherServer);
        otherServerTable.setServer(true);
        serverAbilityControlManager.addNewTable(otherServerTable);
        Assert.assertFalse(serverAbilityControlManager.isClusterEnableAbility(AbilityKey.TEST_1));

        Map<AbilityKey, Boolean> clientTa = new HashMap<>();
        clientTa.put(AbilityKey.TEST_2, true);
        clientTa.put(AbilityKey.TEST_1, false);
        AbilityTable clientTable = new AbilityTable();
        clientTable.setConnectionId("test-00002");
        clientTable.setAbility(clientTa);
        clientTable.setServer(false);
        serverAbilityControlManager.addNewTable(clientTable);
        Assert.assertFalse(serverAbilityControlManager.isClusterEnableAbility(AbilityKey.TEST_1));
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
        serverAbilityControlManager.addNewTable(clientTable);
        Assert.assertTrue(serverAbilityControlManager.contains(clientTable.getConnectionId()));
        serverAbilityControlManager.removeTable("test-01111");
        Assert.assertFalse(serverAbilityControlManager.contains(clientTable.getConnectionId()));
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
    public void testClusterComponent() throws InterruptedException {
        clusterEnabled = 0;
        // invoke enable() because it turn on
        serverAbilityControlManager.registerComponentForCluster(AbilityKey.TEST_1, new ClusterHandlerMapping(), -1);
        Assert.assertEquals(1, serverAbilityControlManager.clusterHandlerMappingCount());
        Assert.assertTrue(serverAbilityControlManager.isClusterEnableAbility(AbilityKey.TEST_1));
        Assert.assertEquals(clusterEnabled, 1);

        Map<AbilityKey, Boolean> serverAbility = new HashMap<>();
        serverAbility.put(AbilityKey.TEST_2, true);
        serverAbility.put(AbilityKey.TEST_1, false);
        AbilityTable serverTable = new AbilityTable();
        serverTable.setConnectionId("test-01111");
        serverTable.setAbility(serverAbility);
        serverTable.setServer(true);
        serverAbilityControlManager.addNewTable(serverTable);
        // wait for invoking handler asyn
        Thread.sleep(200L);

        // disabled
        Assert.assertFalse(serverAbilityControlManager.isClusterEnableAbility(AbilityKey.TEST_1));
        Assert.assertEquals(clusterEnabled, 0);

        // remove this table to enabled
        serverAbilityControlManager.removeTable("test-01111");
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertTrue(serverAbilityControlManager.isClusterEnableAbility(AbilityKey.TEST_1));
        Assert.assertEquals(clusterEnabled, 1);
    }
    
    @Test
    public void testCurrentNodeAbility() {
        Set<AbilityKey> keySet = serverAbilityControlManager.getCurrentRunningAbility().keySet();
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
    
    @Test
    public void testPriority() throws InterruptedException {
        TestServerAbilityControlManager testServerAbilityControlManager = new TestServerAbilityControlManager();
        AbilityKey key = AbilityKey.TEST_1;
        TestPriority clusterHandlerMapping1 = new TestPriority("1");
        TestPriority clusterHandlerMapping2 = new TestPriority("2");
        TestPriority clusterHandlerMapping3 = new TestPriority("3");
        // first one, invoke enable()
        testServerAbilityControlManager.registerComponentForCluster(key, clusterHandlerMapping2, 128);
        // last one, invoke enable()
        testServerAbilityControlManager.registerComponentForCluster(key, clusterHandlerMapping3);
        // second one, invoke enable()
        testServerAbilityControlManager.registerComponentForCluster(key, clusterHandlerMapping1, 12);
        // trigger cluster
        testServerAbilityControlManager.triggerCluster(key);
        Assert.assertEquals(3, testServerAbilityControlManager.getClusterHandlerMapping(key).size());
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
        testServerAbilityControlManager.registerClusterHandlerMapping(key, new ClusterHandlerMapping(), -1);
        Assert.assertEquals(4, testServerAbilityControlManager.getClusterHandlerMapping(key).size());
        Assert.assertEquals(1, testServerAbilityControlManager.removeClusterComponent(key, ClusterHandlerMapping.class));
        Assert.assertEquals(3, testServerAbilityControlManager.getClusterHandlerMapping(key).size());
        testServerAbilityControlManager.removeAllForCluster(key);
        Assert.assertNull(testServerAbilityControlManager.getClusterHandlerMapping(key));
    
        // first one
        testServerAbilityControlManager.registerComponent(key, clusterHandlerMapping2, 128);
        // last one
        testServerAbilityControlManager.registerComponent(key, clusterHandlerMapping3);
        // second one
        testServerAbilityControlManager.registerComponent(key, clusterHandlerMapping1, 12);
        Assert.assertEquals(3, testServerAbilityControlManager.getHandlerMapping(key).size());
        // wait for invoking
        Thread.sleep(200L);
        // trigger
        testServerAbilityControlManager.trigger(key);
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
        testServerAbilityControlManager.registerComponent(key, new ClusterHandlerMapping(), -1);
        Assert.assertEquals(4, testServerAbilityControlManager.getHandlerMapping(key).size());
        Assert.assertEquals(1, testServerAbilityControlManager.removeComponent(key, ClusterHandlerMapping.class));
        Assert.assertEquals(3, testServerAbilityControlManager.getHandlerMapping(key).size());
        testServerAbilityControlManager.removeAll(key);
        Assert.assertNull(testServerAbilityControlManager.getClusterHandlerMapping(key));
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
    
    class ClusterHandlerMapping implements HandlerMapping {

        @Override
        public void enable() {
            clusterEnabled++;
        }

        @Override
        public void disable() {
            clusterEnabled--;
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


