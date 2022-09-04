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

        Map<AbilityKey, Boolean> otherServer = new HashMap<>();
        otherServer.put(AbilityKey.TEST_2, true);
        otherServer.put(AbilityKey.TEST_1, false);
        AbilityTable otherServerTable = new AbilityTable();
        otherServerTable.setConnectionId("test-00000");
        otherServerTable.setAbility(otherServer);
        otherServerTable.setServer(true);
        serverAbilityControlManager.addNewTable(otherServerTable);

        Map<AbilityKey, Boolean> clientTa = new HashMap<>();
        clientTa.put(AbilityKey.TEST_2, true);
        clientTa.put(AbilityKey.TEST_1, false);
        AbilityTable clientTable = new AbilityTable();
        clientTable.setConnectionId("test-00002");
        clientTable.setAbility(clientTa);
        clientTable.setServer(false);
        serverAbilityControlManager.addNewTable(clientTable);
        
        // if not support
        AbilityTable serverTable = new AbilityTable();
        serverTable.setConnectionId("test-001231");
        serverTable.setServer(true);
        serverAbilityControlManager.addNewTable(serverTable);
        // unknown because not support
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.UNKNOWN);
        Assert.assertEquals(serverAbilityControlManager.getServerNotSupportAbility().size(), 1);
        Assert.assertTrue(serverAbilityControlManager.getServerNotSupportAbility().contains("test-001231"));
        
        AbilityTable serverTable1 = new AbilityTable();
        serverTable1.setConnectionId("test-001231231");
        serverTable1.setServer(true);
        serverAbilityControlManager.addNewTable(serverTable1);
        // unknown because not support
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.UNKNOWN);
        Assert.assertEquals(serverAbilityControlManager.getServerNotSupportAbility().size(), 2);
        Assert.assertTrue(serverAbilityControlManager.getServerNotSupportAbility().contains("test-001231231"));
    
        // remove then support
        serverAbilityControlManager.removeTable("test-001231");
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.UNKNOWN);
        serverAbilityControlManager.removeTable("test-001231231");
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.NOT_SUPPORTED);
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
        
        // add node doesn't support ability table
        AbilityTable abilityTable = new AbilityTable();
        abilityTable.setServer(true);
        abilityTable.setConnectionId("adsadsa1");
        serverAbilityControlManager.addNewTable(abilityTable);
        // cluster abilities close
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.UNKNOWN);
        
        AbilityTable abilityTable1 = new AbilityTable();
        abilityTable1.setServer(true);
        abilityTable1.setConnectionId("adsadsa2");
        serverAbilityControlManager.addNewTable(abilityTable1);
        // cluster abilities still close
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.UNKNOWN);
        Assert.assertNull(serverAbilityControlManager.getClusterAbility());
    
        AbilityTable abilityTable2 = new AbilityTable();
        abilityTable2.setServer(true);
        abilityTable2.setConnectionId("adsadsa3");
        Map<AbilityKey, Boolean> clientTa = new HashMap<>();
        clientTa.put(AbilityKey.TEST_2, true);
        clientTa.put(AbilityKey.TEST_1, false);
        abilityTable2.setAbility(clientTa);
        serverAbilityControlManager.addNewTable(abilityTable2);
        // cluster abilities still close
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.UNKNOWN);
        Assert.assertNull(serverAbilityControlManager.getClusterAbility());
        
        // remove
        serverAbilityControlManager.removeTable("adsadsa3");
        serverAbilityControlManager.removeTable("adsadsa2");
        serverAbilityControlManager.removeTable("adsadsa1");
        // cluster abilities open
        Assert.assertEquals(serverAbilityControlManager.isClusterEnableAbilityNow(AbilityKey.TEST_1), AbilityStatus.SUPPORTED);
        Assert.assertNotNull(serverAbilityControlManager.getClusterAbility());
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


