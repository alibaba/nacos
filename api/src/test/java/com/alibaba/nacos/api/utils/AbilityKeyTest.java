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

package com.alibaba.nacos.api.utils;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * .
 *
 * @author Daydreamer
 * @description Ability key test
 * @date 2022/9/8 12:27
 **/
class AbilityKeyTest {
    
    @Test
    void testMapStr() {
        Map<AbilityKey, Boolean> enumMap = new HashMap<>();
        Map<String, Boolean> stringBooleanMap = AbilityKey.mapStr(enumMap);
        assertEquals(0, stringBooleanMap.size());
        
        enumMap.put(AbilityKey.SERVER_TEST_1, true);
        enumMap.put(AbilityKey.SERVER_TEST_2, false);
        enumMap.put(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC, false);
        stringBooleanMap = AbilityKey.mapStr(enumMap);
        assertEquals(3, stringBooleanMap.size());
        assertTrue(stringBooleanMap.get(AbilityKey.SERVER_TEST_1.getName()));
        assertFalse(stringBooleanMap.get(AbilityKey.SERVER_TEST_2.getName()));
        assertFalse(stringBooleanMap.get(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC.getName()));
        
        enumMap.put(AbilityKey.SERVER_TEST_2, true);
        enumMap.put(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC, true);
        stringBooleanMap = AbilityKey.mapStr(enumMap);
        assertEquals(3, stringBooleanMap.size());
        assertTrue(stringBooleanMap.get(AbilityKey.SERVER_TEST_1.getName()));
        assertTrue(stringBooleanMap.get(AbilityKey.SERVER_TEST_2.getName()));
        assertTrue(stringBooleanMap.get(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC.getName()));
    }
    
    @Test
    void testMapEnumForEmpty() {
        Map<AbilityKey, Boolean> actual = AbilityKey.mapEnum(AbilityMode.SERVER, Collections.emptyMap());
        assertTrue(actual.isEmpty());
    }
    
    @Test
    void testMapEnum() {
        Map<String, Boolean> mapStr = new HashMap<>();
        mapStr.put("test-no-existed", true);
        Map<AbilityKey, Boolean> enumMap = AbilityKey.mapEnum(AbilityMode.SERVER, mapStr);
        assertEquals(0, enumMap.size());
        
        mapStr.put(AbilityKey.SERVER_TEST_2.getName(), false);
        mapStr.put(AbilityKey.SERVER_TEST_1.getName(), true);
        mapStr.put(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC.getName(), true);
        enumMap = AbilityKey.mapEnum(AbilityMode.SERVER, mapStr);
        assertFalse(enumMap.get(AbilityKey.SERVER_TEST_2));
        assertTrue(enumMap.get(AbilityKey.SERVER_TEST_1));
        assertTrue(enumMap.get(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC));
        
        mapStr.clear();
        mapStr.put(AbilityKey.SERVER_TEST_2.getName(), true);
        mapStr.put(AbilityKey.SERVER_TEST_1.getName(), true);
        mapStr.put(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC.getName(), true);
        enumMap = AbilityKey.mapEnum(AbilityMode.SERVER, mapStr);
        assertTrue(enumMap.get(AbilityKey.SERVER_TEST_2));
        assertTrue(enumMap.get(AbilityKey.SERVER_TEST_1));
        assertTrue(enumMap.get(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC));
        
    }
    
    @Test
    void testGetAllValues() {
        Collection<AbilityKey> actual = AbilityKey.getAllValues(AbilityMode.SERVER);
        assertEquals(3, actual.size());
        actual = AbilityKey.getAllValues(AbilityMode.SDK_CLIENT);
        assertEquals(1, actual.size());
        actual = AbilityKey.getAllValues(AbilityMode.CLUSTER_CLIENT);
        assertEquals(1, actual.size());
    }
    
    @Test
    void testGetAllNames() {
        Collection<String> actual = AbilityKey.getAllNames(AbilityMode.SERVER);
        assertEquals(3, actual.size());
        actual = AbilityKey.getAllNames(AbilityMode.SDK_CLIENT);
        assertEquals(1, actual.size());
        actual = AbilityKey.getAllNames(AbilityMode.CLUSTER_CLIENT);
        assertEquals(1, actual.size());
    }
    
    @Test
    void testGetDescription() {
        assertEquals("just for junit test", AbilityKey.SERVER_TEST_1.getDescription());
    }
}
