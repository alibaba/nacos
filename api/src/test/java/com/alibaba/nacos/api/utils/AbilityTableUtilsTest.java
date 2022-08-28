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

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AbilityTableUtilsTest {

    @Test
    public void testGetByteArray() {
        Map<String, Integer> offset = new HashMap<>();
        offset.put("a", 1);
        offset.put("b", 2);
        offset.put("c", 10);
        offset.put("d", 127);
        byte[] abilityBitBy = AbilityTableUtils.getAbilityBitBy(offset.values());
        Assert.assertEquals(16, abilityBitBy.length);
        Assert.assertEquals((byte) (3 << 6), abilityBitBy[0]);
        Assert.assertEquals((byte) (1 << 6), abilityBitBy[1]);
    }

    @Test
    public void testGetAbilityTable() {
        Map<String, Integer> offset = new HashMap<>();
        offset.put("a", 1);
        offset.put("b", 2);
        offset.put("c", 10);
        offset.put("d", 127);
        byte[] abilityBitBy = AbilityTableUtils.getAbilityBitBy(offset.values());
        Map<String, Boolean> abilityTableBy = AbilityTableUtils.getAbilityTableBy(abilityBitBy, offset);
        Assert.assertEquals(4, abilityTableBy.size());
        Assert.assertEquals(Boolean.TRUE, abilityTableBy.get("a"));
        Assert.assertEquals(Boolean.TRUE, abilityTableBy.get("b"));
        Assert.assertEquals(Boolean.TRUE, abilityTableBy.get("c"));
        Assert.assertEquals(Boolean.TRUE, abilityTableBy.get("d"));
        Assert.assertEquals(Boolean.FALSE, abilityTableBy.getOrDefault("asdasd", false));
    }
}
