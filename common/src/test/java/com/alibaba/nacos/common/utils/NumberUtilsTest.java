/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
 * Number utils.
 * @author zzq
 */
public class NumberUtilsTest {
    
    @Test
    public void testToInt() {
        Assert.assertEquals(0, NumberUtils.toInt(null));
        Assert.assertEquals(0, NumberUtils.toInt(StringUtils.EMPTY));
        Assert.assertEquals(1, NumberUtils.toInt("1"));
    }
    
    @Test
    public void testTestToInt() {
        Assert.assertEquals(1, NumberUtils.toInt(null, 1));
        Assert.assertEquals(1, NumberUtils.toInt("", 1));
        Assert.assertEquals(1, NumberUtils.toInt("1", 0));
    }
    
    @Test
    public void testToLong() {
        Assert.assertEquals(1L, NumberUtils.toLong(null, 1L));
        Assert.assertEquals(1L, NumberUtils.toLong("", 1L));
        Assert.assertEquals(1L, NumberUtils.toLong("1", 0L));
    }
    
    @Test
    public void testToDouble() {
        Assert.assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0);
        Assert.assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0);
        Assert.assertEquals(1.5d, NumberUtils.toDouble("1.5", 0.0d), 0);
    }
    
    @Test
    public void testIsDigits() {
        Assert.assertFalse(NumberUtils.isDigits(null));
        Assert.assertFalse(NumberUtils.isDigits(""));
        Assert.assertTrue(NumberUtils.isDigits("12345"));
        Assert.assertFalse(NumberUtils.isDigits("1234.5"));
        Assert.assertFalse(NumberUtils.isDigits("1ab"));
        Assert.assertFalse(NumberUtils.isDigits("abc"));
    }
    
    @Test
    public void testToFloatString() {
        Assert.assertEquals(NumberUtils.toFloat("-1.2345"), -1.2345f, 0);
        Assert.assertEquals(1.2345f, NumberUtils.toFloat("1.2345"), 0);
        Assert.assertEquals(0.0f, NumberUtils.toFloat("abc"), 0);
    
        Assert.assertEquals(NumberUtils.toFloat("-001.2345"), -1.2345f, 0);
        Assert.assertEquals(1.2345f, NumberUtils.toFloat("+001.2345"), 0);
        Assert.assertEquals(1.2345f, NumberUtils.toFloat("001.2345"), 0);
        Assert.assertEquals(0f, NumberUtils.toFloat("000.00"), 0);
    
        Assert.assertEquals(NumberUtils.toFloat(Float.MAX_VALUE + ""), Float.MAX_VALUE, 0);
        Assert.assertEquals(NumberUtils.toFloat(Float.MIN_VALUE + ""), Float.MIN_VALUE, 0);
        Assert.assertEquals(0.0f, NumberUtils.toFloat(""), 0);
        Assert.assertEquals(0.0f, NumberUtils.toFloat(null), 0);
    }
    
    @Test
    public void testToFloatStringString() {
        Assert.assertEquals(1.2345f, NumberUtils.toFloat("1.2345", 5.1f), 0);
        Assert.assertEquals(5.0f, NumberUtils.toFloat("a", 5.0f), 0);
        // LANG-1060
        Assert.assertEquals(5.0f, NumberUtils.toFloat("-001Z.2345", 5.0f), 0);
        Assert.assertEquals(5.0f, NumberUtils.toFloat("+001AB.2345", 5.0f), 0);
        Assert.assertEquals(5.0f, NumberUtils.toFloat("001Z.2345", 5.0f), 0);
    }
    
}
