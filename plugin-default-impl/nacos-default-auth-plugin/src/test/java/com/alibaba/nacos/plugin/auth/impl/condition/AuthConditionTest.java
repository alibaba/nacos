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

package com.alibaba.nacos.plugin.auth.impl.condition;

import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthConditionTest {
    
    @Mock
    private ConditionContext conditionContext;
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testConditionOnNacosAuth() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE,
            AuthSystemTypes.NACOS.name().toLowerCase());
        EnvUtil.setEnvironment(environment);
        
        assertTrue(new ConditionOnNacosAuth().matches(null, null));
        
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "ldap");
        assertFalse(new ConditionOnNacosAuth().matches(null, null));
    }
    
    @Test
    void testConditionOnRemoteDatasource() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(com.alibaba.nacos.sys.env.Constants.NACOS_DEPLOYMENT_TYPE,
            com.alibaba.nacos.sys.env.Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE);
        when(conditionContext.getEnvironment()).thenReturn(environment);
        
        assertTrue(new ConditionOnRemoteDatasource().matches(conditionContext, null));
        
        environment.setProperty(com.alibaba.nacos.sys.env.Constants.NACOS_DEPLOYMENT_TYPE,
            "server");
        assertFalse(new ConditionOnRemoteDatasource().matches(conditionContext, null));
    }
    
    @Test
    void testConditionOnInnerDatasource() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(com.alibaba.nacos.sys.env.Constants.NACOS_DEPLOYMENT_TYPE,
            "server");
        when(conditionContext.getEnvironment()).thenReturn(environment);
        
        assertTrue(new ConditionOnInnerDatasource().matches(conditionContext, null));
        
        environment.setProperty(com.alibaba.nacos.sys.env.Constants.NACOS_DEPLOYMENT_TYPE,
            com.alibaba.nacos.sys.env.Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE);
        assertFalse(new ConditionOnInnerDatasource().matches(conditionContext, null));
    }
}
