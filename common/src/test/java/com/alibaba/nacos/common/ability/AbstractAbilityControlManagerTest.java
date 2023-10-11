/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractAbilityControlManagerTest {
    
    private AbstractAbilityControlManager abilityControlManager;
    
    private Subscriber<AbstractAbilityControlManager.AbilityUpdateEvent> mockSubscriber;
    
    private boolean isOn = true;
    
    private AssertionError assertionError;
    
    private boolean notified = false;
    
    @Before
    public void setUp() throws Exception {
        mockSubscriber = new Subscriber<AbstractAbilityControlManager.AbilityUpdateEvent>() {
            @Override
            public void onEvent(AbstractAbilityControlManager.AbilityUpdateEvent event) {
                notified = true;
                try {
                    assertEquals(AbilityKey.SERVER_TEST_1, event.getAbilityKey());
                    assertEquals(isOn, event.isOn());
                    assertEquals(2, event.getAbilityTable().size());
                    assertEquals(isOn, event.getAbilityTable().get(AbilityKey.SERVER_TEST_1.getName()));
                } catch (AssertionError error) {
                    assertionError = error;
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return AbstractAbilityControlManager.AbilityUpdateEvent.class;
            }
        };
        abilityControlManager = new MockAbilityControlManager();
        NotifyCenter.registerSubscriber(mockSubscriber);
    }
    
    @After
    public void tearDown() throws Exception {
        NotifyCenter.deregisterSubscriber(mockSubscriber);
        assertionError = null;
        notified = false;
    }
    
    @Test
    public void testEnableCurrentNodeAbility() throws InterruptedException {
        isOn = true;
        abilityControlManager.enableCurrentNodeAbility(AbilityKey.SERVER_TEST_1);
        TimeUnit.MILLISECONDS.sleep(1100);
        assertTrue(notified);
        if (null != assertionError) {
            throw assertionError;
        }
    }
    
    @Test
    public void testDisableCurrentNodeAbility() throws InterruptedException {
        isOn = false;
        abilityControlManager.disableCurrentNodeAbility(AbilityKey.SERVER_TEST_1);
        TimeUnit.MILLISECONDS.sleep(1100);
        assertTrue(notified);
        if (null != assertionError) {
            throw assertionError;
        }
    }
    
    @Test
    public void testIsCurrentNodeAbilityRunning() {
        assertEquals(AbilityStatus.SUPPORTED,
                abilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_1));
        assertEquals(AbilityStatus.NOT_SUPPORTED,
                abilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_2));
        assertEquals(AbilityStatus.UNKNOWN,
                abilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SDK_CLIENT_TEST_1));
    }
    
    @Test
    public void testGetCurrentNodeAbilities() {
        Map<String, Boolean> actual = abilityControlManager.getCurrentNodeAbilities(AbilityMode.SERVER);
        assertEquals(2, actual.size());
        assertTrue(actual.containsKey(AbilityKey.SERVER_TEST_1.getName()));
        assertTrue(actual.containsKey(AbilityKey.SERVER_TEST_2.getName()));
        actual = abilityControlManager.getCurrentNodeAbilities(AbilityMode.SDK_CLIENT);
        assertTrue(actual.isEmpty());
    }
    
    @Test
    public void testGetPriority() {
        assertEquals(Integer.MIN_VALUE, abilityControlManager.getPriority());
    }
    
    @Test(expected = IllegalStateException.class)
    public void testInitFailed() {
        abilityControlManager = new AbstractAbilityControlManager() {
            @Override
            protected Map<AbilityMode, Map<AbilityKey, Boolean>> initCurrentNodeAbilities() {
                Map<AbilityKey, Boolean> abilities = Collections.singletonMap(AbilityKey.SDK_CLIENT_TEST_1, true);
                return Collections.singletonMap(AbilityMode.SERVER, abilities);
            }
    
            @Override
            public int getPriority() {
                return 0;
            }
        };
    }
    
    private static final class MockAbilityControlManager extends AbstractAbilityControlManager {
        
        @Override
        protected Map<AbilityMode, Map<AbilityKey, Boolean>> initCurrentNodeAbilities() {
            Map<AbilityKey, Boolean> abilities = new HashMap<>(2);
            abilities.put(AbilityKey.SERVER_TEST_1, true);
            abilities.put(AbilityKey.SERVER_TEST_2, false);
            return Collections.singletonMap(AbilityMode.SERVER, abilities);
        }
        
        @Override
        public int getPriority() {
            return Integer.MIN_VALUE;
        }
    }
}