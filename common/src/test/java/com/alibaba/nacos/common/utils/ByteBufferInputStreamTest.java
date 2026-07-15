/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteBufferInputStreamTest {
    
    @Test
    void testReadSingleByteAndBytes() {
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3});
        byteBuffer.position(1);
        byteBuffer.limit(3);
        ByteBufferInputStream inputStream = new ByteBufferInputStream(byteBuffer);
        
        assertEquals(2, inputStream.available());
        assertEquals(1, inputStream.read());
        assertEquals(1, inputStream.available());
        
        byte[] bytes = new byte[] {9, 9, 9};
        assertEquals(1, inputStream.read(bytes, 1, 2));
        assertArrayEquals(new byte[] {9, 2, 9}, bytes);
        assertEquals(0, inputStream.available());
        assertEquals(-1, inputStream.read());
        assertEquals(-1, inputStream.read(bytes, 0, bytes.length));
        assertEquals(1, byteBuffer.position());
    }
    
    @Test
    void testReadZeroLength() {
        ByteBufferInputStream inputStream = new ByteBufferInputStream(ByteBuffer.allocate(0));
        
        assertEquals(0, inputStream.read(new byte[0], 0, 0));
        assertEquals(-1, inputStream.read());
    }
    
    @Test
    void testConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new ByteBufferInputStream(null));
    }
    
    @Test
    void testReadRejectsInvalidArguments() {
        ByteBufferInputStream inputStream =
            new ByteBufferInputStream(ByteBuffer.wrap(new byte[] {1}));
        
        assertThrows(NullPointerException.class, () -> inputStream.read(null, 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> inputStream.read(new byte[1], -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> inputStream.read(new byte[1], 0, 2));
    }
}
