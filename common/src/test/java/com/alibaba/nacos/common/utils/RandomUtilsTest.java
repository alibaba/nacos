/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * test RandomUtils.
 * @author zzq
 */
public class RandomUtilsTest {
    
    @Test
    public void nextLong() {
        final long result = RandomUtils.nextLong(1L, 199L);
        Assert.assertTrue(result >= 1L && result < 199L);
    }
    
    @Test
    public void nextInt() {
        final int result = RandomUtils.nextInt(1, 199);
        Assert.assertTrue(result >= 1 && result < 199);
    }
}
