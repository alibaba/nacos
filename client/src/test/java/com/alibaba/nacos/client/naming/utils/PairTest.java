/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.naming.utils;

import org.junit.Assert;
import org.junit.Test;

public class PairTest {
    
    @Test
    public void testItem() {
        String item = "aa";
        double weight = 1.0;
        Pair<String> pair = new Pair<>(item, weight);
        Assert.assertEquals(weight, pair.weight(), 0.01);
        Assert.assertEquals(item, pair.item());
    }
}