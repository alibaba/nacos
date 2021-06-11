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

package com.alibaba.nacos.client.config.impl;

import org.junit.Assert;
import org.junit.Test;

public class LimiterTest {
    
    @Test
    public void testIsLimit() {
        String keyId = "a";
        Assert.assertFalse(Limiter.isLimit(keyId));
        long start = System.currentTimeMillis();
        for (int j = 0; j < 5; j++) {
            Assert.assertFalse(Limiter.isLimit(keyId));
        }
        long elapse = System.currentTimeMillis() - start;
        // assert  < limit 5qps
        Assert.assertTrue(Math.abs(1000 - elapse) < 20);
    }
}