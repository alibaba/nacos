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

package com.alibaba.nacos.common.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base64Test {
    
    @Test
    void test() {
        String origin = "nacos";
        String encoded = "bmFjb3M=";
        
        byte[] encodeBase64 = Base64.encodeBase64(origin.getBytes(StandardCharsets.UTF_8));
        assertEquals(encoded, new String(encodeBase64));
        byte[] decodeBase64 = Base64.decodeBase64(encoded.getBytes(StandardCharsets.UTF_8));
        assertEquals(origin, new String(decodeBase64));
    }
    
    @Test
    void testEncodeNullOrEmpty() {
        byte[] b1 = Base64.encodeBase64(null);
        assertNull(b1);
        byte[] b2 = Base64.encodeBase64(new byte[] {});
        assertEquals(0, b2.length);
    }
    
    @Test
    void testDecodeNullOrEmpty() {
        byte[] b1 = Base64.decodeBase64(null);
        assertNull(b1);
        byte[] b2 = Base64.decodeBase64(new byte[] {});
        assertEquals(0, b2.length);
    }
    
    @Test
    void testChunk() {
        String a = "very large characters to test chunk encoding and see if the result is expected or not";
        byte[] b1 = Base64.encodeBase64(a.getBytes(StandardCharsets.UTF_8), false, false, Integer.MAX_VALUE);
        byte[] b2 = Base64.encodeBase64(a.getBytes(StandardCharsets.UTF_8), true, false, Integer.MAX_VALUE);
        String s1 = new String(b1);
        String s2 = new String(b2);
        assertEquals(s1, "dmVyeSBsYXJnZSBjaGFyYWN0ZXJzIHRvIHRlc3QgY2h1bmsgZW5jb2RpbmcgYW5kIHNlZSBpZiB0"
                + "aGUgcmVzdWx0IGlzIGV4cGVjdGVkIG9yIG5vdA==");
        assertEquals(s2, "dmVyeSBsYXJnZSBjaGFyYWN0ZXJzIHRvIHRlc3QgY2h1bmsgZW5jb2RpbmcgYW5kIHNlZSBpZiB0" + "\r\n"
                + "aGUgcmVzdWx0IGlzIGV4cGVjdGVkIG9yIG5vdA==" + "\r\n");
        
        byte[] c1 = Base64.decodeBase64(b1);
        byte[] c2 = Base64.decodeBase64(b2);
        String s3 = new String(c1);
        String s4 = new String(c2);
        assertEquals(a, s3);
        assertEquals(a, s4);
    }
    
    @Test
    void testUrlSafe() {
        String a = "aa~aa?";
        byte[] b1 = Base64.encodeBase64(a.getBytes(StandardCharsets.UTF_8), false, false, Integer.MAX_VALUE);
        byte[] b2 = Base64.encodeBase64(a.getBytes(StandardCharsets.UTF_8), false, true, Integer.MAX_VALUE);
        String s1 = new String(b1);
        String s2 = new String(b2);
        assertEquals("YWF+YWE/", s1);
        assertEquals("YWF-YWE_", s2);
        
        byte[] c1 = Base64.decodeBase64(b1);
        byte[] c2 = Base64.decodeBase64(b2);
        String s3 = new String(c1);
        String s4 = new String(c2);
        assertEquals("aa~aa?", s3);
        assertEquals("aa~aa?", s4);
    }
    
    @Test
    void testEncodeOverMaxLength() {
        assertThrows(IllegalArgumentException.class, () -> {
            String a = "very large characters to test chunk encoding and see if the result is expected or not";
            Base64.encodeBase64(a.getBytes(StandardCharsets.UTF_8), false, false, 10);
        });
    }
}
