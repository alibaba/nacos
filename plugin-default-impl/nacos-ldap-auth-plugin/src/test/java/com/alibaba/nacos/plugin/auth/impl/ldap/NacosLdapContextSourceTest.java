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

package com.alibaba.nacos.plugin.auth.impl.ldap;

import com.alibaba.nacos.common.tls.TlsHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import javax.naming.Context;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NacosLdapContextSourceTest {
    
    private static final String DISABLE_ENDPOINT_IDENTIFICATION =
        "com.sun.jndi.ldap.object.disableEndpointIdentification";
    
    @Test
    void testInitPlainLdapEnvironment() {
        TestableNacosLdapContextSource source = new TestableNacosLdapContextSource(
            "ldap://localhost:389", "dc=example,dc=org", "cn=admin,dc=example,dc=org",
            "password", "1234");
        
        Hashtable<String, Object> environment = source.anonymousEnvironment();
        
        assertArrayEquals(new String[] {"ldap://localhost:389"}, source.getUrls());
        assertEquals("dc=example,dc=org", source.getBaseLdapPathAsString());
        assertEquals("cn=admin,dc=example,dc=org", source.getUserDn());
        assertEquals("password", source.getPassword());
        assertFalse(environment.containsKey("java.naming.security.protocol"));
        assertEquals("objectGUID", environment.get("java.naming.ldap.attributes.binary"));
        assertEquals("1234", environment.get("com.sun.jndi.ldap.connect.timeout"));
    }
    
    @Test
    void testInitLdapsEnvironment() {
        String previous = System.getProperty(DISABLE_ENDPOINT_IDENTIFICATION);
        try {
            TestableNacosLdapContextSource source = new TestableNacosLdapContextSource(
                "ldaps://localhost:636", "dc=example,dc=org", "cn=admin,dc=example,dc=org",
                "password", "2000");
            
            Hashtable<String, Object> environment = source.anonymousEnvironment();
            
            assertEquals("true", System.getProperty(DISABLE_ENDPOINT_IDENTIFICATION));
            assertEquals("ssl", environment.get("java.naming.security.protocol"));
            assertEquals(NacosLdapContextSource.LdapSslSocketFactory.class.getName(),
                environment.get("java.naming.ldap.factory.socket"));
            assertEquals("com.sun.jndi.ldap.LdapCtxFactory",
                environment.get(Context.INITIAL_CONTEXT_FACTORY));
        } finally {
            if (null == previous) {
                System.clearProperty(DISABLE_ENDPOINT_IDENTIFICATION);
            } else {
                System.setProperty(DISABLE_ENDPOINT_IDENTIFICATION, previous);
            }
        }
    }
    
    @Test
    void testSslSocketFactoryDelegatesToConfiguredFactory() throws Exception {
        NacosLdapContextSource.LdapSslSocketFactory factory =
            new NacosLdapContextSource.LdapSslSocketFactory();
        SSLSocketFactory delegate = mock(SSLSocketFactory.class);
        Socket socket = mock(Socket.class);
        Socket createdSocket = mock(Socket.class);
        InetAddress address = InetAddress.getLoopbackAddress();
        ReflectionTestUtils.setField(factory, "socketFactory", delegate);
        when(delegate.getDefaultCipherSuites()).thenReturn(new String[] {"TLS_AES_128_GCM_SHA256"});
        when(delegate.getSupportedCipherSuites()).thenReturn(
            new String[] {"TLS_AES_256_GCM_SHA384"});
        when(delegate.createSocket(socket, "localhost", 636, true)).thenReturn(createdSocket);
        when(delegate.createSocket("localhost", 636)).thenReturn(createdSocket);
        when(delegate.createSocket("localhost", 636, address, 0)).thenReturn(createdSocket);
        when(delegate.createSocket(address, 636)).thenReturn(createdSocket);
        when(delegate.createSocket(address, 636, address, 0)).thenReturn(createdSocket);
        
        assertArrayEquals(new String[] {"TLS_AES_128_GCM_SHA256"},
            factory.getDefaultCipherSuites());
        assertArrayEquals(new String[] {"TLS_AES_256_GCM_SHA384"},
            factory.getSupportedCipherSuites());
        assertSame(createdSocket, factory.createSocket(socket, "localhost", 636, true));
        assertSame(createdSocket, factory.createSocket("localhost", 636));
        assertSame(createdSocket, factory.createSocket("localhost", 636, address, 0));
        assertSame(createdSocket, factory.createSocket(address, 636));
        assertSame(createdSocket, factory.createSocket(address, 636, address, 0));
    }
    
    @Test
    void testSslSocketFactoryKeepsNullDelegateWhenTlsHelperFails() {
        try (MockedStatic<TlsHelper> tlsHelper = mockStatic(TlsHelper.class)) {
            tlsHelper.when(() -> TlsHelper.buildSslContext(true))
                .thenThrow(new NoSuchAlgorithmException("missing"));
            
            NacosLdapContextSource.LdapSslSocketFactory factory =
                new NacosLdapContextSource.LdapSslSocketFactory();
            
            assertNull(ReflectionTestUtils.getField(factory, "socketFactory"));
        }
    }
    
    private static class TestableNacosLdapContextSource extends NacosLdapContextSource {
        
        TestableNacosLdapContextSource(String ldapUrl, String ldapBaseDc, String userDn,
            String password, String ldapTimeOut) {
            super(ldapUrl, ldapBaseDc, userDn, password, ldapTimeOut);
        }
        
        Hashtable<String, Object> anonymousEnvironment() {
            return getAnonymousEnv();
        }
    }
}
