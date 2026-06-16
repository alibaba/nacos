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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Input stream backed by a {@link ByteBuffer}.
 *
 * @author nacos
 */
public class ByteBufferInputStream extends InputStream {
    
    private final ByteBuffer byteBuffer;
    
    public ByteBufferInputStream(ByteBuffer byteBuffer) {
        this.byteBuffer = Objects.requireNonNull(byteBuffer, "byteBuffer").slice();
    }
    
    @Override
    public int read() {
        if (!byteBuffer.hasRemaining()) {
            return -1;
        }
        return byteBuffer.get() & 0xff;
    }
    
    @Override
    public int read(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes");
        if (offset < 0 || length < 0 || length > bytes.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) {
            return 0;
        }
        if (!byteBuffer.hasRemaining()) {
            return -1;
        }
        int lengthToRead = Math.min(length, byteBuffer.remaining());
        byteBuffer.get(bytes, offset, lengthToRead);
        return lengthToRead;
    }
    
    @Override
    public int available() {
        return byteBuffer.remaining();
    }
}
