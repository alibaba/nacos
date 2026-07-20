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

package com.alibaba.nacos.plugin.auth.impl.configuration;

import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.condition.ConditionOnLdapAuth;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConditionOnLdapAuth test.
 *
 * @author ChenHao26
 */
class ConditionOnLdapAuthTest {
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void matches() {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        ConditionOnLdapAuth condition = new ConditionOnLdapAuth();
        assertFalse(condition.matches(null, null));
        
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "ldap");
        assertTrue(condition.matches(null, null));
        
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "nacos");
        assertFalse(condition.matches(null, null));
        
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "ldap");
        assertTrue(condition.matches(null, null));
    }
}
