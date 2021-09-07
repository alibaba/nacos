/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.auth.AuthService;
import com.alibaba.nacos.auth.context.IdentityContext;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.model.User;
import org.springframework.stereotype.Component;

@Component
public class MockAuthManager implements AuthService {
    
    @Override
    public User login(IdentityContext identityContext) throws AccessException {
        return null;
    }
    
    @Override
    public Boolean authorityAccess(IdentityContext identityContext, Permission permission) throws AccessException {
        return null;
    }
    
    @Override
    public String getAuthServiceName() {
        return null;
    }
    
}
