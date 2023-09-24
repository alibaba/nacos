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

import org.junit.Assert;
import org.junit.Test;

/**
 * Base64Decoder test.
 *
 * @author xYohn
 * @date 2023/8/8
 */
public class Base64DecodeTest {
    
    @Test
    public void testStandardDecode() {
        String origin = "aGVsbG8sbmFjb3MhdGVzdEJhc2U2NGVuY29kZQ==";
        String expectDecodeOrigin = "hello,nacos!testBase64encode";
        byte[] decodeOrigin = Base64Decode.decode(origin);
        Assert.assertArrayEquals(decodeOrigin, expectDecodeOrigin.getBytes());
    }
    
    @Test
    public void testNotStandardDecode() {
        String notStandardOrigin = "SecretKey012345678901234567890123456789012345678901234567890123456789";
        byte[] decodeNotStandardOrigin = Base64Decode.decode(notStandardOrigin);
        String truncationOrigin = "SecretKey01234567890123456789012345678901234567890123456789012345678";
        byte[] decodeTruncationOrigin = Base64Decode.decode(truncationOrigin);
        Assert.assertArrayEquals(decodeNotStandardOrigin, decodeTruncationOrigin);
    }
}
