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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AbilityTableUtilsTest {
    
    @Test
    public void testGetAbilityBitBy() {
        byte[] abilityBitBy = AbilityTableUtils.getAbilityBitBy(Arrays.asList(1, 8, 9, 17));
        Assert.assertEquals(abilityBitBy[0], -127);
        Assert.assertEquals(abilityBitBy[1], -128);
        Assert.assertEquals(abilityBitBy[2], -128);
        // clear
        byte[] abilityBits = AbilityTableUtils.getAbilityBitBy(Collections.emptyList());
        Assert.assertEquals(abilityBits.length, 1);
        Assert.assertEquals(abilityBits[0], 0);
    }
    
    @Test
    public void testGetAbilityTableBy() {
        byte[] bytes = new byte[]{0};
        Map<AbilityKey, Boolean> abilityTableBy =
                AbilityTableUtils.getAbilityTableBy(bytes, AbilityKey.offset());
        Assert.assertEquals(abilityTableBy.getOrDefault(AbilityKey.TEST_1, false), false);
        Assert.assertEquals(abilityTableBy.getOrDefault(AbilityKey.TEST_2, false), false);
        
        byte[] bytes1 = new byte[]{-64};
        Map<AbilityKey, Boolean> abilityTableBy1 =
                AbilityTableUtils.getAbilityTableBy(bytes1, AbilityKey.offset());
        Assert.assertEquals(abilityTableBy1.get(AbilityKey.TEST_1), true);
        Assert.assertEquals(abilityTableBy1.get(AbilityKey.TEST_2), true);
    
        byte[] bytes2 = new byte[]{-128};
        Map<AbilityKey, Boolean> abilityTableBy2 =
                AbilityTableUtils.getAbilityTableBy(bytes2, AbilityKey.offset());
        Assert.assertEquals(abilityTableBy2.getOrDefault(AbilityKey.TEST_1, false), true);
        Assert.assertEquals(abilityTableBy2.getOrDefault(AbilityKey.TEST_2, false), false);
    
        byte[] bytes3 = new byte[]{64};
        Map<AbilityKey, Boolean> abilityTableBy3 =
                AbilityTableUtils.getAbilityTableBy(bytes3, AbilityKey.offset());
        Assert.assertEquals(abilityTableBy3.getOrDefault(AbilityKey.TEST_1, false), false);
        Assert.assertEquals(abilityTableBy3.getOrDefault(AbilityKey.TEST_2, false), true);
    }
    
    @Test
    public void testGetAbilityBiTableBy() {
        Map<AbilityKey, Boolean> map = new HashMap<>();
        byte[] bytes1 = AbilityTableUtils.getAbilityBiTableBy(AbilityKey.values(), map);
        Assert.assertEquals(1, bytes1.length);
        Assert.assertEquals(bytes1[0], 0);
        
        map.put(AbilityKey.TEST_1, true);
        byte[] bytes2 = AbilityTableUtils.getAbilityBiTableBy(AbilityKey.values(), map);
        Assert.assertEquals(1, bytes1.length);
        Assert.assertEquals(bytes2[0], -128);
    
        map.put(AbilityKey.TEST_1, false);
        map.put(AbilityKey.TEST_2, true);
        byte[] bytes3 = AbilityTableUtils.getAbilityBiTableBy(AbilityKey.values(), map);
        Assert.assertEquals(1, bytes3.length);
        Assert.assertEquals(bytes3[0], 64);
    
        map.put(AbilityKey.TEST_1, true);
        byte[] bytes4 = AbilityTableUtils.getAbilityBiTableBy(AbilityKey.values(), map);
        Assert.assertEquals(1, bytes4.length);
        Assert.assertEquals(bytes4[0], -64);
    }
    
    @Test
    public void testGetAbilityBiTable() {
        Map<AbilityKey, Integer> offset = AbilityKey.offset();
        Map<AbilityKey, Boolean> abilities = new HashMap<>();
        byte[] bytes1 = AbilityTableUtils.getAbilityBiTableBy(offset, abilities);
        Assert.assertEquals(1, bytes1.length);
        Assert.assertEquals(bytes1[0], 0);
    
        abilities.put(AbilityKey.TEST_1, true);
        byte[] bytes2 = AbilityTableUtils.getAbilityBiTableBy(offset, abilities);
        Assert.assertEquals(1, bytes2.length);
        Assert.assertEquals(bytes2[0], -128);
    
        abilities.put(AbilityKey.TEST_2, true);
        byte[] bytes3 = AbilityTableUtils.getAbilityBiTableBy(offset, abilities);
        Assert.assertEquals(1, bytes3.length);
        Assert.assertEquals(bytes3[0], -64);
        
        offset = new HashMap<>();
        offset.put(AbilityKey.TEST_1, 2);
        byte[] bytes4 = AbilityTableUtils.getAbilityBiTableBy(offset, abilities);
        Assert.assertEquals(1, bytes4.length);
        Assert.assertEquals(bytes4[0], 64);
    }
    
}
