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

package com.alibaba.nacos.core.ability.config;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.common.ability.handler.HandlerMapping;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.core.ability.TestServerAbilityControlManager;
import com.alibaba.nacos.core.ability.control.ServerAbilityControlManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;

/**.
 * @author Daydreamer
 * @description
 * @date 2022/9/3 12:27
 **/
public class AbilityConfigsTest {
    
    private MockEnvironment environment;
    
    private TestAbilityConfig abilityConfigs;
    
    private int tmp;
    
    private ServerAbilityControlManager serverAbilityControlManager;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        abilityConfigs = new TestAbilityConfig();
        inject(abilityConfigs);
        serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_1);
        serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.TEST_2);
        serverAbilityControlManager.registerComponent(AbilityKey.TEST_1, new TestHandler());
        serverAbilityControlManager.registerComponent(AbilityKey.TEST_2, new TestHandler());
        // tmp is 2 now
    }
    
    void inject(AbilityConfigs abilityConfigs) {
        TestServerAbilityControlManager serverAbilityControlManager = new TestServerAbilityControlManager();
        Map<String, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.TEST_1.getName(), true);
        newTable.put(AbilityKey.TEST_2.getName(), true);
        serverAbilityControlManager.setCurrentSupportingAbility(newTable);
        abilityConfigs.setAbilityHandlerRegistry(serverAbilityControlManager);
        this.serverAbilityControlManager = serverAbilityControlManager;
    }
    
    @Test
    public void testInit() {
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_2));
    }
    
    @Test
    public void testConfigChange() throws InterruptedException {
        // test no change
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.TEST_1.getName(), Boolean.TRUE.toString());
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.TEST_2.getName(), Boolean.TRUE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_2));
        //wait for invoke
        Thread.sleep(100);
        Assert.assertEquals(tmp, 2);
        
        // test change
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.TEST_1.getName(), Boolean.FALSE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_2));
        //wait for invoke
        Thread.sleep(100);
        Assert.assertEquals(tmp, 1);
    
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.TEST_1.getName(), Boolean.TRUE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertTrue(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
        //wait for invoke
        Thread.sleep(100);
        Assert.assertEquals(tmp, 2);
    
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.TEST_1.getName(), Boolean.FALSE.toString());
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.TEST_2.getName(), Boolean.FALSE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_1));
        Assert.assertFalse(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.TEST_2));
        //wait for invoke
        Thread.sleep(100);
        Assert.assertEquals(tmp, 0);
    }
    
    class TestHandler implements HandlerMapping {
    
        @Override
        public void enable() {
            tmp++;
        }
    
        @Override
        public void disable() {
            tmp--;
        }
    }
    
}
