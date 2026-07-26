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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test ArrayUtils.
 *
 * @author zzq
 */
class ArrayUtilsTest {
    
    Integer[] nullArr = null;
    
    Integer[] nothingArr = new Integer[] {};
    
    @Test
    void testisEmpty() {
        Integer[] arr = new Integer[] {1, 2};
        assertTrue(ArrayUtils.isEmpty(nullArr));
        assertTrue(ArrayUtils.isEmpty(nothingArr));
        assertFalse(ArrayUtils.isEmpty(arr));
    }
    
    @Test
    void contains() {
        assertFalse(ArrayUtils.contains(nullArr, "a"));
        assertFalse(ArrayUtils.contains(nullArr, null));
        assertFalse(ArrayUtils.contains(nothingArr, "b"));
        Integer[] arr = new Integer[] {1, 2, 3};
        assertFalse(ArrayUtils.contains(arr, null));
        Integer[] arr1 = new Integer[] {1, 2, 3, null};
        assertTrue(ArrayUtils.contains(arr1, null));
        assertTrue(ArrayUtils.contains(arr, 1));
        assertFalse(ArrayUtils.contains(arr, "1"));
    }
}
