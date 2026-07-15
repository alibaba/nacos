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
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultLdapTemplateProviderTest {
    
    @Test
    void testBuildsAndReusesTemplateForAcceptedConfig() {
        AtomicReference<LdapAuthPluginConfig> config =
            new AtomicReference<>(LdapAuthPluginConfig.defaults());
        DefaultLdapTemplateProvider provider =
            new DefaultLdapTemplateProvider(config::get);
        
        LdapTemplate first = provider.getLdapTemplate();
        LdapTemplate reused = provider.getLdapTemplate();
        
        assertSame(first, reused);
        assertTemplate(first, "ldap://localhost:389", false);
        
        config.set(LdapAuthPluginConfig.from(Collections.singletonMap(
            LdapAuthPluginConfig.IGNORE_PARTIAL_RESULT_EXCEPTION, "true")));
        LdapTemplate refreshed = provider.getLdapTemplate();
        
        assertNotSame(first, refreshed);
        assertTemplate(refreshed, "ldap://localhost:389", true);
    }
    
    private void assertTemplate(LdapTemplate template, String url,
        boolean ignorePartialResultException) {
        LdapContextSource contextSource = (LdapContextSource) ReflectionTestUtils.getField(template,
            "contextSource");
        assertArrayEquals(new String[] {url}, contextSource.getUrls());
        assertEquals(ignorePartialResultException,
            ReflectionTestUtils.getField(template, "ignorePartialResultException"));
    }
}
