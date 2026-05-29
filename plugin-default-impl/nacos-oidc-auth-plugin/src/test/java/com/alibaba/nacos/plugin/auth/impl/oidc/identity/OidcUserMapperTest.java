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

package com.alibaba.nacos.plugin.auth.impl.oidc.identity;

import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OidcUserMapperTest {
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(OidcUserMapper.class, "instance", null);
    }
    
    @Test
    void testMapToUserCopiesClaimsAndValidatorAttributes() {
        JwtTokenValidator tokenValidator = mock(JwtTokenValidator.class);
        OidcUserMapper mapper = newMapper(tokenValidator);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject("subject")
            .issuer("issuer")
            .claim("email", "nacos@nacos.io")
            .claim("name", "Nacos")
            .build();
        when(tokenValidator.extractUsername(claims)).thenReturn("nacos");
        when(tokenValidator.extractRoles(claims)).thenReturn(Arrays.asList("reader", "admin"));
        when(tokenValidator.isAdmin(claims)).thenReturn(true);
        
        OidcUser user = mapper.mapToUser(claims);
        
        assertEquals("nacos", user.getUsername());
        assertEquals("subject", user.getSubject());
        assertEquals("issuer", user.getIssuer());
        assertEquals("nacos@nacos.io", user.getEmail());
        assertEquals("Nacos", user.getName());
        assertEquals(Arrays.asList("reader", "admin"), user.getRoles());
        assertTrue(user.isGlobalAdmin());
        assertTrue(user.toString().contains("nacos"));
    }
    
    @Test
    void testOidcUserAccessors() {
        OidcUser user = new OidcUser();
        user.setUsername("nacos");
        user.setSubject("subject");
        user.setEmail("nacos@nacos.io");
        user.setName("Nacos");
        user.setIssuer("issuer");
        user.setRoles(Arrays.asList("reader"));
        user.setGlobalAdmin(true);
        user.setToken("token");
        
        assertEquals("nacos", user.getUsername());
        assertEquals("subject", user.getSubject());
        assertEquals("nacos@nacos.io", user.getEmail());
        assertEquals("Nacos", user.getName());
        assertEquals("issuer", user.getIssuer());
        assertEquals(Arrays.asList("reader"), user.getRoles());
        assertTrue(user.isGlobalAdmin());
        assertEquals("token", user.getToken());
    }
    
    private OidcUserMapper newMapper(JwtTokenValidator tokenValidator) {
        ReflectionTestUtils.setField(OidcUserMapper.class, "instance", null);
        try (MockedStatic<OidcAuthConfig> configStatic = mockStatic(OidcAuthConfig.class);
            MockedStatic<JwtTokenValidator> validatorStatic =
                mockStatic(JwtTokenValidator.class)) {
            configStatic.when(OidcAuthConfig::getInstance).thenReturn(mock(OidcAuthConfig.class));
            validatorStatic.when(JwtTokenValidator::getInstance).thenReturn(tokenValidator);
            return OidcUserMapper.getInstance();
        }
    }
}
