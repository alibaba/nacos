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

package com.alibaba.nacos.common.tls;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfTrustManagerTest {
    
    @BeforeEach
    void setUp() throws Exception {
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testTrustManagerSuccess() throws CertificateException {
        URL url = SelfTrustManagerTest.class.getClassLoader().getResource("test-tls-cert.pem");
        String path = url.getPath();
        TrustManager[] actual = SelfTrustManager.trustManager(true, path);
        assertNotNull(actual);
        assertEquals(1, actual.length);
        assertTrue(actual[0] instanceof X509TrustManager);
        assertFalse(actual[0].getClass().getCanonicalName().startsWith("com.alibaba.nacos"));
        X509TrustManager x509TrustManager = (X509TrustManager) actual[0];
        X509Certificate[] certificates = x509TrustManager.getAcceptedIssuers();
        assertNotNull(certificates);
        x509TrustManager.checkClientTrusted(certificates, "a");
        x509TrustManager.checkServerTrusted(certificates, "b");
    }
    
    @Test
    void testTrustManagerNonExist() throws CertificateException {
        TrustManager[] actual = SelfTrustManager.trustManager(true, "non-exist-cert.pem");
        assertNotNull(actual);
        assertEquals(1, actual.length);
        assertTrue(actual[0] instanceof X509TrustManager);
        assertTrue(actual[0].getClass().isAnonymousClass());
        X509TrustManager x509TrustManager = (X509TrustManager) actual[0];
        assertNull(x509TrustManager.getAcceptedIssuers());
        x509TrustManager.checkClientTrusted(null, "a");
        x509TrustManager.checkServerTrusted(null, "b");
    }
}