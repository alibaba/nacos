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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.ldap.LdapPluginDependencyChecker;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapAuthPluginServiceTest {
    
    @Mock
    private IAuthenticationManager authenticationManager;
    
    @Test
    void testGetAuthServiceName() {
        assertEquals(AuthConstants.LDAP_AUTH_PLUGIN_TYPE,
            new LdapAuthPluginService().getAuthServiceName());
    }
    
    @Test
    void testValidateIdentityLoadsLdapAuthenticationManager() throws AccessException {
        LdapAuthPluginService service = new LdapAuthPluginService();
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(AuthConstants.PARAM_USERNAME, "nacos");
        identityContext.setParameter(AuthConstants.PARAM_PASSWORD, "password");
        NacosUser user = new NacosUser("nacos", "token");
        when(authenticationManager.authenticate("nacos", "password")).thenReturn(user);
        
        try (MockedStatic<ApplicationUtils> applicationUtils = mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(() -> ApplicationUtils.getBean(
                LdapPluginDependencyChecker.LDAP_AUTHENTICATION_MANAGER_BEAN_NAME,
                IAuthenticationManager.class)).thenReturn(authenticationManager);
            AuthResult<?> result = service.validateIdentity(identityContext, null);
            
            assertTrue(result.isSuccess());
            assertSame(user, result.getData());
            assertSame(user, identityContext.getParameter(AuthConstants.NACOS_USER_KEY));
        }
    }
}
