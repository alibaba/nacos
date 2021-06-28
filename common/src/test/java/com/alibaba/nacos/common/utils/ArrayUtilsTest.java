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
 * @author zzq
 * @date 2021/6/28
 */
public class ArrayUtilsTest {
    
    @Test
    public void testisEmpty() {
        Integer[] nullArr = null;
        Integer[] nothingArr = new Integer[]{};
        Integer[] arr = new Integer[]{1,2};
        Assert.assertTrue(ArrayUtils.isEmpty(nullArr));
        Assert.assertTrue(ArrayUtils.isEmpty(nothingArr));
        Assert.assertFalse(ArrayUtils.isEmpty(arr));
    }
    
    @Test
    public void testtoPrimitive() {
        Boolean[] nullArr = null;
        Boolean[] nothingArr = new Boolean[]{};
        Boolean[] arr = new Boolean[]{Boolean.TRUE,Boolean.FALSE,Boolean.TRUE};
        Assert.assertNull(ArrayUtils.toPrimitive(nullArr));
        Assert.assertArrayEquals(new boolean[0], ArrayUtils.toPrimitive(nothingArr));
        Assert.assertArrayEquals(new boolean[]{true,false,true},ArrayUtils.toPrimitive(arr));
    }
    
    @Test
    public void testadd() {
        testadd1();
        Assert.assertArrayEquals(new String[]{"a"}, ArrayUtils.add(null, "a"));;
        Assert.assertArrayEquals(new String[]{"a", null}, ArrayUtils.add(new String[]{"a"}, null));
        Assert.assertArrayEquals(new String[]{"a", "b"},ArrayUtils.add(new String[]{"a"}, "b"));
        Assert.assertArrayEquals(new String[]{"a", "b", "c"},ArrayUtils.add(new String[]{"a", "b"}, "c"));
    }
    
    private void testadd1() {
        try {
            ArrayUtils.add(null, null);
        }catch (Exception ex) {
            if (!(ex instanceof  IllegalArgumentException)) {
                Assert.fail("unknown mistake");
            }
        }
    }
    
    @Test
    public void testaddAll() {
        testaddAll1();
        Assert.assertArrayEquals(new String[] {"a", "b"}, ArrayUtils.addAll(new String[] {"a", "b"}, null));
        Assert.assertArrayEquals(new String[] {"a", "b"}, ArrayUtils.addAll(null, new String[] {"a", "b"}));
        Assert.assertArrayEquals(new String[0], ArrayUtils.addAll(new String[0], new String[0]));
        Assert.assertArrayEquals(new String[]{null, null}, ArrayUtils.addAll(new String[]{null}, new String[]{null}));
        Assert.assertArrayEquals(new String[] {"a", "b", "c", "1", "2", "3"}, ArrayUtils.addAll(new String[]{"a", "b", "c"}, new String[]{"1", "2", "3"}));
    }
    
    private void testaddAll1() {
        try {
            ArrayUtils.addAll(null, null);
        }catch (Exception ex) {
            if (!(ex instanceof  IllegalArgumentException)) {
                Assert.fail("unknown mistake");
            }
        }
    }
    
    @Test
    public void testclone() {
        Assert.assertArrayEquals(null,ArrayUtils.clone(null));
        Assert.assertArrayEquals(new String[] {"a", "b"}, ArrayUtils.clone(new String[] {"a", "b"}));
        Assert.assertArrayEquals(new String[0], ArrayUtils.clone(new String[0]));
    }
    
}
