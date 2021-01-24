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

import java.nio.charset.Charset;

import com.alibaba.nacos.api.common.Constants;

/**
 * ByteUtils.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ByteUtils {
    
    public static final byte[] EMPTY = new byte[0];
    
    /**
     * String to byte array.
     *
     * @param input input string
     * @return byte array of string
     */
    public static byte[] toBytes(String input) {
        if (input == null) {
            return EMPTY;
        }
        return input.getBytes(Charset.forName(Constants.ENCODE));
    }
    
    /**
     * Object to byte array.
     *
     * @param obj input obj
     * @return byte array of object
     */
    public static byte[] toBytes(Object obj) {
        if (obj == null) {
            return EMPTY;
        }
        return toBytes(String.valueOf(obj));
    }
    
    /**
     * Byte array to string.
     *
     * @param bytes byte array
     * @return string
     */
    public static String toString(byte[] bytes) {
        if (bytes == null) {
            return StringUtils.EMPTY;
        }
        return new String(bytes, Charset.forName(Constants.ENCODE));
    }
    
    public static boolean isEmpty(byte[] data) {
        return data == null || data.length == 0;
    }
    
    public static boolean isNotEmpty(byte[] data) {
        return !isEmpty(data);
    }
    
}
