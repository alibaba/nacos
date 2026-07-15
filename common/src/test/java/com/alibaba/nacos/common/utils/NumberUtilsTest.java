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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Number utils.
 *
 * @author zzq
 */
class NumberUtilsTest {
    
    @Test
    void testToInt() {
        assertEquals(0, NumberUtils.toInt(null));
        assertEquals(0, NumberUtils.toInt(StringUtils.EMPTY));
        assertEquals(1, NumberUtils.toInt("1"));
    }
    
    @Test
    void testTestToInt() {
        assertEquals(1, NumberUtils.toInt(null, 1));
        assertEquals(1, NumberUtils.toInt("", 1));
        assertEquals(1, NumberUtils.toInt("1", 0));
    }
    
    @Test
    void testToLong() {
        assertEquals(1L, NumberUtils.toLong(null, 1L));
        assertEquals(1L, NumberUtils.toLong("", 1L));
        assertEquals(1L, NumberUtils.toLong("1", 0L));
    }
    
    @Test
    void testToDouble() {
        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0);
        assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0);
        assertEquals(1.5d, NumberUtils.toDouble("1.5", 0.0d), 0);
    }
    
    @Test
    void testIsDigits() {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("1234.5"));
        assertFalse(NumberUtils.isDigits("1ab"));
        assertFalse(NumberUtils.isDigits("abc"));
    }
    
    @Test
    void testToFloatString() {
        assertEquals(NumberUtils.toFloat("-1.2345"), -1.2345f, 0);
        assertEquals(1.2345f, NumberUtils.toFloat("1.2345"), 0);
        assertEquals(0.0f, NumberUtils.toFloat("abc"), 0);
        
        assertEquals(NumberUtils.toFloat("-001.2345"), -1.2345f, 0);
        assertEquals(1.2345f, NumberUtils.toFloat("+001.2345"), 0);
        assertEquals(1.2345f, NumberUtils.toFloat("001.2345"), 0);
        assertEquals(0f, NumberUtils.toFloat("000.00"), 0);
        
        assertEquals(Float.MAX_VALUE, NumberUtils.toFloat(Float.MAX_VALUE + ""), 0);
        assertEquals(Float.MIN_VALUE, NumberUtils.toFloat(Float.MIN_VALUE + ""), 0);
        assertEquals(0.0f, NumberUtils.toFloat(""), 0);
        assertEquals(0.0f, NumberUtils.toFloat(null), 0);
    }
    
    @Test
    void testToFloatStringString() {
        assertEquals(1.2345f, NumberUtils.toFloat("1.2345", 5.1f), 0);
        assertEquals(5.0f, NumberUtils.toFloat("a", 5.0f), 0);
        // LANG-1060
        assertEquals(5.0f, NumberUtils.toFloat("-001Z.2345", 5.0f), 0);
        assertEquals(5.0f, NumberUtils.toFloat("+001AB.2345", 5.0f), 0);
        assertEquals(5.0f, NumberUtils.toFloat("001Z.2345", 5.0f), 0);
    }
    
}
