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

package com.alibaba.nacos.api.utils;

import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * testcase.
 * @author TFdream
 */
public class UrlCodecUtilsTest {
    private String url = "https://nacos.io/";
    
    @Test
    public void testEncode() {
        String result = UrlCodecUtils.encode(url);
        //System.out.println(result);
        Assert.assertTrue(result.equals("https%3A%2F%2Fnacos.io%2F"));
    }
    
    @Test
    public void testEncode2() {
        String result = UrlCodecUtils.encode(url, StandardCharsets.UTF_8);
        Assert.assertTrue(result.equals("https%3A%2F%2Fnacos.io%2F"));
    }
    
    @Test
    public void testEncode3() throws UnsupportedEncodingException {
        String result = UrlCodecUtils.encode(url, "UTF-8");
        Assert.assertTrue(result.equals("https%3A%2F%2Fnacos.io%2F"));
    }
    
    //==========
    @Test
    public void testDecode() {
        String result = UrlCodecUtils.decode("https%3A%2F%2Fnacos.io%2F");
        //System.out.println(result);
        Assert.assertTrue(result.equals(url));
    }
    
    @Test
    public void testDecode2() {
        String result = UrlCodecUtils.decode("https%3A%2F%2Fnacos.io%2F", StandardCharsets.UTF_8);
        //System.out.println(result);
        Assert.assertTrue(result.equals(url));
    }
    
    @Test
    public void testDecode3() throws UnsupportedEncodingException {
        String result = UrlCodecUtils.decode("https%3A%2F%2Fnacos.io%2F", "UTF-8");
        //System.out.println(result);
        Assert.assertTrue(result.equals(url));
    }
}
