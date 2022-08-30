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

package com.alibaba.nacos.test.ability;

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

    private TestClientAbilityControlManager clientAbilityControlManager = new TestClientAbilityControlManager();

    private TestServerAbilityControlManager serverAbilityControlManager = new TestServerAbilityControlManager();

    private volatile int clusterEnabled = 0;

    private volatile int enabled = 0;
    
    private volatile LinkedList<String> testPriority = new LinkedList<>();

    @Before
    public void inject() {
        Map<String, Boolean> newTable = new HashMap<>();
        newTable.put("stop-raft", true);
        clientAbilityControlManager.setCurrentSupportingAbility(newTable);

        Map<String, Boolean> table = new HashMap<>();
        table.put("stop-raft", true);
        serverAbilityControlManager.setCurrentSupportingAbility(table);

        Map<String, Boolean> cluster = new HashMap<>();
        cluster.put("stop-raft", true);
        serverAbilityControlManager.setClusterAbility(cluster);
        serverAbilityControlManager.setCurrentSupportingAbility(newTable);
    }
    
    @Test
    public void testClientAdd() {
        Map<String, Boolean> newTable = new HashMap<>();
        newTable.put("test-no-existed", true);
        newTable.put("stop-raft", true);
        AbilityTable table = new AbilityTable();
        table.setConnectionId("test-00001");
        table.setAbility(newTable);
        table.setServer(true);
        clientAbilityControlManager.addNewTable(table);
        Assert.assertFalse(clientAbilityControlManager.isSupport("test-00001", "test-no-existed"));
        Assert.assertTrue(clientAbilityControlManager.isSupport("test-00001", "stop-raft"));
    }
    
    @Test
    public void testServerAdd() {
        Map<String, Boolean> newTable = new HashMap<>();
        newTable.put("test-no-existed", true);
        newTable.put("stop-raft", true);
        AbilityTable table = new AbilityTable();
        table.setConnectionId("test-00001");
        table.setAbility(newTable);
        table.setServer(true);
        serverAbilityControlManager.addNewTable(table);
        Assert.assertFalse(serverAbilityControlManager.isSupport("test-00001", "test-no-existed"));
        Assert.assertTrue(serverAbilityControlManager.isSupport("test-00001", "stop-raft"));
        Assert.assertTrue(serverAbilityControlManager.isClusterEnableAbility("stop-raft"));

        Map<String, Boolean> otherServer = new HashMap<>();
        otherServer.put("test-no-existed", true);
        otherServer.put("stop-raft", false);
        AbilityTable otherServerTable = new AbilityTable();
        otherServerTable.setConnectionId("test-00000");
        otherServerTable.setAbility(otherServer);
        otherServerTable.setServer(true);
        serverAbilityControlManager.addNewTable(otherServerTable);
        Assert.assertFalse(serverAbilityControlManager.isClusterEnableAbility("stop-raft"));

        Map<String, Boolean> clientTa = new HashMap<>();
        clientTa.put("test-no-existed", true);
        clientTa.put("stop-raft", false);
        AbilityTable clientTable = new AbilityTable();
        clientTable.setConnectionId("test-00002");
        clientTable.setAbility(clientTa);
        clientTable.setServer(false);
        serverAbilityControlManager.addNewTable(clientTable);
        Assert.assertFalse(serverAbilityControlManager.isClusterEnableAbility("stop-raft"));
    }
    
    @Test
    public void testClientRemove() {
        Map<String, Boolean> clientTa = new HashMap<>();
        clientTa.put("test-no-existed", true);
        clientTa.put("stop-raft", false);
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
        serverAbilityControlManager.registerComponent("stop-raft", new TestHandlerMapping(), -1);
        Assert.assertEquals(1, serverAbilityControlManager.handlerMappingCount());

        serverAbilityControlManager.enableCurrentNodeAbility("stop-raft");
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // nothing happens if it has enabled
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning("stop-raft"));

        // invoke disable()
        serverAbilityControlManager.disableCurrentNodeAbility("stop-raft");
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // disable will invoke handler
        Assert.assertEquals(enabled, 0);
        Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning("stop-raft"));

        serverAbilityControlManager.disableCurrentNodeAbility("stop-raft");
        // wait for invoking handler asyn
        Thread.sleep(200L);
        // nothing to do because it has disable
        Assert.assertEquals(enabled, 0);
        Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning("stop-raft"));

        serverAbilityControlManager.enableCurrentNodeAbility("stop-raft");
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning("stop-raft"));

        serverAbilityControlManager.enableCurrentNodeAbility("stop-raft");
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertEquals(enabled, 1);
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning("stop-raft"));
    }

    @Test
    public void testClusterComponent() throws InterruptedException {
        clusterEnabled = 0;
        // invoke enable() because it turn on
        serverAbilityControlManager.registerComponentForCluster("stop-raft", new ClusterHandlerMapping(), -1);
        Assert.assertEquals(1, serverAbilityControlManager.clusterHandlerMappingCount());
        Assert.assertTrue(serverAbilityControlManager.isClusterEnableAbility("stop-raft"));
        Assert.assertEquals(clusterEnabled, 1);

        Map<String, Boolean> serverAbility = new HashMap<>();
        serverAbility.put("test-no-existed", true);
        serverAbility.put("stop-raft", false);
        AbilityTable serverTable = new AbilityTable();
        serverTable.setConnectionId("test-01111");
        serverTable.setAbility(serverAbility);
        serverTable.setServer(true);
        serverAbilityControlManager.addNewTable(serverTable);
        // wait for invoking handler asyn
        Thread.sleep(200L);

        // disabled
        Assert.assertFalse(serverAbilityControlManager.isClusterEnableAbility("stop-raft"));
        Assert.assertEquals(clusterEnabled, 0);

        // remove this table to enabled
        serverAbilityControlManager.removeTable("test-01111");
        // wait for invoking handler asyn
        Thread.sleep(200L);
        Assert.assertTrue(serverAbilityControlManager.isClusterEnableAbility("stop-raft"));
        Assert.assertEquals(clusterEnabled, 1);
    }
    
    @Test
    public void testCurrentNodeAbility() {
        Set<String> keySet = serverAbilityControlManager.getCurrentRunningAbility().keySet();
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
        String key = "key";
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


