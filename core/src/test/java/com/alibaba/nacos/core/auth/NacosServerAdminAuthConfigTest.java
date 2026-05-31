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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NacosServerAdminAuthConfig} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class NacosServerAdminAuthConfigTest {
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testGetAuthScope() {
        NacosServerAdminAuthConfig config = new NacosServerAdminAuthConfig();
        assertEquals(ApiType.ADMIN_API.name(), config.getAuthScope());
    }
    
    @Test
    void testIsAuthEnabledWhenDisabled() {
        NacosServerAdminAuthConfig config = new NacosServerAdminAuthConfig();
        assertFalse(config.isAuthEnabled());
    }
    
    @Test
    void testIsAuthEnabledWhenEnabled() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "true");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "key");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "value");
        NacosServerAdminAuthConfig config = new NacosServerAdminAuthConfig();
        assertTrue(config.isAuthEnabled());
    }
    
    @Test
    void testGetNacosAuthSystemType() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "ldap");
        NacosServerAdminAuthConfig config = new NacosServerAdminAuthConfig();
        assertEquals("ldap", config.getNacosAuthSystemType());
    }
    
    @Test
    void testIsSupportServerIdentity() {
        NacosServerAdminAuthConfig config = new NacosServerAdminAuthConfig();
        assertTrue(config.isSupportServerIdentity());
    }
    
    @Test
    void testGetServerIdentityKeyAndValue() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "admin-key");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE,
            "admin-value");
        NacosServerAdminAuthConfig config = new NacosServerAdminAuthConfig();
        assertEquals("admin-key", config.getServerIdentityKey());
        assertEquals("admin-value", config.getServerIdentityValue());
    }
    
    @Test
    void testValidateThrowsWhenAuthEnabledButEmptyType() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "true");
        NacosRuntimeException ex =
            assertThrows(NacosRuntimeException.class, NacosServerAdminAuthConfig::new);
        assertEquals(AuthErrorCode.INVALID_TYPE.getCode(), ex.getErrCode());
        assertTrue(ex.getMessage().contains(AuthErrorCode.INVALID_TYPE.getMsg()));
    }
    
    @Test
    void testValidateThrowsWhenAuthEnabledButEmptyIdentity() {
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "true");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "nacos");
        NacosRuntimeException ex =
            assertThrows(NacosRuntimeException.class, NacosServerAdminAuthConfig::new);
        assertEquals(AuthErrorCode.EMPTY_IDENTITY.getCode(), ex.getErrCode());
        assertTrue(ex.getMessage().contains(AuthErrorCode.EMPTY_IDENTITY.getMsg()));
    }
    
    @Test
    void testToString() {
        NacosServerAdminAuthConfig config = new NacosServerAdminAuthConfig();
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("NacosServerAdminAuthConfig"));
        assertTrue(str.contains("authEnabled"));
    }
}
