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

package com.alibaba.nacos.test.core.auth;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.auth.config.AuthConfigs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URL;
import java.util.concurrent.TimeUnit;

@RunWith(Suite.class)
@Suite.SuiteClasses({LdapAuth_ITCase.NonTlsTest.class, LdapAuth_ITCase.TlsTest.class})
class LdapAuth_ITCase extends AuthBase {
    
    @LocalServerPort
    private int port;
    
    private String filterPrefix = "uid";
    
    @MockBean
    private LdapTemplate ldapTemplate;
    
    @BeforeEach
    void init() throws Exception {
        Mockito.when(ldapTemplate.authenticate("", "(" + filterPrefix + "=" + "karson" + ")", "karson")).thenReturn(true);
        AuthConfigs.setCachingEnabled(false);
        TimeUnit.SECONDS.sleep(5L);
        String url = String.format("http://localhost:%d/", port);
        System.setProperty("nacos.core.auth.enabled", "true");
        this.base = new URL(url);
    }
    
    @Nested
    @ExtendWith(SpringExtension.class)
    @SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos",
            "nacos.core.auth.system.type=ldap"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
    class NonTlsTest extends LdapAuth_ITCase {
        
        @Test
        void testLdapAuth() throws Exception {
            super.login("karson", "karson");
        }
    }
    
    @Nested
    @ExtendWith(SpringExtension.class)
    @SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos", "nacos.core.auth.system.type=ldap",
            "nacos.core.auth.ldap.url=ldaps://localhost:636"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
    class TlsTest extends LdapAuth_ITCase {
        
        @Test
        void testLdapAuth() throws Exception {
            super.login("karson", "karson");
        }
    }
    
    
}
