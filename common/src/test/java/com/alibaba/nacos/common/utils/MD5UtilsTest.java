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

import com.alibaba.nacos.api.common.Constants;

import java.security.NoSuchAlgorithmException;

import org.junit.Assert;
import org.junit.Test;

public class MD5UtilsTest {
    
    @Test
    public void testMd5Hex() throws NoSuchAlgorithmException {
        Assert.assertEquals("d41d8cd98f00b204e9800998ecf8427e", MD5Utils.md5Hex("", Constants.ENCODE));
        Assert.assertEquals("acbd18db4cc2f85cedef654fccc4a4d8", MD5Utils.md5Hex("foo", Constants.ENCODE));
        Assert.assertEquals("02f463eb799797e2a978fb1a2ae2991e", MD5Utils.md5Hex(
                "38c5ee9532f037a20b93d0f804cf111fca4003e451d09a692d9dea8032308d9c64eda9047fcd5e850284a49b1a0cfb2ecd45",
                Constants.ENCODE));
        
        Assert.assertEquals("d41d8cd98f00b204e9800998ecf8427e", MD5Utils.md5Hex(new byte[0]));
        Assert.assertEquals("5289df737df57326fcdd22597afb1fac", MD5Utils.md5Hex(new byte[] {1, 2, 3}));
    }
    
    @Test
    public void testEncodeHexString() {
        Assert.assertEquals("", MD5Utils.encodeHexString(new byte[0]));
        Assert.assertEquals("010203", MD5Utils.encodeHexString(new byte[] {1, 2, 3}));
    }
}
