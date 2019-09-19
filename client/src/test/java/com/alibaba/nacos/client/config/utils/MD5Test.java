/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.client.config.utils;

import org.junit.Assert;
import org.junit.Test;

public class MD5Test {

    @Test
    public void testGetMD5String() {
        Assert.assertEquals("d41d8cd98f00b204e9800998ecf8427e",
            MD5.getInstance().getMD5String(""));
        Assert.assertEquals("acbd18db4cc2f85cedef654fccc4a4d8",
            MD5.getInstance().getMD5String("foo"));

        Assert.assertEquals("d41d8cd98f00b204e9800998ecf8427e",
            MD5.getInstance().getMD5String(new byte[0]));
        Assert.assertEquals("5289df737df57326fcdd22597afb1fac",
            MD5.getInstance().getMD5String(new byte[]{1, 2, 3}));
    }

    @Test
    public void testGetMD5Bytes() {
        byte[] bytes1 = new byte[]{-44, 29, -116, -39, -113, 0, -78,
            4, -23, -128, 9, -104, -20, -8, 66, 126};
        byte[] bytes2 = new byte[]{82, -119, -33, 115, 125, -11, 115,
            38, -4, -35, 34, 89, 122, -5, 31, -84};

        Assert.assertArrayEquals(bytes1,
            MD5.getInstance().getMD5Bytes(new byte[0]));
        Assert.assertArrayEquals(bytes2,
            MD5.getInstance().getMD5Bytes(new byte[]{1, 2, 3}));
    }

    @Test
    public void testHash() {
        byte[] bytes1 = new byte[]{-44, 29, -116, -39, -113, 0, -78,
            4, -23, -128, 9, -104, -20, -8, 66, 126};
        byte[] bytes2 = new byte[]{-84, -67, 24, -37, 76, -62, -8, 92,
            -19, -17, 101, 79, -52, -60, -92, -40};
        byte[] bytes3 = new byte[]{82, -119, -33, 115, 125, -11, 115,
            38, -4, -35, 34, 89, 122, -5, 31, -84};

        Assert.assertArrayEquals(bytes1, MD5.getInstance().hash(""));
        Assert.assertArrayEquals(bytes2, MD5.getInstance().hash("foo"));
        Assert.assertArrayEquals(bytes1, MD5.getInstance().hash(new byte[0]));
        Assert.assertArrayEquals(bytes3,
            MD5.getInstance().hash(new byte[]{1, 2, 3}));
    }

    @Test
    public void testBytes2string() {
        Assert.assertEquals("", MD5.getInstance().bytes2string(new byte[0]));
        Assert.assertEquals("010203",
            MD5.getInstance().bytes2string(new byte[]{1, 2, 3}));
    }
}
