/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.jwt;

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * NacosJwtParserTest.
 *
 * @author Weizhan▪Yun
 * @date 2023/2/1 16:32
 */
@RunWith(MockitoJUnitRunner.class)
public class NacosJwtParserTest {
    
    @Test
    public void testParseWithOriginKey() {
        new NacosJwtParser("SecretKey012345678901234567890123456789012345678901234567890123456789");
    }
    
    @Test
    public void testParseWith16Key() {
        Exception e = null;
        try {
            new NacosJwtParser("SecretKey0123456");
        } catch (Exception exception) {
            e = exception;
        }
        
        assertNotNull(e);
        assertEquals(IllegalArgumentException.class, e.getClass());
        
    }
    
    @Test
    public void testParseWith32Key() {
        NacosJwtParser parser = new NacosJwtParser(encode("SecretKey01234567890123456789012"));
        String token = parser.jwtBuilder().setUserName("nacos").setExpiredTime(100L).compact();
        
        assertTrue(token.startsWith(NacosSignatureAlgorithm.HS256.getHeader()));
    }
    
    @Test
    public void testParseWith48Key() {
        NacosJwtParser parser = new NacosJwtParser(encode("SecretKey012345678901234567890120124568aa9012345"));
        String token = parser.jwtBuilder().setUserName("nacos").setExpiredTime(100L).compact();
        
        assertTrue(token.startsWith(NacosSignatureAlgorithm.HS384.getHeader()));
    }
    
    @Test
    public void testParseWith64Key() {
        NacosJwtParser parser = new NacosJwtParser(
                encode("SecretKey012345678901234567SecretKey0123456789012345678901289012"));
        String token = parser.jwtBuilder().setUserName("nacos").setExpiredTime(100L).compact();
        
        assertTrue(token.startsWith(NacosSignatureAlgorithm.HS512.getHeader()));
    }
    
    @Test
    public void testGetExpireTimeInSeconds() throws AccessException {
        NacosJwtParser parser = new NacosJwtParser(
                encode("SecretKey012345678901234567SecretKey0123456789012345678901289012"));
        String token = parser.jwtBuilder().setUserName("nacos").setExpiredTime(100L).compact();
        long expiredTimeSeconds = parser.getExpireTimeInSeconds(token);
        assertTrue(expiredTimeSeconds * 1000 - System.currentTimeMillis() > 0);
    }
    
    private String encode(String key) {
        return Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }
}