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

package com.alibaba.nacos.plugin.auth.impl.utils;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Base64Decoder test.
 *
 * @author xYohn
 * @date 2023/8/8
 */
class Base64DecodeTest {
    
    @Test
    void testStandardDecode() {
        String origin = "aGVsbG8sbmFjb3MhdGVzdEJhc2U2NGVuY29kZQ==";
        String expectDecodeOrigin = "hello,nacos!testBase64encode";
        byte[] decodeOrigin = Base64Decode.decode(origin);
        assertArrayEquals(decodeOrigin, expectDecodeOrigin.getBytes());
    }
    
    @Test
    void testNotStandardDecode() {
        String notStandardOrigin =
            "SecretKey012345678901234567890123456789012345678901234567890123456789";
        byte[] decodeNotStandardOrigin = Base64Decode.decode(notStandardOrigin);
        String truncationOrigin =
            "SecretKey01234567890123456789012345678901234567890123456789012345678";
        byte[] decodeTruncationOrigin = Base64Decode.decode(truncationOrigin);
        assertArrayEquals(decodeNotStandardOrigin, decodeTruncationOrigin);
    }
    
    @Test
    void testDecodeEmptyAndTrimmedInput() {
        assertEquals(0, Base64Decode.decode(null).length);
        assertEquals(0, Base64Decode.decode("").length);
        assertArrayEquals("hello".getBytes(), Base64Decode.decode("$aGVsbG8=$"));
    }
    
    @Test
    void testDecodeMimeInputWithLineSeparators() {
        byte[] expected = new byte[120];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
        }
        String encoded =
            Base64.getMimeEncoder(76, new byte[] {'\r', '\n'}).encodeToString(expected);
        
        assertArrayEquals(expected, Base64Decode.decode(encoded));
    }
    
    @Test
    void testDecodeRejectsIllegalCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Base64Decode.decode("AA$A"));
        assertThrows(IllegalArgumentException.class,
            () -> Base64Decode.decode("AA" + (char) 256 + "A"));
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new Base64Decode());
    }
}
