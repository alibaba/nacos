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

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of ConvertUtils.
 *
 * @author <a href="mailto:jifeng.sun@outlook.com">sunjifeng</a>
 */
class ConvertUtilsTest {
    
    @Test
    void testToInt() {
        // ConvertUtils.toInt(String)
        assertEquals(0, ConvertUtils.toInt("0"));
        assertEquals(-1, ConvertUtils.toInt("-1"));
        assertEquals(10, ConvertUtils.toInt("10"));
        assertEquals(Integer.MAX_VALUE, ConvertUtils.toInt(String.valueOf(Integer.MAX_VALUE)));
        assertEquals(Integer.MIN_VALUE, ConvertUtils.toInt(String.valueOf(Integer.MIN_VALUE)));
        assertEquals(0, ConvertUtils.toInt("notIntValue"));
        
        // ConvertUtils.toInt(String, Integer)
        assertEquals(0, ConvertUtils.toInt("0", 100));
        assertEquals(100, ConvertUtils.toInt(null, 100));
        assertEquals(100, ConvertUtils.toInt("null", 100));
        assertEquals(100, ConvertUtils.toInt("notIntValue", 100));
    }
    
    @Test
    void testToLong() {
        // ConvertUtils.toLong(Object)
        assertEquals(0L, ConvertUtils.toLong(new ArrayList<>()));
        assertEquals(10L, ConvertUtils.toLong((Object) 10L));
        
        // ConvertUtils.toLong(String)
        assertEquals(0L, ConvertUtils.toLong("0"));
        assertEquals(-1L, ConvertUtils.toLong("-1"));
        assertEquals(10L, ConvertUtils.toLong("10"));
        assertEquals(Long.MAX_VALUE, ConvertUtils.toLong(String.valueOf(Long.MAX_VALUE)));
        assertEquals(Long.MIN_VALUE, ConvertUtils.toLong(String.valueOf(Long.MIN_VALUE)));
        assertEquals(0L, ConvertUtils.toLong("notIntValue"));
        
        // ConvertUtils.toLong(String, Integer)
        assertEquals(0L, ConvertUtils.toLong("0", 100L));
        assertEquals(100L, ConvertUtils.toLong(null, 100L));
        assertEquals(100L, ConvertUtils.toLong("null", 100L));
        assertEquals(100L, ConvertUtils.toLong("notIntValue", 100L));
    }
    
    @Test
    void testToBoolean() {
        // ConvertUtils.toBoolean(String)
        assertTrue(ConvertUtils.toBoolean("true"));
        assertTrue(ConvertUtils.toBoolean("True"));
        assertTrue(ConvertUtils.toBoolean("TRUE"));
        assertFalse(ConvertUtils.toBoolean("false"));
        assertFalse(ConvertUtils.toBoolean("False"));
        assertFalse(ConvertUtils.toBoolean("FALSE"));
        assertFalse(ConvertUtils.toBoolean(null));
        assertFalse(ConvertUtils.toBoolean("notBoolean"));
        
        // ConvertUtils.toBoolean(String, boolean)
        assertFalse(ConvertUtils.toBoolean("", false));
        assertFalse(ConvertUtils.toBoolean(null, false));
        assertFalse(ConvertUtils.toBoolean("notBoolean", false));
        assertTrue(ConvertUtils.toBoolean("true", false));
    }
    
