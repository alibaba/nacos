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

package com.alibaba.nacos.common.ability.discover;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class NacosAbilityManagerHolderTest {
    
    @Before
    public void setUp() throws Exception {
        NacosAbilityManagerHolder.getInstance();
    }
    
    @After
    public void tearDown() throws Exception {
        Field abilityControlManagerField = NacosAbilityManagerHolder.class
                .getDeclaredField("abstractAbilityControlManager");
        abilityControlManagerField.setAccessible(true);
        abilityControlManagerField.set(null, null);
    }
    
    @Test
    public void testGetInstance() {
        assertNotNull(NacosAbilityManagerHolder.getInstance());
    }
    
    @Test
    public void testGetInstanceByType() {
        assertNotNull(NacosAbilityManagerHolder.getInstance(HigherMockAbilityManager.class));
    }
    
    @Test(expected = ClassCastException.class)
    public void testGetInstanceByWrongType() {
        assertNotNull(NacosAbilityManagerHolder.getInstance(LowerMockAbilityManager.class));
    }
}