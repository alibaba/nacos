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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigCloneSourceNamespaceAuthUtilTest {

    @Mock
    private AuthConfigs authConfigs;

    @Mock
    private AuthPluginService authPluginService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }

    @Test
    void testCheckReadPermissionWhenAuthDisabled() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(false);

        newTestUtil(authPluginService).checkReadPermission("sourceTenant");

        verifyNoInteractions(authPluginService);
    }

    @Test
    void testCheckReadPermissionWhenIdentityMissing() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);

        newTestUtil(authPluginService).checkReadPermission("sourceTenant");

        verifyNoInteractions(authPluginService);
    }

    @Test
    void testCheckReadPermissionWhenPluginDisabled() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.CONFIG)).thenReturn(false);
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(new IdentityContext());

        newTestUtil(authPluginService).checkReadPermission("sourceTenant");

        verify(authPluginService).enableAuth(ActionTypes.READ, SignType.CONFIG);
        verify(authPluginService, never()).validateAuthority(any(IdentityContext.class), any(Permission.class));
    }

    @Test
    void testCheckReadPermissionWhenAllowed() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.CONFIG)).thenReturn(true);
        when(authPluginService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(true);
        IdentityContext identityContext = new IdentityContext();
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);

        newTestUtil(authPluginService).checkReadPermission("sourceTenant");

        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(authPluginService).validateAuthority(eq(identityContext), permissionCaptor.capture());
        Permission permission = permissionCaptor.getValue();
        assertEquals(ActionTypes.READ.toString(), permission.getAction());
        assertEquals("sourceTenant", permission.getResource().getNamespaceId());
        assertEquals(SignType.CONFIG, permission.getResource().getType());
        assertEquals(ActionTypes.READ.toString(),
                permission.getResource().getProperties().get(Constants.Resource.ACTION));
    }

    @Test
    void testCheckReadPermissionWhenDenied() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authConfigs.getNacosAuthSystemType()).thenReturn("nacos");
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.CONFIG)).thenReturn(true);
        when(authPluginService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(false);
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(new IdentityContext());

        assertThrows(AccessException.class, () -> newTestUtil(authPluginService).checkReadPermission("sourceTenant"));
    }

    @Test
    void testCheckReadPermissionWhenPluginThrowsAccessException() throws AccessException {
        when(authConfigs.isAuthEnabled()).thenReturn(true);
        when(authPluginService.enableAuth(ActionTypes.READ, SignType.CONFIG)).thenReturn(true);
        when(authPluginService.validateAuthority(any(IdentityContext.class), any(Permission.class)))
                .thenThrow(new AccessException("authorization failed!"));
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(new IdentityContext());

        AccessException exception = assertThrows(AccessException.class,
                () -> newTestUtil(authPluginService).checkReadPermission("sourceTenant"));
        assertEquals(NacosException.NO_RIGHT, exception.getErrCode());
        assertEquals("authorization failed!", exception.getErrMsg());
    }

    private TestConfigCloneSourceNamespaceAuthUtil newTestUtil(AuthPluginService authPluginService) {
        return new TestConfigCloneSourceNamespaceAuthUtil(authConfigs, authPluginService);
    }

    private static class TestConfigCloneSourceNamespaceAuthUtil extends ConfigCloneSourceNamespaceAuthUtil {

        private final Optional<AuthPluginService> authPluginService;

        TestConfigCloneSourceNamespaceAuthUtil(AuthConfigs authConfigs, AuthPluginService authPluginService) {
            super(authConfigs);
            this.authPluginService = Optional.ofNullable(authPluginService);
        }

        @Override
        Optional<AuthPluginService> findAuthPluginService() {
            return authPluginService;
        }
    }
}
