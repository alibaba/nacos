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

package com.alibaba.nacos.common.remote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsConfigTest {
    
    @Test
    void testTlsConfig() {
        TlsConfig tlsConfig = new TlsConfig();
        assertFalse(tlsConfig.getEnableTls());
        assertFalse(tlsConfig.getMutualAuthEnable());
        assertNull(tlsConfig.getProtocols());
        assertNull(tlsConfig.getCiphers());
        assertFalse(tlsConfig.getTrustAll());
        assertNull(tlsConfig.getTrustCollectionCertFile());
        assertNull(tlsConfig.getCertPrivateKeyPassword());
        assertNull(tlsConfig.getCertPrivateKey());
        assertNull(tlsConfig.getCertChainFile());
        assertEquals("", tlsConfig.getSslProvider());
        
        // Set values
        tlsConfig.setEnableTls(true);
        tlsConfig.setMutualAuthEnable(true);
        tlsConfig.setProtocols("TLSv1.1,TLSv1.2,TLSv1.3");
        tlsConfig.setCiphers("cipher1,cipher2");
        tlsConfig.setTrustAll(true);
        tlsConfig.setTrustCollectionCertFile("certFile");
        tlsConfig.setCertPrivateKeyPassword("password");
        tlsConfig.setCertPrivateKey("privateKey");
        tlsConfig.setCertChainFile("chainFile");
        tlsConfig.setSslProvider("OPENSSL");
        
        // Test values
        assertTrue(tlsConfig.getEnableTls());
        assertTrue(tlsConfig.getMutualAuthEnable());
        assertEquals("TLSv1.1,TLSv1.2,TLSv1.3", tlsConfig.getProtocols());
        assertEquals("cipher1,cipher2", tlsConfig.getCiphers());
        assertTrue(tlsConfig.getTrustAll());
        assertEquals("certFile", tlsConfig.getTrustCollectionCertFile());
        assertEquals("password", tlsConfig.getCertPrivateKeyPassword());
        assertEquals("privateKey", tlsConfig.getCertPrivateKey());
        assertEquals("chainFile", tlsConfig.getCertChainFile());
        assertEquals("OPENSSL", tlsConfig.getSslProvider());
    }
}