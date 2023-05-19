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
 * Test ArrayUtils.
 * @author zzq
 */
public class ArrayUtilsTest {
    
    Integer[] nullArr = null;
    
    Integer[] nothingArr = new Integer[]{};
    
    @Test
    public void testisEmpty() {
        Integer[] arr = new Integer[]{1, 2};
        Assert.assertTrue(ArrayUtils.isEmpty(nullArr));
        Assert.assertTrue(ArrayUtils.isEmpty(nothingArr));
        Assert.assertFalse(ArrayUtils.isEmpty(arr));
    }
    
    @Test
    public void contains() {
        Integer[] arr = new Integer[]{1, 2, 3};
        Integer[] arr1 = new Integer[]{1, 2, 3, null};
        Assert.assertFalse(ArrayUtils.contains(nullArr, "a"));
        Assert.assertFalse(ArrayUtils.contains(nullArr, null));
        Assert.assertFalse(ArrayUtils.contains(nothingArr, "b"));
        Assert.assertFalse(ArrayUtils.contains(arr, null));
        Assert.assertTrue(ArrayUtils.contains(arr1, null));
        Assert.assertTrue(ArrayUtils.contains(arr, 1));
        Assert.assertFalse(ArrayUtils.contains(arr, "1"));
    }
}
