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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link StringPool} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 13:52
 */
class StringPoolTest {
    
    @Test
    void testStringPool() {
        String val1 = StringPool.get("test");
        assertEquals("test", val1);
        
        String val2 = StringPool.get(null);
        assertNull(val2);
        
        long size1 = StringPool.size();
        assertEquals(1, size1);
        
        StringPool.remove("test");
        long size2 = StringPool.size();
        assertEquals(0, size2);
    }
}
