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

package com.alibaba.nacos.plugin.auth.impl.utils;

import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthIdentityUtilsTest {
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void resolveCurrentUsernameShouldReturnNacosUserName() {
        setCurrentIdentity(new NacosUser("alice"));
        
        assertEquals("alice", AuthIdentityUtils.resolveCurrentUsername());
    }
    
    @Test
    void resolveCurrentUsernameShouldReturnNullForMissingOrUnexpectedIdentity() {
        assertNull(AuthIdentityUtils.resolveCurrentUsername());
        
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, "alice");
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
        
        assertNull(AuthIdentityUtils.resolveCurrentUsername());
    }
    
    @Test
    void isCurrentIdentityGlobalAdminShouldRequireSameGlobalAdminUser() {
        NacosUser user = new NacosUser("alice");
        user.setGlobalAdmin(true);
        setCurrentIdentity(user);
        
        assertTrue(AuthIdentityUtils.isCurrentIdentityGlobalAdmin("alice"));
        assertFalse(AuthIdentityUtils.isCurrentIdentityGlobalAdmin("bob"));
    }
    
    @Test
    void isCurrentIdentityGlobalAdminShouldReturnFalseForBlankOrNonAdminIdentity() {
        assertFalse(AuthIdentityUtils.isCurrentIdentityGlobalAdmin(""));
        
        NacosUser user = new NacosUser("alice");
        user.setGlobalAdmin(false);
        setCurrentIdentity(user);
        
        assertFalse(AuthIdentityUtils.isCurrentIdentityGlobalAdmin("alice"));
    }
    
    private void setCurrentIdentity(Object nacosUser) {
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, nacosUser);
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
    }
}
