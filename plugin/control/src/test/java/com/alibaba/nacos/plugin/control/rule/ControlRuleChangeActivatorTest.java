/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.rule;

import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.rule.storage.RuleStorageProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ControlRuleChangeActivatorTest {
    
    private String localRuleBaseDir;
    
    @BeforeEach
    void setUp() throws Exception {
        localRuleBaseDir = Files.createTempDirectory("nacos-control-rules").toString();
        resetControlConfigs();
        ControlConfigs.getInstance().setLocalRuleStorageBaseDir(localRuleBaseDir);
        resetRuleStorageProxy();
        resetControlManagerCenter();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        resetControlConfigs();
        resetControlManagerCenter();
        resetRuleStorageProxy();
    }
    
    @Test
    void testTpsRuleSubscriberHandlesNullPointAndNoExternalStorage() {
        ControlRuleChangeActivator.TpsRuleChangeSubscriber subscriber =
            new ControlRuleChangeActivator.TpsRuleChangeSubscriber();
        
        subscriber.onEvent(new TpsControlRuleChangeEvent(null, false));
        subscriber.onEvent(new TpsControlRuleChangeEvent("emptyTpsRule", true));
        
        assertNotNull(ControlManagerCenter.getInstance().getTpsControlManager());
    }
    
    @Test
    void testTpsRuleSubscriberIgnoresInvalidLocalRule() throws Exception {
        RuleStorageProxy.getInstance().getLocalDiskStorage().saveTpsRule("invalidTpsRule", "{");
        ControlRuleChangeActivator.TpsRuleChangeSubscriber subscriber =
            new ControlRuleChangeActivator.TpsRuleChangeSubscriber();
        
        subscriber.onEvent(new TpsControlRuleChangeEvent("invalidTpsRule", false));
        
        assertNotNull(ControlManagerCenter.getInstance().getTpsControlManager());
    }
    
    @Test
    void testConnectionRuleSubscriberHandlesNoExternalStorageAndInvalidRule() throws Exception {
        ControlRuleChangeActivator.ConnectionRuleChangeSubscriber subscriber =
            new ControlRuleChangeActivator.ConnectionRuleChangeSubscriber();
        
        subscriber.onEvent(new ConnectionLimitRuleChangeEvent(true));
        RuleStorageProxy.getInstance().getLocalDiskStorage().saveConnectionRule("{");
        subscriber.onEvent(new ConnectionLimitRuleChangeEvent(false));
        
        assertNotNull(ControlManagerCenter.getInstance().getConnectionControlManager());
    }
    
    private void resetControlConfigs() throws Exception {
        Field instance = ControlConfigs.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }
    
    private void resetControlManagerCenter() throws Exception {
        Field instance = ControlManagerCenter.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }
    
    private void resetRuleStorageProxy() throws Exception {
        Field instance = RuleStorageProxy.class.getDeclaredField("INSTANCE");
        Constructor<RuleStorageProxy> constructor = RuleStorageProxy.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        setStaticFinalField(instance, constructor.newInstance());
    }
    
    private void setStaticFinalField(Field finalField, Object value)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getDeclaredFields0 =
            Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
        getDeclaredFields0.setAccessible(true);
        Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
        Field modifiers = null;
        for (Field each : fields) {
            if ("modifiers".equals(each.getName())) {
                modifiers = each;
            }
        }
        modifiers.setAccessible(true);
        modifiers.setInt(finalField, finalField.getModifiers() & ~Modifier.FINAL);
        finalField.setAccessible(true);
        finalField.set(null, value);
    }
}
