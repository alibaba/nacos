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

package com.alibaba.nacos.core.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ByteUtils {

    private static final byte[] EMPTY = new byte[0];

    public static byte getByteByIndex(byte[] source, int pos) {
        assert pos < source.length;
        return source[pos];
    }

    public static byte[] cut(byte[] source, int start, int end) {
        byte[] target = new byte[end - start + 1];
        System.arraycopy(source, start, target, 0, end - start + 1);
        return target;
    }

    public static byte[] toBytes(String s) {
        if (Objects.isNull(s)) {
            return EMPTY;
        }
        return s.getBytes(Charset.forName(StandardCharsets.UTF_8.name()));
    }

    public static byte[] toBytes(long s) {
        return toBytes(String.valueOf(s));
    }

    public static String toString(byte[] bytes) {
        if (Objects.isNull(bytes)) {
            return StringUtils.EMPTY;
        }
        return new String(bytes, Charset.forName(StandardCharsets.UTF_8.name()));
    }

    public static byte[] copyAndAdd(byte[] source, byte b, int pos, int length) {
        assert (length - pos > source.length);
        byte[] target = new byte[length];
        System.arraycopy(source, 0, target, pos + 1, source.length);
        target[pos] = b;
        return target;
    }

}
