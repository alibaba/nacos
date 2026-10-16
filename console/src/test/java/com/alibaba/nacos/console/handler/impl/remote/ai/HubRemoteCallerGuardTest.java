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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.constant.Constants.Hub;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.console.handler.impl.remote.ConsoleMaintainerClientAuthPlugin;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HubRemoteCallerGuardTest {
    
    private final HubRemoteCallerGuard guard = new HubRemoteCallerGuard();
    
    @AfterEach
    void clearContext() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void verifiedAnonymousReadsAreForwardedWithoutServerIdentityFallback() {
        AuthContext ctx = context();
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "anonymous");
        ctx.setIdentityContext(identity);
        ctx.setAuthResult(AuthResult.successResult());
        assertDoesNotThrow(guard::requireForwardableRead);
        assertThrows(NacosException.class, guard::requireForwardableUser);
        RequestResource resource =
            RequestResource.aiBuilder()
                .setResource(Hub.ENTRY_RESOURCE).build();
        assertEquals(0, new ConsoleMaintainerClientAuthPlugin().getLoginIdentityContext(resource)
            .getAllKey().size());
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, "true");
        assertThrows(NacosException.class, guard::requireForwardableRead);
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, false);
        ctx.setAuthResult(AuthResult.failureResult(401, "invalid token"));
        assertThrows(NacosException.class, guard::requireForwardableRead);
    }
    
    @Test
    void authenticatedCallerWithOriginalTokenMayForward() {
        AuthContext ctx = context();
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        identity.setRequestIdentityParameter("accessToken", "token-from-alice");
        ctx.setIdentityContext(identity);
        ctx.setAuthResult(AuthResult.successResult());
        assertDoesNotThrow(guard::requireForwardableUser);
    }
    
    @Test
    void noOriginalCredentialMustNotUseServerIdentityFallback() {
        AuthContext ctx = context();
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        ctx.setIdentityContext(identity);
        ctx.setAuthResult(AuthResult.successResult());
        assertEquals(NacosException.NO_RIGHT,
            assertThrows(NacosException.class, guard::requireForwardableUser).getErrCode());
    }
    
    @Test
    void serverIdentityEvenWithTokenIsRejected() {
        AuthContext ctx = context();
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        identity.setParameter(Constants.Identity.SERVER_IDENTITY, Boolean.TRUE);
        identity.setRequestIdentityParameter("accessToken", "token");
        ctx.setIdentityContext(identity);
        ctx.setAuthResult(AuthResult.successResult());
        assertThrows(NacosException.class, guard::requireForwardableUser);
    }
    
    @Test
    void unverifiedOrAnonymousCallerIsRejected() {
        AuthContext ctx = context();
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "anonymous");
        identity.setRequestIdentityParameter("accessToken", "token");
        ctx.setIdentityContext(identity);
        ctx.setAuthResult(AuthResult.successResult());
        assertThrows(NacosException.class, guard::requireForwardableUser);
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        ctx.setAuthResult(AuthResult.failureResult(403, "denied"));
        assertThrows(NacosException.class, guard::requireForwardableUser);
    }
    
    @Test
    void missingIdentityOrAuthResultIsRejected() {
        context();
        assertThrows(NacosException.class, guard::requireForwardableUser);
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, "alice");
        identity.setRequestIdentityParameter("accessToken", "alice-token");
        context().setIdentityContext(identity);
        assertThrows(NacosException.class, guard::requireForwardableUser);
    }
    
    @Test
    void wrongAudienceIsRejectedEvenWithValidCredentials() {
        AuthContext auth = authenticatedContext("alice", "alice-token");
        auth.setApiType(ApiType.ADMIN_API.name());
        assertThrows(NacosException.class, guard::requireForwardableUser);
    }
    
    @Test
    void blankOrNonStringPrincipalIsRejected() {
        AuthContext auth = authenticatedContext(" ", "alice-token");
        assertThrows(NacosException.class, guard::requireForwardableUser);
        auth.getIdentityContext().setParameter(Constants.Identity.IDENTITY_ID, 123);
        assertThrows(NacosException.class, guard::requireForwardableUser);
    }
    
    @Test
    void blankForwardedCredentialIsRejected() {
        authenticatedContext("alice", " ");
        assertThrows(NacosException.class, guard::requireForwardableUser);
    }
    
    @Test
    void acceptedUserTokenIsActuallyForwardedByConsoleAuthPlugin() throws Exception {
        authenticatedContext("alice", "original-user-token");
        guard.requireForwardableUser();
        assertEquals("original-user-token", new ConsoleMaintainerClientAuthPlugin()
            .getLoginIdentityContext(new com.alibaba.nacos.plugin.auth.api.RequestResource())
            .getParameter("accessToken"));
    }
    
    private AuthContext authenticatedContext(String principal, String token) {
        AuthContext auth = context();
        IdentityContext identity = new IdentityContext();
        identity.setParameter(Constants.Identity.IDENTITY_ID, principal);
        identity.setRequestIdentityParameter("accessToken", token);
        auth.setIdentityContext(identity);
        auth.setAuthResult(AuthResult.successResult());
        return auth;
    }
    
    private AuthContext context() {
        AuthContext auth = RequestContextHolder.getContext().getAuthContext();
        auth.setApiType(ApiType.CONSOLE_API.name());
        return auth;
    }
}
