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

import java.util.ArrayList;

/**
 * Unit test of ConvertUtils.
 *
 * @author <a href="mailto:jifeng.sun@outlook.com">sunjifeng</a>
 */
public class ConvertUtilsTest {
    
    @Test
    public void testToInt() {
        // ConvertUtils.toInt(String)
        Assert.assertEquals(0, ConvertUtils.toInt("0"));
        Assert.assertEquals(-1, ConvertUtils.toInt("-1"));
        Assert.assertEquals(10, ConvertUtils.toInt("10"));
        Assert.assertEquals(Integer.MAX_VALUE, ConvertUtils.toInt(String.valueOf(Integer.MAX_VALUE)));
        Assert.assertEquals(Integer.MIN_VALUE, ConvertUtils.toInt(String.valueOf(Integer.MIN_VALUE)));
        Assert.assertEquals(0, ConvertUtils.toInt("notIntValue"));
        
        // ConvertUtils.toInt(String, Integer)
        Assert.assertEquals(0, ConvertUtils.toInt("0", 100));
        Assert.assertEquals(100, ConvertUtils.toInt(null, 100));
        Assert.assertEquals(100, ConvertUtils.toInt("null", 100));
        Assert.assertEquals(100, ConvertUtils.toInt("notIntValue", 100));
    }
    
    @Test
    public void testToLong() {
        // ConvertUtils.toLong(Object)
        Assert.assertEquals(0L, ConvertUtils.toLong(new ArrayList<>()));
        Assert.assertEquals(10L, ConvertUtils.toLong((Object) 10L));
        
        // ConvertUtils.toLong(String)
        Assert.assertEquals(0L, ConvertUtils.toLong("0"));
        Assert.assertEquals(-1L, ConvertUtils.toLong("-1"));
        Assert.assertEquals(10L, ConvertUtils.toLong("10"));
        Assert.assertEquals(Long.MAX_VALUE, ConvertUtils.toLong(String.valueOf(Long.MAX_VALUE)));
        Assert.assertEquals(Long.MIN_VALUE, ConvertUtils.toLong(String.valueOf(Long.MIN_VALUE)));
        Assert.assertEquals(0L, ConvertUtils.toLong("notIntValue"));
        
        // ConvertUtils.toLong(String, Integer)
        Assert.assertEquals(0L, ConvertUtils.toLong("0", 100L));
        Assert.assertEquals(100L, ConvertUtils.toLong(null, 100L));
        Assert.assertEquals(100L, ConvertUtils.toLong("null", 100L));
        Assert.assertEquals(100L, ConvertUtils.toLong("notIntValue", 100L));
    }
    
    @Test
    public void testToBoolean() {
        // ConvertUtils.toBoolean(String)
        Assert.assertTrue(ConvertUtils.toBoolean("true"));
        Assert.assertTrue(ConvertUtils.toBoolean("True"));
        Assert.assertTrue(ConvertUtils.toBoolean("TRUE"));
        Assert.assertFalse(ConvertUtils.toBoolean("false"));
        Assert.assertFalse(ConvertUtils.toBoolean("False"));
        Assert.assertFalse(ConvertUtils.toBoolean("FALSE"));
        Assert.assertFalse(ConvertUtils.toBoolean(null));
        Assert.assertFalse(ConvertUtils.toBoolean("notBoolean"));
        
        // ConvertUtils.toBoolean(String, boolean)
        Assert.assertFalse(ConvertUtils.toBoolean("", false));
        Assert.assertFalse(ConvertUtils.toBoolean(null, false));
        Assert.assertFalse(ConvertUtils.toBoolean("notBoolean", false));
        Assert.assertTrue(ConvertUtils.toBoolean("true", false));
    }
    
