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
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ability.register.AbstractAbilityRegistry;
import com.alibaba.nacos.api.ability.register.impl.ServerAbilities;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.core.ability.TestServerAbilityControlManager;
import com.alibaba.nacos.core.ability.control.ServerAbilityControlManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * test for ability in config.
 *
 * @author Daydreamer
 * @date 2022/9/3 12:27
 **/
public class AbilityConfigsTest {
    
    private MockEnvironment environment;
    
    private TestAbilityConfig abilityConfigs;
    
    private ServerAbilityControlManager serverAbilityControlManager;
    
    private Map<AbilityKey, Boolean> currentAbilities;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        abilityConfigs = new TestAbilityConfig();
        inject(abilityConfigs);
        serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.SERVER_TEST_1);
        serverAbilityControlManager.enableCurrentNodeAbility(AbilityKey.SERVER_TEST_2);
    }
    
    void inject(AbilityConfigs abilityConfigs) {
        TestServerAbilityControlManager serverAbilityControlManager = new TestServerAbilityControlManager();
        Map<String, Boolean> newTable = new HashMap<>();
        newTable.put(AbilityKey.SERVER_TEST_1.getName(), true);
        newTable.put(AbilityKey.SERVER_TEST_2.getName(), true);
        serverAbilityControlManager.setCurrentSupportingAbility(newTable);
        abilityConfigs.setAbilityHandlerRegistry(serverAbilityControlManager);
        this.serverAbilityControlManager = serverAbilityControlManager;
    }

    /**
     * fill field.
     *
     * @throws Exception ignore
     */
    public void fill() throws Exception {
        Field instanceField = ServerAbilities.class.getDeclaredField("INSTANCE");
        Field abilitiesField = AbstractAbilityRegistry.class.getDeclaredField("supportedAbilities");
        abilitiesField.setAccessible(true);
        instanceField.setAccessible(true);
        ServerAbilities serverAbilities = (ServerAbilities) instanceField.get(ServerAbilities.class);
        currentAbilities = (Map<AbilityKey, Boolean>) abilitiesField.get(serverAbilities);
        currentAbilities.put(AbilityKey.SERVER_TEST_1, true);
        currentAbilities.put(AbilityKey.SERVER_TEST_2, true);
    }
    
    @Test
    public void testLoadAbilities() throws Exception {
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_1.getName(), Boolean.TRUE.toString());
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_2.getName(), Boolean.FALSE.toString());
        // test load
        fill();
        ServerAbilityControlManager manager = new ServerAbilityControlManager();
        // config has higher priority
        Assert.assertEquals(manager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_1), AbilityStatus.SUPPORTED);
        Assert.assertNotEquals(manager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_2), AbilityStatus.SUPPORTED);
        // clear
        currentAbilities.clear();
    }
    
    @Test
    public void testInit() {
        Assert.assertEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_1), AbilityStatus.SUPPORTED);
        Assert.assertEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_2), AbilityStatus.SUPPORTED);
    }
    
    @Test
    public void testConfigChange() throws InterruptedException {
        // test no change
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_1.getName(), Boolean.TRUE.toString());
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_2.getName(), Boolean.TRUE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_1), AbilityStatus.SUPPORTED);
        Assert.assertEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_2), AbilityStatus.SUPPORTED);
        
        // test change
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_1.getName(), Boolean.FALSE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertNotEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_1), AbilityStatus.SUPPORTED);
        Assert.assertEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_2), AbilityStatus.SUPPORTED);
    
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_1.getName(), Boolean.TRUE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_1), AbilityStatus.SUPPORTED);
    
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_1.getName(), Boolean.FALSE.toString());
        environment.setProperty(AbilityConfigs.PREFIX + AbilityKey.SERVER_TEST_2.getName(), Boolean.FALSE.toString());
        abilityConfigs.onEvent(new ServerConfigChangeEvent());
        Assert.assertNotEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_1), AbilityStatus.SUPPORTED);
        Assert.assertNotEquals(serverAbilityControlManager.isCurrentNodeAbilityRunning(AbilityKey.SERVER_TEST_2), AbilityStatus.SUPPORTED);
    }
    
}
