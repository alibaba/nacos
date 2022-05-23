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

import org.apache.commons.io.Charsets;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Unit test of IoUtils.
 *
 * @author karsonto
 */
public class IoUtilsTest {
    
    @Test()
    public void testCloseQuietly() throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream("111".getBytes(Charsets.toCharset("UTF-8")))));
        Assert.assertEquals("111", br.readLine());
        IoUtils.closeQuietly(br);
        try {
            br.readLine();
        } catch (IOException e) {
            Assert.assertNotNull(e);
            return;
        }
        Assert.fail();
    }
    
    @Test()
    public void testCloseQuietly2() throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream("123".getBytes(Charsets.toCharset("UTF-8")))));
        Assert.assertEquals("123", br.readLine());
        BufferedReader br2 = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream("456".getBytes(Charsets.toCharset("UTF-8")))));
        Assert.assertEquals("456", br2.readLine());
        IoUtils.closeQuietly(br, br2);
        try {
            br.readLine();
        } catch (IOException e) {
            Assert.assertNotNull(e);
        }
        try {
            br2.readLine();
        } catch (IOException e) {
            Assert.assertNotNull(e);
            return;
        }
        Assert.fail();
    }
    
}
