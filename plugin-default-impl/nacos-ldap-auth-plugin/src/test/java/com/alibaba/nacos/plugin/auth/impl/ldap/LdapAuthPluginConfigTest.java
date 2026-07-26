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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LdapAuthPluginConfigTest {
    
    @Test
    void testDefaultsAndCustomConfig() {
        LdapAuthPluginConfig defaults = LdapAuthPluginConfig.defaults();
        assertEquals(LdapAuthPluginConfig.DEFAULT_URL, defaults.getUrl());
        assertEquals(LdapAuthPluginConfig.DEFAULT_BASE_DN, defaults.getBaseDn());
        assertEquals(LdapAuthPluginConfig.DEFAULT_TIMEOUT, defaults.getTimeout());
        assertEquals(LdapAuthPluginConfig.DEFAULT_USER_DN, defaults.getUserDn());
        assertEquals(LdapAuthPluginConfig.DEFAULT_PASSWORD, defaults.getPassword());
        assertEquals(LdapAuthPluginConfig.DEFAULT_FILTER_PREFIX, defaults.getFilterPrefix());
        assertTrue(defaults.isCaseSensitive());
        assertFalse(defaults.isIgnorePartialResultException());
        assertEquals(defaults.toMap(), LdapAuthPluginConfig.from(null).toMap());
        
        Map<String, String> values = new LinkedHashMap<>();
        values.put(LdapAuthPluginConfig.URL, "ldaps://ldap.example.com:636");
        values.put(LdapAuthPluginConfig.BASE_DN, "dc=nacos,dc=io");
        values.put(LdapAuthPluginConfig.TIMEOUT, "6000");
        values.put(LdapAuthPluginConfig.USER_DN, "cn=reader,dc=nacos,dc=io");
        values.put(LdapAuthPluginConfig.PASSWORD, "secret");
        values.put(LdapAuthPluginConfig.FILTER_PREFIX, "mail");
        values.put(LdapAuthPluginConfig.CASE_SENSITIVE, "FALSE");
        values.put(LdapAuthPluginConfig.IGNORE_PARTIAL_RESULT_EXCEPTION, "TRUE");
        
        LdapAuthPluginConfig config = LdapAuthPluginConfig.from(values);
        
        assertEquals("ldaps://ldap.example.com:636", config.getUrl());
        assertEquals("dc=nacos,dc=io", config.getBaseDn());
        assertEquals(6000L, config.getTimeout());
        assertEquals("cn=reader,dc=nacos,dc=io", config.getUserDn());
        assertEquals("secret", config.getPassword());
        assertEquals("mail", config.getFilterPrefix());
        assertFalse(config.isCaseSensitive());
        assertTrue(config.isIgnorePartialResultException());
        values.put(LdapAuthPluginConfig.CASE_SENSITIVE, "false");
        values.put(LdapAuthPluginConfig.IGNORE_PARTIAL_RESULT_EXCEPTION, "true");
        assertEquals(values, config.toMap());
    }
    
    @Test
    void testRejectsInvalidValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(LdapAuthPluginConfig.URL, null);
        assertThrows(IllegalArgumentException.class,
            () -> LdapAuthPluginConfig.from(values));
        
        values.clear();
        values.put(LdapAuthPluginConfig.TIMEOUT, "invalid");
        assertThrows(IllegalArgumentException.class,
            () -> LdapAuthPluginConfig.from(values));
        values.put(LdapAuthPluginConfig.TIMEOUT, "0");
        assertThrows(IllegalArgumentException.class,
            () -> LdapAuthPluginConfig.from(values));
        
        values.clear();
        values.put(LdapAuthPluginConfig.CASE_SENSITIVE, "invalid");
        assertThrows(IllegalArgumentException.class,
            () -> LdapAuthPluginConfig.from(values));
        values.clear();
        values.put(LdapAuthPluginConfig.IGNORE_PARTIAL_RESULT_EXCEPTION, "invalid");
        assertThrows(IllegalArgumentException.class,
            () -> LdapAuthPluginConfig.from(values));
    }
}
