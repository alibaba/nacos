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
import org.springframework.util.ClassUtils;

/**
 * LDAP plugin dependency checker.
 *
 * @author xiweng.yy
 */
public final class LdapPluginDependencyChecker {
    
    public static final String LDAP_AUTHENTICATION_MANAGER_BEAN_NAME = "ldapAuthenticatoinManager";
    
    static final String LDAP_TEMPLATE_CLASS_NAME = "org.springframework.ldap.core.LdapTemplate";
    
    private LdapPluginDependencyChecker() {
    }
    
    public static boolean hasRequiredDependency() {
        return hasRequiredDependency(LDAP_TEMPLATE_CLASS_NAME);
    }
    
    static boolean hasRequiredDependency(String className) {
        return ClassUtils.isPresent(className, resolveClassLoader());
    }
    
    /**
     * Build missing LDAP runtime dependency message.
     *
     * @return missing dependency message
     */
    public static String buildMissingDependencyMessage() {
        return "LDAP auth plugin requires org.springframework.ldap:spring-ldap-core in "
            + "plugins/classpath "
            + "when nacos.core.auth.system.type=ldap. Please add spring-ldap-core jar into "
            + "the plugins "
            + "directory.";
    }
    
    private static ClassLoader resolveClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (null != classLoader) {
            return classLoader;
        }
        try {
            return ApplicationUtils.getClassLoader();
        } catch (NullPointerException ignored) {
            return LdapPluginDependencyChecker.class.getClassLoader();
        }
    }
}
