/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.ram.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SpasAdapterTest {
    
    @Test
    public void test() {
        Assert.assertNull(SpasAdapter.getAk());
        Assert.assertNull(SpasAdapter.getSk());
        try {
            SpasAdapter.freeCredentialInstance();
        } catch (Exception e) {
            Assert.fail();
        }
        
    }
    
    @Test
    public void testSign() {
        
        Assert.assertNull(SpasAdapter.getSignHeaders("", "", "123"));
        
        final Map<String, String> map1 = SpasAdapter.getSignHeaders("aa", "bb", "123");
        Assert.assertEquals(2, map1.size());
        Assert.assertEquals(SpasAdapter.signWithHmacSha1Encrypt("bb+aa+" + map1.get("Timestamp"), "123"),
                map1.get("Spas-Signature"));
        
        final Map<String, String> map2 = SpasAdapter.getSignHeaders("aa", "", "123");
        Assert.assertEquals(2, map2.size());
        Assert.assertEquals(SpasAdapter.signWithHmacSha1Encrypt("aa" + "+" + map2.get("Timestamp"), "123"),
                map2.get("Spas-Signature"));
        
        final Map<String, String> map3 = SpasAdapter.getSignHeaders("", "bb", "123");
        Assert.assertEquals(2, map3.size());
        Assert.assertEquals(SpasAdapter.signWithHmacSha1Encrypt(map3.get("Timestamp"), "123"),
                map3.get("Spas-Signature"));
    }
    
    @Test
    public void testSign2() {
        
        Assert.assertNull(SpasAdapter.getSignHeaders((Map) null, "123"));
        
        Map<String, String> param1 = new HashMap<>();
        param1.put("tenant", "bb");
        param1.put("group", "aa");
        final Map<String, String> map1 = SpasAdapter.getSignHeaders(param1, "123");
        Assert.assertEquals(2, map1.size());
        Assert.assertEquals(SpasAdapter.signWithHmacSha1Encrypt("bb+aa+" + map1.get("Timestamp"), "123"),
                map1.get("Spas-Signature"));
    }
    
    @Test
    public void testGetSignHeadersWithoutTenant() {
        Map<String, String> param1 = new HashMap<>();
        param1.put("group", "aa");
        final Map<String, String> map1 = SpasAdapter.getSignHeaders(param1, "123");
        Assert.assertEquals(2, map1.size());
        Assert.assertEquals(SpasAdapter.signWithHmacSha1Encrypt("aa+" + map1.get("Timestamp"), "123"),
                map1.get("Spas-Signature"));
    }
    
    @Test(expected = Exception.class)
    public void testSignWithHmacSha1EncryptWithException() {
        SpasAdapter.signWithHmacSha1Encrypt(null, "123");
    }
}
