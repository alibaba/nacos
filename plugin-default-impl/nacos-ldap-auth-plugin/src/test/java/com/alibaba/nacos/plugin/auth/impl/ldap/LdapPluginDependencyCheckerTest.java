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

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LdapPluginDependencyCheckerTest {
    
    @Test
    void testHasRequiredDependencyWithKnownPresentClass() {
        assertTrue(LdapPluginDependencyChecker.hasRequiredDependency("java.lang.String"));
    }
    
    @Test
    void testHasRequiredDependencyWithMissingClass() {
        String missingClassName = "com.alibaba.nacos.test.MissingClass";
        assertFalse(LdapPluginDependencyChecker.hasRequiredDependency(missingClassName));
    }
    
    @Test
    void testHasRequiredDependencyWithDefaultDependency() {
        assertTrue(LdapPluginDependencyChecker.hasRequiredDependency());
    }
    
    @Test
    void testHasRequiredDependencyWithoutContextClassLoader() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            assertTrue(LdapPluginDependencyChecker.hasRequiredDependency("java.lang.String"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
    
    @Test
    void testHasRequiredDependencyWithApplicationClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Object originalApplicationContext =
            ReflectionTestUtils.getField(ApplicationUtils.class, "applicationContext");
        ConfigurableApplicationContext applicationContext =
            mock(ConfigurableApplicationContext.class);
        when(applicationContext.getClassLoader()).thenReturn(getClass().getClassLoader());
        try {
            Thread.currentThread().setContextClassLoader(null);
            ApplicationUtils.injectContext(applicationContext);
            
            assertTrue(LdapPluginDependencyChecker.hasRequiredDependency("java.lang.String"));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            ReflectionTestUtils.setField(ApplicationUtils.class, "applicationContext",
                originalApplicationContext);
        }
    }
    
    @Test
    void testBuildMissingDependencyMessage() {
        String message = LdapPluginDependencyChecker.buildMissingDependencyMessage();
        
        assertTrue(message.contains("spring-ldap-core"));
        assertTrue(message.contains("nacos.core.auth.system.type=ldap"));
    }
}
