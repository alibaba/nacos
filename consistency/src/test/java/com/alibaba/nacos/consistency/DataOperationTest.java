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

package com.alibaba.nacos.consistency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataOperationTest {
    
    @Test
    void testValues() {
        DataOperation[] values = DataOperation.values();
        assertEquals(6, values.length);
    }
    
    @Test
    void testValueOf() {
        assertEquals(DataOperation.ADD, DataOperation.valueOf("ADD"));
        assertEquals(DataOperation.CHANGE, DataOperation.valueOf("CHANGE"));
        assertEquals(DataOperation.DELETE, DataOperation.valueOf("DELETE"));
        assertEquals(DataOperation.VERIFY, DataOperation.valueOf("VERIFY"));
        assertEquals(DataOperation.SNAPSHOT, DataOperation.valueOf("SNAPSHOT"));
        assertEquals(DataOperation.QUERY, DataOperation.valueOf("QUERY"));
    }
}
