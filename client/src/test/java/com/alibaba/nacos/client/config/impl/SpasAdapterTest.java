/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

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
        
        // TODO BUG fix
        //  Map<String, String> param2 = new HashMap<>();
        //  param2.put("tenant", "");
        //  param2.put("group", "aa");
        //  final Map<String, String> map2 = SpasAdapter.getSignHeaders(param2, "123");
        //  Assert.assertEquals(2, map2.size());
        //  Assert.assertEquals(SpasAdapter.signWithHmacSha1Encrypt("aa" + "+" + map2.get("Timestamp"), "123"),
        //  map2.get("Spas-Signature"));
        
        //  Map<String, String> param3 = new HashMap<>();
        //  param3.put("tenant", "bb");
        //  param3.put("group", "");
        //  final Map<String, String> map3 = SpasAdapter.getSignHeaders(param3, "123");
        //  Assert.assertEquals(2, map3.size());
        //  Assert.assertEquals(SpasAdapter.signWithHmacSha1Encrypt(map3.get("Timestamp"), "123"),
        //   map3.get("Spas-Signature"));
    }
    
}
