/*
 *  Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.auth.config.AuthErrorCode;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NacosServerAuthConfig} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class NacosServerAuthConfigTest {
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testGetAuthScope() {
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        assertEquals(ApiType.OPEN_API.name(), config.getAuthScope());
    }
    
    @Test
    void testIsAuthEnabledWhenDisabled() {
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        assertFalse(config.isAuthEnabled());
    }
    
    @Test
    void testIsAuthEnabledWhenEnabled() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "true");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "key");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "value");
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        assertTrue(config.isAuthEnabled());
    }
    
    @Test
    void testGetNacosAuthSystemType() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "ldap");
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        assertEquals("ldap", config.getNacosAuthSystemType());
    }
    
    @Test
    void testIsSupportServerIdentity() {
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        assertTrue(config.isSupportServerIdentity());
    }
    
    @Test
    void testGetServerIdentityKeyAndValue() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "identity-key");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "identity-value");
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        assertEquals("identity-key", config.getServerIdentityKey());
        assertEquals("identity-value", config.getServerIdentityValue());
    }
    
    @Test
    void testGetAuthPluginPropertiesWhenTypeNotFound() {
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        Properties props = config.getAuthPluginProperties("unknownType");
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }
    
    @Test
    void testGetAuthPluginPropertiesWithPrefix() {
        environment.setProperty("nacos.core.auth.plugin.nacos.key1", "value1");
        environment.setProperty("nacos.core.auth.plugin.nacos.key2", "value2");
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        Properties props = config.getAuthPluginProperties("nacos");
        assertNotNull(props);
        assertEquals("value1", props.getProperty("key1"));
        assertEquals("value2", props.getProperty("key2"));
    }
    
    @Test
    void testValidateThrowsWhenAuthEnabledButEmptyType() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "true");
        NacosRuntimeException ex = assertThrows(NacosRuntimeException.class, NacosServerAuthConfig::new);
        assertEquals(AuthErrorCode.INVALID_TYPE.getCode(), ex.getErrCode());
        assertTrue(ex.getMessage().contains(AuthErrorCode.INVALID_TYPE.getMsg()));
    }
    
    @Test
    void testValidateThrowsWhenAuthEnabledButEmptyIdentity() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "true");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        NacosRuntimeException ex = assertThrows(NacosRuntimeException.class, NacosServerAuthConfig::new);
        assertEquals(AuthErrorCode.EMPTY_IDENTITY.getCode(), ex.getErrCode());
        assertTrue(ex.getMessage().contains(AuthErrorCode.EMPTY_IDENTITY.getMsg()));
    }
    
    @Test
    void testToString() {
        NacosServerAuthConfig config = new NacosServerAuthConfig();
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("NacosServerAuthConfig"));
        assertTrue(str.contains("authEnabled"));
    }
}
