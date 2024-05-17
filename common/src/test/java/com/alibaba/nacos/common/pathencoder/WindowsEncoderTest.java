/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.pathencoder;

import com.alibaba.nacos.common.pathencoder.impl.WindowsEncoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsEncoderTest {
    
    WindowsEncoder windowsEncoder = new WindowsEncoder();
    
    /**
     * test encode.
     */
    @Test
    void testEncode() {
        String charset = Charset.defaultCharset().name();
        String case1 = "aaaadsaknkf";
        assertEquals(windowsEncoder.encode(case1, charset), case1);
        // matches one
        String case2 = "aaaa\\dsaknkf";
        assertEquals("aaaa%A1%dsaknkf", windowsEncoder.encode(case2, charset));
        String case3 = "aaaa/dsaknkf";
        assertEquals("aaaa%A2%dsaknkf", windowsEncoder.encode(case3, charset));
        String case4 = "aaaa:dsaknkf";
        assertEquals("aaaa%A3%dsaknkf", windowsEncoder.encode(case4, charset));
        String case5 = "aaaa*dsaknkf";
        assertEquals("aaaa%A4%dsaknkf", windowsEncoder.encode(case5, charset));
        String case6 = "aaaa?dsaknkf";
        assertEquals("aaaa%A5%dsaknkf", windowsEncoder.encode(case6, charset));
        String case7 = "aaaa\"dsaknkf";
        assertEquals("aaaa%A6%dsaknkf", windowsEncoder.encode(case7, charset));
        String case8 = "aaaa<dsaknkf";
        assertEquals("aaaa%A7%dsaknkf", windowsEncoder.encode(case8, charset));
        String case9 = "aaaa>dsaknkf";
        assertEquals("aaaa%A8%dsaknkf", windowsEncoder.encode(case9, charset));
        String case10 = "aaaa|dsaknkf";
        assertEquals("aaaa%A9%dsaknkf", windowsEncoder.encode(case10, charset));
        
        // matches more
        String case11 = "aaaa<dsa<>>knkf";
        assertEquals("aaaa%A7%dsa%A7%%A8%%A8%knkf", windowsEncoder.encode(case11, charset));
        String case12 = "aaaa\"dsa\"\\\\knkf";
        assertEquals("aaaa%A6%dsa%A6%%A1%%A1%knkf", windowsEncoder.encode(case12, charset));
    }
    
    /**
     * test decode.
     */
    @Test
    void testDecode() {
        String charset = Charset.defaultCharset().name();
        String case1 = "aaaadsaknkf";
        assertEquals(windowsEncoder.decode(case1, charset), case1);
        // matches one
        String case2 = "aaaa%A1%dsaknkf";
        assertEquals("aaaa\\dsaknkf", windowsEncoder.decode(case2, charset));
        String case3 = "aaaa%A2%dsaknkf";
        assertEquals("aaaa/dsaknkf", windowsEncoder.decode(case3, charset));
        String case4 = "aaaa%A3%dsaknkf";
        assertEquals("aaaa:dsaknkf", windowsEncoder.decode(case4, charset));
        String case5 = "aaaa%A4%dsaknkf";
        assertEquals("aaaa*dsaknkf", windowsEncoder.decode(case5, charset));
        String case6 = "aaaa%A5%dsaknkf";
        assertEquals("aaaa?dsaknkf", windowsEncoder.decode(case6, charset));
        String case7 = "aaaa%A6%dsaknkf";
        assertEquals("aaaa\"dsaknkf", windowsEncoder.decode(case7, charset));
        String case8 = "aaaa%A7%dsaknkf";
        assertEquals("aaaa<dsaknkf", windowsEncoder.decode(case8, charset));
        String case9 = "aaaa%A8%dsaknkf";
        assertEquals("aaaa>dsaknkf", windowsEncoder.decode(case9, charset));
        String case10 = "aaaa%A9%dsaknkf";
        assertEquals("aaaa|dsaknkf", windowsEncoder.decode(case10, charset));
        
        // matches more
        String case11 = "aaaa%A7%dsa%A7%%A8%%A8%knkf";
        assertEquals("aaaa<dsa<>>knkf", windowsEncoder.decode(case11, charset));
        String case12 = "aaaa%A6%dsa%A6%%A1%%A1%knkf";
        assertEquals("aaaa\"dsa\"\\\\knkf", windowsEncoder.decode(case12, charset));
    }
    
    /**
     * test needEncode.
     */
    @Test
    void testNeedEncode() {
        // / : ? " < > | \
        assertFalse(windowsEncoder.needEncode(null));
        String case1 = "aaaadsaknkf";
        assertFalse(windowsEncoder.needEncode(case1));
        String case2 = "?asda";
        assertTrue(windowsEncoder.needEncode(case2));
        String case3 = "/asdasd";
        assertTrue(windowsEncoder.needEncode(case3));
        String case4 = "as\\dasda";
        assertTrue(windowsEncoder.needEncode(case4));
        String case5 = "asd::as";
        assertTrue(windowsEncoder.needEncode(case5));
        String case6 = "sda\"sda";
        assertTrue(windowsEncoder.needEncode(case6));
        String case7 = "asdas<da";
        assertTrue(windowsEncoder.needEncode(case7));
        String case8 = "sdasas>a";
        assertTrue(windowsEncoder.needEncode(case8));
        String case9 = "das1|2e";
        assertTrue(windowsEncoder.needEncode(case9));
    }
}
