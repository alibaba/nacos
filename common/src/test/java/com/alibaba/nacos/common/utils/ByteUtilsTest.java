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

import com.alibaba.nacos.common.utils.to.User;
import org.junit.Assert;
import org.junit.Test;

/**
 * ByteUtils Test.
 *
 * @ClassName: ByteUtilsTest
 * @Author: ChenHao26
 * @Date: 2022/8/22 10:58
 */
public class ByteUtilsTest {
    
    @Test
    public void objectToByte() {
        User user = new User(1, "google");
        byte[] bytes = ByteUtils.toBytes(user);
        Assert.assertNotNull(bytes);
    }
    
    @Test
    public void stringToByte() {
        byte[] bytes = ByteUtils.toBytes("google");
        Assert.assertNotNull(bytes);
    }
    
    @Test
    public void toStringTest() {
        byte[] bytes = ByteUtils.toBytes("google");
        String str = ByteUtils.toString(bytes);
        Assert.assertEquals(str, "google");
    }
    
    @Test
    public void testForInputNull() {
        Assert.assertEquals(0, ByteUtils.toBytes(null).length);
        Assert.assertEquals(0, ByteUtils.toBytes((Object) null).length);
        Assert.assertEquals("", ByteUtils.toString(null));
    }
    
    @Test
    public void isEmpty() {
        byte[] bytes = ByteUtils.toBytes("");
        Assert.assertTrue(ByteUtils.isEmpty(bytes));
        byte[] byte2 = new byte[1024];
        Assert.assertFalse(ByteUtils.isEmpty(byte2));
        byte[] byte3 = null;
        Assert.assertTrue(ByteUtils.isEmpty(byte3));
    }
    
    @Test
    public void isNotEmpty() {
        byte[] bytes = ByteUtils.toBytes("google");
        Assert.assertTrue(ByteUtils.isNotEmpty(bytes));
        byte[] bytes2 = ByteUtils.toBytes("");
        Assert.assertFalse(ByteUtils.isNotEmpty(bytes2));
    }
}

