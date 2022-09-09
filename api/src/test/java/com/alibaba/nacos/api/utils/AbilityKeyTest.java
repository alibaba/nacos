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
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**.
 * @author Daydreamer
 * @description Ability key test
 * @date 2022/9/8 12:27
 **/
public class AbilityKeyTest {
    
    @Test
    public void testMapStr() {
        Map<AbilityKey, Boolean> enumMap = new HashMap<>();
        Map<String, Boolean> stringBooleanMap = AbilityKey.mapStr(enumMap);
        Assert.assertEquals(0, stringBooleanMap.size());
        
        enumMap.put(AbilityKey.TEST_1, true);
        enumMap.put(AbilityKey.TEST_2, false);
        stringBooleanMap = AbilityKey.mapStr(enumMap);
        Assert.assertEquals(2, stringBooleanMap.size());
        Assert.assertTrue(stringBooleanMap.get(AbilityKey.TEST_1.getName()));
        Assert.assertFalse(stringBooleanMap.get(AbilityKey.TEST_2.getName()));
        
        enumMap.put(AbilityKey.TEST_2, true);
        stringBooleanMap = AbilityKey.mapStr(enumMap);
        Assert.assertEquals(2, stringBooleanMap.size());
        Assert.assertTrue(stringBooleanMap.get(AbilityKey.TEST_1.getName()));
        Assert.assertTrue(stringBooleanMap.get(AbilityKey.TEST_2.getName()));
    }
    
    @Test
    public void testMapEnum() {
        Map<String, Boolean> mapStr = new HashMap<>();
        mapStr.put("test-no-existed", true);
        Map<AbilityKey, Boolean> enumMap = AbilityKey.mapEnum(mapStr);
        Assert.assertEquals(0, enumMap.size());
        
        mapStr.put(AbilityKey.TEST_2.getName(), false);
        mapStr.put(AbilityKey.TEST_1.getName(), true);
        enumMap = AbilityKey.mapEnum(mapStr);
        Assert.assertFalse(enumMap.get(AbilityKey.TEST_2));
        Assert.assertTrue(enumMap.get(AbilityKey.TEST_1));
    
        mapStr.clear();
        mapStr.put(AbilityKey.TEST_2.getName(), true);
        mapStr.put(AbilityKey.TEST_1.getName(), true);
        enumMap = AbilityKey.mapEnum(mapStr);
        Assert.assertTrue(enumMap.get(AbilityKey.TEST_2));
        Assert.assertTrue(enumMap.get(AbilityKey.TEST_1));
        
    }
    
}
