/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosSignatureAlgorithmTest {
    
    private final SecretKeySpec key = new SecretKeySpec(
        "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    
    @Test
    void testSignVerifyAndReadExpireTime() throws AccessException {
        NacosJwtPayload payload = new NacosJwtPayload();
        payload.setSub("nacos");
        payload.setExp(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + 60L);
        
        String jwt = NacosSignatureAlgorithm.HS256.sign(payload, key);
        NacosUser user = NacosSignatureAlgorithm.verify(jwt, key);
        
        assertEquals("nacos", user.getUserName());
        assertEquals(jwt, user.getToken());
        assertEquals(payload.getExp(), NacosSignatureAlgorithm.getExpiredTimeInSeconds(jwt, key));
        assertEquals("HS256", NacosSignatureAlgorithm.HS256.getAlgorithm());
        assertEquals("HmacSHA256", NacosSignatureAlgorithm.HS256.getJcaName());
        assertEquals(jwt.split("\\.")[0], NacosSignatureAlgorithm.HS256.getHeader());
    }
    
    @Test
    void testVerifyRejectsInvalidTokens() {
        NacosJwtPayload expiredPayload = new NacosJwtPayload();
        expiredPayload.setSub("nacos");
        expiredPayload.setExp(1L);
        String expiredJwt = NacosSignatureAlgorithm.HS256.sign(expiredPayload, key);
        String invalidSignature = expiredJwt.substring(0, expiredJwt.length() - 1) + "x";
        
        assertThrows(AccessException.class, () -> NacosSignatureAlgorithm.verify("", key));
        assertThrows(AccessException.class, () -> NacosSignatureAlgorithm.verify("a.b", key));
        assertThrows(AccessException.class,
            () -> NacosSignatureAlgorithm.verify("unknown.payload.signature", key));
        assertThrows(AccessException.class,
            () -> NacosSignatureAlgorithm.verify(invalidSignature, key));
        assertThrows(AccessException.class, () -> NacosSignatureAlgorithm.verify(expiredJwt, key));
    }
    
    @Test
    void testGetExpiredTimeRejectsInvalidTokens() {
        assertThrows(AccessException.class,
            () -> NacosSignatureAlgorithm.getExpiredTimeInSeconds("", key));
        assertThrows(AccessException.class,
            () -> NacosSignatureAlgorithm.getExpiredTimeInSeconds("a.b", key));
        assertThrows(AccessException.class,
            () -> NacosSignatureAlgorithm.getExpiredTimeInSeconds("unknown.payload.signature",
                key));
    }
    
    @Test
    void testSignRejectsInvalidKey() {
        NacosJwtPayload payload = new NacosJwtPayload();
        payload.setSub("nacos");
        
        assertThrows(IllegalArgumentException.class,
            () -> NacosSignatureAlgorithm.HS256.sign(payload, null));
    }
    
    @Test
    void testAlgorithmsExposeDistinctHeaders() {
        assertTrue(NacosSignatureAlgorithm.HS384.getHeader().contains("UzM4"));
        assertTrue(NacosSignatureAlgorithm.HS512.getHeader().contains("UxMi"));
    }
}
