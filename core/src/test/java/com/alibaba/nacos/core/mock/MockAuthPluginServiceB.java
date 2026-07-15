/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.mock;

import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;

import java.util.Collection;
import java.util.Collections;

public class MockAuthPluginServiceB implements AuthPluginService {
    
    public static final String TEST_PLUGIN = "testB";
    
    public static final String IDENTITY_TEST_KEY = "identity-test-key";
    
    @Override
    public Collection<String> identityNames() {
        return Collections.singletonList(IDENTITY_TEST_KEY);
    }
    
    @Override
    public boolean enableAuth(ActionTypes action, String type) {
        return true;
    }
    
    @Override
    public String getAuthServiceName() {
        return TEST_PLUGIN;
    }
    
    @Override
    public boolean isLoginEnabled() {
        return true;
    }
    
    @Override
    public boolean isAdminRequest() {
        return false;
    }
    
    @Override
    public AuthResult validateAuthority(IdentityContext identityContext, Permission permission) {
        return AuthResult.failureResult(401, "mock auth failed");
    }
    
    @Override
    public AuthResult validateIdentity(IdentityContext identityContext, Resource resource) {
        return AuthResult.failureResult(403, "mock auth failed");
    }
}