    @Test
    public void testToBooleanObject() {
        Assert.assertTrue(ConvertUtils.toBooleanObject("T"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("t"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("Y"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("y"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("f"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("F"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("n"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("N"));
        Assert.assertNull(ConvertUtils.toBooleanObject("a"));
        
        Assert.assertTrue(ConvertUtils.toBooleanObject("on"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("oN"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("On"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("ON"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("No"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("NO"));
        Assert.assertNull(ConvertUtils.toBooleanObject("an"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aN"));
        Assert.assertNull(ConvertUtils.toBooleanObject("oa"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Oa"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Na"));
        Assert.assertNull(ConvertUtils.toBooleanObject("na"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aO"));
        Assert.assertNull(ConvertUtils.toBooleanObject("ao"));
        
        Assert.assertFalse(ConvertUtils.toBooleanObject("off"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("ofF"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("oFf"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("oFF"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("Off"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("OfF"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("OFf"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("OFF"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("yes"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("yeS"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("yEs"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("yES"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("Yes"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("YeS"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("YEs"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("YES"));
        Assert.assertNull(ConvertUtils.toBooleanObject("ono"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aes"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aeS"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aEs"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aES"));
        Assert.assertNull(ConvertUtils.toBooleanObject("yas"));
        Assert.assertNull(ConvertUtils.toBooleanObject("yaS"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Yas"));
        Assert.assertNull(ConvertUtils.toBooleanObject("YaS"));
        Assert.assertNull(ConvertUtils.toBooleanObject("yea"));
        Assert.assertNull(ConvertUtils.toBooleanObject("yEa"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Yea"));
        Assert.assertNull(ConvertUtils.toBooleanObject("YEa"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aff"));
        Assert.assertNull(ConvertUtils.toBooleanObject("afF"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aFf"));
        Assert.assertNull(ConvertUtils.toBooleanObject("aFF"));
        Assert.assertNull(ConvertUtils.toBooleanObject("oaf"));
        Assert.assertNull(ConvertUtils.toBooleanObject("oaF"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Oaf"));
        Assert.assertNull(ConvertUtils.toBooleanObject("OaF"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Ofa"));
        Assert.assertNull(ConvertUtils.toBooleanObject("ofa"));
        Assert.assertNull(ConvertUtils.toBooleanObject("OFa"));
        Assert.assertNull(ConvertUtils.toBooleanObject("oFa"));
        
        Assert.assertTrue(ConvertUtils.toBooleanObject("true"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("truE"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("trUe"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("trUE"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("tRue"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("tRuE"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("tRUe"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("tRUE"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("True"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("TruE"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("TrUe"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("TrUE"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("TRue"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("TRuE"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("TRUe"));
        Assert.assertTrue(ConvertUtils.toBooleanObject("TRUE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Xrue"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XruE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XrUe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XrUE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XRue"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XRuE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XRUe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XRUE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tXue"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tXuE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tXUe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tXUE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TXue"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TXuE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TXUe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TXUE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("trXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("trXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tRXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tRXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TrXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TrXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TRXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TRXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("truX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("trUX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tRuX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("tRUX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TruX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TrUX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TRuX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("TRUX"));
        
        Assert.assertFalse(ConvertUtils.toBooleanObject("false"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("falsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("falSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("falSE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("faLse"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("faLsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("faLSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("faLSE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fAlse"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fAlsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fAlSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fAlSE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fALse"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fALsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fALSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("fALSE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("False"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FalsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FalSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FalSE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FaLse"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FaLsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FaLSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FaLSE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FAlse"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FAlsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FAlSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FAlSE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FALse"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FALsE"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FALSe"));
        Assert.assertFalse(ConvertUtils.toBooleanObject("FALSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("Xalse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XalsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XalSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XalSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XaLse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XaLsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XaLSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XaLSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XAlse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XAlsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XAlSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XAlSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XALse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XALsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XALSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("XALSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXlse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXlsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXlSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXlSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXLse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXLsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXLSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fXLSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXlse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXlsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXlSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXlSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXLse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXLsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXLSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FXLSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faXse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faXsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faXSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faXSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAXse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAXsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAXSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAXSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaXse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaXsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaXSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaXSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAXse"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAXsE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAXSe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAXSE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("falXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("falXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faLXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faLXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAlXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAlXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fALXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fALXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FalXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FalXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaLXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaLXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAlXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAlXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FALXe"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FALXE"));
        Assert.assertNull(ConvertUtils.toBooleanObject("falsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("falSX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faLsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("faLSX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAlsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fAlSX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fALsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("fALSX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FalsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FalSX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaLsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FaLSX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAlsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FAlSX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FALsX"));
        Assert.assertNull(ConvertUtils.toBooleanObject("FALSX"));
        
        Assert.assertNull(ConvertUtils.toBooleanObject(null));
    }
}