    @Test
    void testToBooleanObject() {
        assertTrue(ConvertUtils.toBooleanObject("T"));
        assertTrue(ConvertUtils.toBooleanObject("t"));
        assertTrue(ConvertUtils.toBooleanObject("Y"));
        assertTrue(ConvertUtils.toBooleanObject("y"));
        assertFalse(ConvertUtils.toBooleanObject("f"));
        assertFalse(ConvertUtils.toBooleanObject("F"));
        assertFalse(ConvertUtils.toBooleanObject("n"));
        assertFalse(ConvertUtils.toBooleanObject("N"));
        assertNull(ConvertUtils.toBooleanObject("a"));
        
        assertTrue(ConvertUtils.toBooleanObject("on"));
        assertTrue(ConvertUtils.toBooleanObject("oN"));
        assertTrue(ConvertUtils.toBooleanObject("On"));
        assertTrue(ConvertUtils.toBooleanObject("ON"));
        assertFalse(ConvertUtils.toBooleanObject("No"));
        assertFalse(ConvertUtils.toBooleanObject("NO"));
        assertNull(ConvertUtils.toBooleanObject("an"));
        assertNull(ConvertUtils.toBooleanObject("aN"));
        assertNull(ConvertUtils.toBooleanObject("oa"));
        assertNull(ConvertUtils.toBooleanObject("Oa"));
        assertNull(ConvertUtils.toBooleanObject("Na"));
        assertNull(ConvertUtils.toBooleanObject("na"));
        assertNull(ConvertUtils.toBooleanObject("aO"));
        assertNull(ConvertUtils.toBooleanObject("ao"));
        
        assertFalse(ConvertUtils.toBooleanObject("off"));
        assertFalse(ConvertUtils.toBooleanObject("ofF"));
        assertFalse(ConvertUtils.toBooleanObject("oFf"));
        assertFalse(ConvertUtils.toBooleanObject("oFF"));
        assertFalse(ConvertUtils.toBooleanObject("Off"));
        assertFalse(ConvertUtils.toBooleanObject("OfF"));
        assertFalse(ConvertUtils.toBooleanObject("OFf"));
        assertFalse(ConvertUtils.toBooleanObject("OFF"));
        assertTrue(ConvertUtils.toBooleanObject("yes"));
        assertTrue(ConvertUtils.toBooleanObject("yeS"));
        assertTrue(ConvertUtils.toBooleanObject("yEs"));
        assertTrue(ConvertUtils.toBooleanObject("yES"));
        assertTrue(ConvertUtils.toBooleanObject("Yes"));
        assertTrue(ConvertUtils.toBooleanObject("YeS"));
        assertTrue(ConvertUtils.toBooleanObject("YEs"));
        assertTrue(ConvertUtils.toBooleanObject("YES"));
        assertNull(ConvertUtils.toBooleanObject("ono"));
        assertNull(ConvertUtils.toBooleanObject("aes"));
        assertNull(ConvertUtils.toBooleanObject("aeS"));
        assertNull(ConvertUtils.toBooleanObject("aEs"));
        assertNull(ConvertUtils.toBooleanObject("aES"));
        assertNull(ConvertUtils.toBooleanObject("yas"));
        assertNull(ConvertUtils.toBooleanObject("yaS"));
        assertNull(ConvertUtils.toBooleanObject("Yas"));
        assertNull(ConvertUtils.toBooleanObject("YaS"));
        assertNull(ConvertUtils.toBooleanObject("yea"));
        assertNull(ConvertUtils.toBooleanObject("yEa"));
        assertNull(ConvertUtils.toBooleanObject("Yea"));
        assertNull(ConvertUtils.toBooleanObject("YEa"));
        assertNull(ConvertUtils.toBooleanObject("aff"));
        assertNull(ConvertUtils.toBooleanObject("afF"));
        assertNull(ConvertUtils.toBooleanObject("aFf"));
        assertNull(ConvertUtils.toBooleanObject("aFF"));
        assertNull(ConvertUtils.toBooleanObject("oaf"));
        assertNull(ConvertUtils.toBooleanObject("oaF"));
        assertNull(ConvertUtils.toBooleanObject("Oaf"));
        assertNull(ConvertUtils.toBooleanObject("OaF"));
        assertNull(ConvertUtils.toBooleanObject("Ofa"));
        assertNull(ConvertUtils.toBooleanObject("ofa"));
        assertNull(ConvertUtils.toBooleanObject("OFa"));
        assertNull(ConvertUtils.toBooleanObject("oFa"));
        
        assertTrue(ConvertUtils.toBooleanObject("true"));
        assertTrue(ConvertUtils.toBooleanObject("truE"));
        assertTrue(ConvertUtils.toBooleanObject("trUe"));
        assertTrue(ConvertUtils.toBooleanObject("trUE"));
        assertTrue(ConvertUtils.toBooleanObject("tRue"));
        assertTrue(ConvertUtils.toBooleanObject("tRuE"));
        assertTrue(ConvertUtils.toBooleanObject("tRUe"));
        assertTrue(ConvertUtils.toBooleanObject("tRUE"));
        assertTrue(ConvertUtils.toBooleanObject("True"));
        assertTrue(ConvertUtils.toBooleanObject("TruE"));
        assertTrue(ConvertUtils.toBooleanObject("TrUe"));
        assertTrue(ConvertUtils.toBooleanObject("TrUE"));
        assertTrue(ConvertUtils.toBooleanObject("TRue"));
        assertTrue(ConvertUtils.toBooleanObject("TRuE"));
        assertTrue(ConvertUtils.toBooleanObject("TRUe"));
        assertTrue(ConvertUtils.toBooleanObject("TRUE"));
        assertNull(ConvertUtils.toBooleanObject("Xrue"));
        assertNull(ConvertUtils.toBooleanObject("XruE"));
        assertNull(ConvertUtils.toBooleanObject("XrUe"));
        assertNull(ConvertUtils.toBooleanObject("XrUE"));
        assertNull(ConvertUtils.toBooleanObject("XRue"));
        assertNull(ConvertUtils.toBooleanObject("XRuE"));
        assertNull(ConvertUtils.toBooleanObject("XRUe"));
        assertNull(ConvertUtils.toBooleanObject("XRUE"));
        assertNull(ConvertUtils.toBooleanObject("tXue"));
        assertNull(ConvertUtils.toBooleanObject("tXuE"));
        assertNull(ConvertUtils.toBooleanObject("tXUe"));
        assertNull(ConvertUtils.toBooleanObject("tXUE"));
        assertNull(ConvertUtils.toBooleanObject("TXue"));
        assertNull(ConvertUtils.toBooleanObject("TXuE"));
        assertNull(ConvertUtils.toBooleanObject("TXUe"));
        assertNull(ConvertUtils.toBooleanObject("TXUE"));
        assertNull(ConvertUtils.toBooleanObject("trXe"));
        assertNull(ConvertUtils.toBooleanObject("trXE"));
        assertNull(ConvertUtils.toBooleanObject("tRXe"));
        assertNull(ConvertUtils.toBooleanObject("tRXE"));
        assertNull(ConvertUtils.toBooleanObject("TrXe"));
        assertNull(ConvertUtils.toBooleanObject("TrXE"));
        assertNull(ConvertUtils.toBooleanObject("TRXe"));
        assertNull(ConvertUtils.toBooleanObject("TRXE"));
        assertNull(ConvertUtils.toBooleanObject("truX"));
        assertNull(ConvertUtils.toBooleanObject("trUX"));
        assertNull(ConvertUtils.toBooleanObject("tRuX"));
        assertNull(ConvertUtils.toBooleanObject("tRUX"));
        assertNull(ConvertUtils.toBooleanObject("TruX"));
        assertNull(ConvertUtils.toBooleanObject("TrUX"));
        assertNull(ConvertUtils.toBooleanObject("TRuX"));
        assertNull(ConvertUtils.toBooleanObject("TRUX"));
        
        assertFalse(ConvertUtils.toBooleanObject("false"));
        assertFalse(ConvertUtils.toBooleanObject("falsE"));
        assertFalse(ConvertUtils.toBooleanObject("falSe"));
        assertFalse(ConvertUtils.toBooleanObject("falSE"));
        assertFalse(ConvertUtils.toBooleanObject("faLse"));
        assertFalse(ConvertUtils.toBooleanObject("faLsE"));
        assertFalse(ConvertUtils.toBooleanObject("faLSe"));
        assertFalse(ConvertUtils.toBooleanObject("faLSE"));
        assertFalse(ConvertUtils.toBooleanObject("fAlse"));
        assertFalse(ConvertUtils.toBooleanObject("fAlsE"));
        assertFalse(ConvertUtils.toBooleanObject("fAlSe"));
        assertFalse(ConvertUtils.toBooleanObject("fAlSE"));
        assertFalse(ConvertUtils.toBooleanObject("fALse"));
        assertFalse(ConvertUtils.toBooleanObject("fALsE"));
        assertFalse(ConvertUtils.toBooleanObject("fALSe"));
        assertFalse(ConvertUtils.toBooleanObject("fALSE"));
        assertFalse(ConvertUtils.toBooleanObject("False"));
        assertFalse(ConvertUtils.toBooleanObject("FalsE"));
        assertFalse(ConvertUtils.toBooleanObject("FalSe"));
        assertFalse(ConvertUtils.toBooleanObject("FalSE"));
        assertFalse(ConvertUtils.toBooleanObject("FaLse"));
        assertFalse(ConvertUtils.toBooleanObject("FaLsE"));
        assertFalse(ConvertUtils.toBooleanObject("FaLSe"));
        assertFalse(ConvertUtils.toBooleanObject("FaLSE"));
        assertFalse(ConvertUtils.toBooleanObject("FAlse"));
        assertFalse(ConvertUtils.toBooleanObject("FAlsE"));
        assertFalse(ConvertUtils.toBooleanObject("FAlSe"));
        assertFalse(ConvertUtils.toBooleanObject("FAlSE"));
        assertFalse(ConvertUtils.toBooleanObject("FALse"));
        assertFalse(ConvertUtils.toBooleanObject("FALsE"));
        assertFalse(ConvertUtils.toBooleanObject("FALSe"));
        assertFalse(ConvertUtils.toBooleanObject("FALSE"));
        assertNull(ConvertUtils.toBooleanObject("Xalse"));
        assertNull(ConvertUtils.toBooleanObject("XalsE"));
        assertNull(ConvertUtils.toBooleanObject("XalSe"));
        assertNull(ConvertUtils.toBooleanObject("XalSE"));
        assertNull(ConvertUtils.toBooleanObject("XaLse"));
        assertNull(ConvertUtils.toBooleanObject("XaLsE"));
        assertNull(ConvertUtils.toBooleanObject("XaLSe"));
        assertNull(ConvertUtils.toBooleanObject("XaLSE"));
        assertNull(ConvertUtils.toBooleanObject("XAlse"));
        assertNull(ConvertUtils.toBooleanObject("XAlsE"));
        assertNull(ConvertUtils.toBooleanObject("XAlSe"));
        assertNull(ConvertUtils.toBooleanObject("XAlSE"));
        assertNull(ConvertUtils.toBooleanObject("XALse"));
        assertNull(ConvertUtils.toBooleanObject("XALsE"));
        assertNull(ConvertUtils.toBooleanObject("XALSe"));
        assertNull(ConvertUtils.toBooleanObject("XALSE"));
        assertNull(ConvertUtils.toBooleanObject("fXlse"));
        assertNull(ConvertUtils.toBooleanObject("fXlsE"));
        assertNull(ConvertUtils.toBooleanObject("fXlSe"));
        assertNull(ConvertUtils.toBooleanObject("fXlSE"));
        assertNull(ConvertUtils.toBooleanObject("fXLse"));
        assertNull(ConvertUtils.toBooleanObject("fXLsE"));
        assertNull(ConvertUtils.toBooleanObject("fXLSe"));
        assertNull(ConvertUtils.toBooleanObject("fXLSE"));
        assertNull(ConvertUtils.toBooleanObject("FXlse"));
        assertNull(ConvertUtils.toBooleanObject("FXlsE"));
        assertNull(ConvertUtils.toBooleanObject("FXlSe"));
        assertNull(ConvertUtils.toBooleanObject("FXlSE"));
        assertNull(ConvertUtils.toBooleanObject("FXLse"));
        assertNull(ConvertUtils.toBooleanObject("FXLsE"));
        assertNull(ConvertUtils.toBooleanObject("FXLSe"));
        assertNull(ConvertUtils.toBooleanObject("FXLSE"));
        assertNull(ConvertUtils.toBooleanObject("faXse"));
        assertNull(ConvertUtils.toBooleanObject("faXsE"));
        assertNull(ConvertUtils.toBooleanObject("faXSe"));
        assertNull(ConvertUtils.toBooleanObject("faXSE"));
        assertNull(ConvertUtils.toBooleanObject("fAXse"));
        assertNull(ConvertUtils.toBooleanObject("fAXsE"));
        assertNull(ConvertUtils.toBooleanObject("fAXSe"));
        assertNull(ConvertUtils.toBooleanObject("fAXSE"));
        assertNull(ConvertUtils.toBooleanObject("FaXse"));
        assertNull(ConvertUtils.toBooleanObject("FaXsE"));
        assertNull(ConvertUtils.toBooleanObject("FaXSe"));
        assertNull(ConvertUtils.toBooleanObject("FaXSE"));
        assertNull(ConvertUtils.toBooleanObject("FAXse"));
        assertNull(ConvertUtils.toBooleanObject("FAXsE"));
        assertNull(ConvertUtils.toBooleanObject("FAXSe"));
        assertNull(ConvertUtils.toBooleanObject("FAXSE"));
        assertNull(ConvertUtils.toBooleanObject("falXe"));
        assertNull(ConvertUtils.toBooleanObject("falXE"));
        assertNull(ConvertUtils.toBooleanObject("faLXe"));
        assertNull(ConvertUtils.toBooleanObject("faLXE"));
        assertNull(ConvertUtils.toBooleanObject("fAlXe"));
        assertNull(ConvertUtils.toBooleanObject("fAlXE"));
        assertNull(ConvertUtils.toBooleanObject("fALXe"));
        assertNull(ConvertUtils.toBooleanObject("fALXE"));
        assertNull(ConvertUtils.toBooleanObject("FalXe"));
        assertNull(ConvertUtils.toBooleanObject("FalXE"));
        assertNull(ConvertUtils.toBooleanObject("FaLXe"));
        assertNull(ConvertUtils.toBooleanObject("FaLXE"));
        assertNull(ConvertUtils.toBooleanObject("FAlXe"));
        assertNull(ConvertUtils.toBooleanObject("FAlXE"));
        assertNull(ConvertUtils.toBooleanObject("FALXe"));
        assertNull(ConvertUtils.toBooleanObject("FALXE"));
        assertNull(ConvertUtils.toBooleanObject("falsX"));
        assertNull(ConvertUtils.toBooleanObject("falSX"));
        assertNull(ConvertUtils.toBooleanObject("faLsX"));
        assertNull(ConvertUtils.toBooleanObject("faLSX"));
        assertNull(ConvertUtils.toBooleanObject("fAlsX"));
        assertNull(ConvertUtils.toBooleanObject("fAlSX"));
        assertNull(ConvertUtils.toBooleanObject("fALsX"));
        assertNull(ConvertUtils.toBooleanObject("fALSX"));
        assertNull(ConvertUtils.toBooleanObject("FalsX"));
        assertNull(ConvertUtils.toBooleanObject("FalSX"));
        assertNull(ConvertUtils.toBooleanObject("FaLsX"));
        assertNull(ConvertUtils.toBooleanObject("FaLSX"));
        assertNull(ConvertUtils.toBooleanObject("FAlsX"));
        assertNull(ConvertUtils.toBooleanObject("FAlSX"));
        assertNull(ConvertUtils.toBooleanObject("FALsX"));
        assertNull(ConvertUtils.toBooleanObject("FALSX"));
        
        assertNull(ConvertUtils.toBooleanObject(null));
    }
}