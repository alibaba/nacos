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

package com.alibaba.nacos.client.auth.ram.injector;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.auth.ram.RamContext;
import com.alibaba.nacos.client.auth.ram.identify.IdentifyConstants;
import com.alibaba.nacos.client.auth.ram.identify.StsConfig;
import com.alibaba.nacos.client.auth.ram.identify.StsCredential;
import com.alibaba.nacos.client.auth.ram.identify.StsCredentialHolder;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockResourceInjectorTest {
    
    private LockResourceInjector lockResourceInjector;
    
    private RamContext ramContext;
    
    private RequestResource resource;
    
    private String cachedSecurityCredentialsUrl;
    
    private String cachedSecurityCredentials;
    
    private StsCredential stsCredential;
    
    @BeforeEach
    void setUp() {
        lockResourceInjector = new LockResourceInjector();
        ramContext = new RamContext();
        ramContext.setAccessKey(PropertyKeyConst.ACCESS_KEY);
        ramContext.setSecretKey(PropertyKeyConst.SECRET_KEY);
        resource = new RequestResource();
        cachedSecurityCredentialsUrl = StsConfig.getInstance().getSecurityCredentialsUrl();
        cachedSecurityCredentials = StsConfig.getInstance().getSecurityCredentials();
        StsConfig.getInstance().setSecurityCredentialsUrl("");
        StsConfig.getInstance().setSecurityCredentials("");
        stsCredential = new StsCredential();
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        StsConfig.getInstance().setSecurityCredentialsUrl(cachedSecurityCredentialsUrl);
        StsConfig.getInstance().setSecurityCredentials(cachedSecurityCredentials);
        clearForSts();
    }
    
    @Test
    void testDoInjectWithAkSk() {
        LoginIdentityContext actual = new LoginIdentityContext();
        lockResourceInjector.doInject(resource, ramContext, actual);
        assertEquals(1, actual.getAllKey().size());
        assertEquals(PropertyKeyConst.ACCESS_KEY, actual.getParameter("ak"));
    }
    
    @Test
    void testDoInjectWithoutSecretKey() {
        ramContext.setSecretKey(null);
        LoginIdentityContext actual = new LoginIdentityContext();
        lockResourceInjector.doInject(resource, ramContext, actual);
        assertEquals(0, actual.getAllKey().size());
        assertNull(actual.getParameter("ak"));
    }
    
    @Test
    void testDoInjectForSts() throws NoSuchFieldException, IllegalAccessException {
        prepareForSts();
        LoginIdentityContext actual = new LoginIdentityContext();
        lockResourceInjector.doInject(resource, ramContext, actual);
        assertEquals(2, actual.getAllKey().size());
        assertEquals("test-sts-ak", actual.getParameter("ak"));
        assertTrue(actual.getAllKey().contains(IdentifyConstants.SECURITY_TOKEN_HEADER));
    }
    
    private void prepareForSts() throws NoSuchFieldException, IllegalAccessException {
        StsConfig.getInstance().setSecurityCredentialsUrl("test");
        Field field = StsCredentialHolder.class.getDeclaredField("stsCredential");
        field.setAccessible(true);
        field.set(StsCredentialHolder.getInstance(), stsCredential);
        stsCredential.setAccessKeyId("test-sts-ak");
        stsCredential.setAccessKeySecret("test-sts-sk");
        stsCredential.setSecurityToken("test-sts-token");
        stsCredential.setExpiration(new Date(System.currentTimeMillis() + 1_000_000));
    }
    
    private void clearForSts() throws NoSuchFieldException, IllegalAccessException {
        StsConfig.getInstance().setSecurityCredentialsUrl(null);
        Field field = StsCredentialHolder.class.getDeclaredField("stsCredential");
        field.setAccessible(true);
        field.set(StsCredentialHolder.getInstance(), null);
    }
}
