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

package com.alibaba.nacos.core.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link StringPool} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 13:52
 */
public class StringPoolTest {
    
    @Test
    public void testStringPool() {
        String val1 = StringPool.get("test");
        Assert.assertEquals("test", val1);
        
        String val2 = StringPool.get(null);
        Assert.assertEquals(null, val2);
        
        long size1 = StringPool.size();
        Assert.assertEquals(1, size1);
        
        StringPool.remove("test");
        long size2 = StringPool.size();
        Assert.assertEquals(0, size2);
    }
}
