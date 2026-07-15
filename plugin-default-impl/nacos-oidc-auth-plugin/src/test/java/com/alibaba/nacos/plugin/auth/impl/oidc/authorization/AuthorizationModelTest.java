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

package com.alibaba.nacos.plugin.auth.impl.oidc.authorization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationModelTest {
    
    @Test
    void testRequestBuildsResourceUriFromExplicitResource() {
        AuthorizationRequest request = new AuthorizationRequest("token", "nacos:config", "read");
        
        assertEquals("nacos:config", request.buildResourceUri());
        assertEquals("read", request.getAction());
        assertEquals("token", request.getToken());
        assertEquals("nacos:config", request.getResource());
    }
    
    @Test
    void testRequestSettersAndMinimalResourceUri() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setToken("token");
        request.setAction("write");
        request.setResourceType("config");
        request.setNamespace("public");
        request.setGroup("DEFAULT_GROUP");
        request.setResourceName("data.yaml");
        
        assertEquals("token", request.getToken());
        assertEquals("write", request.getAction());
        assertEquals("config", request.getResourceType());
        assertEquals("public", request.getNamespace());
        assertEquals("DEFAULT_GROUP", request.getGroup());
        assertEquals("data.yaml", request.getResourceName());
        assertEquals("nacos:config:public:DEFAULT_GROUP:data.yaml", request.buildResourceUri());
        request.setResource("nacos:explicit");
        assertEquals("nacos:explicit", request.getResource());
        assertEquals("nacos:explicit", request.buildResourceUri());
    }
    
    @Test
    void testRequestBuildsResourceUriFromComponents() {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .token("token")
            .resourceType("config")
            .namespace("public")
            .group("DEFAULT_GROUP")
            .resourceName("data.yaml")
            .action("write")
            .build();
        
        assertEquals("nacos:config:public:DEFAULT_GROUP:data.yaml", request.buildResourceUri());
        assertEquals(
            "{\"token\":\"token\",\"resource\":\"nacos:config:public:DEFAULT_GROUP:data.yaml\","
                + "\"action\":\"write\",\"resourceType\":\"config\",\"namespace\":\"public\","
                + "\"group\":\"DEFAULT_GROUP\",\"resourceName\":\"data.yaml\"}",
            request.toJson());
    }
    
    @Test
    void testRequestEscapesJsonValues() {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .token("tok\"en")
            .resource("nacos\\config\nname")
            .action("read\twrite")
            .build();
        
        assertEquals("{\"token\":\"tok\\\"en\",\"resource\":\"nacos\\\\config\\nname\","
            + "\"action\":\"read\\twrite\"}", request.toJson());
    }
    
    @Test
    void testRequestSerializesNullJsonValues() {
        AuthorizationRequest request = new AuthorizationRequest();
        
        assertEquals("{\"token\":\"\",\"resource\":\"nacos\",\"action\":\"\"}",
            request.toJson());
    }
    
    @Test
    void testResponseFactoryMethodsAndSetters() {
        AuthorizationResponse allowed = AuthorizationResponse.allowed();
        AuthorizationResponse denied = AuthorizationResponse.denied("blocked");
        AuthorizationResponse constructed = new AuthorizationResponse(false);
        AuthorizationResponse custom = new AuthorizationResponse();
        custom.setAllowed(true);
        custom.setReason("ok");
        custom.setErrorCode("none");
        
        assertTrue(allowed.isAllowed());
        assertFalse(denied.isAllowed());
        assertFalse(constructed.isAllowed());
        assertEquals("blocked", denied.getReason());
        assertEquals("ok", custom.getReason());
        assertEquals("none", custom.getErrorCode());
        assertEquals("AuthorizationResponse{allowed=true, reason='ok'}", custom.toString());
    }
    
    @Test
    void testResponseParsesSupportedJsonShapes() {
        assertTrue(AuthorizationResponse.fromJson("{\"allowed\":true}").isAllowed());
        assertTrue(AuthorizationResponse.fromJson("{\"result\":\"PERMIT\"}").isAllowed());
        assertTrue(AuthorizationResponse.fromJson("{\"result\": \"permit\"}").isAllowed());
        assertTrue(AuthorizationResponse.fromJson("{\"decision\":\"permit\"}").isAllowed());
        assertTrue(AuthorizationResponse.fromJson("{\"decision\": \"Permit\"}").isAllowed());
        
        AuthorizationResponse denied =
            AuthorizationResponse.fromJson("{\"allowed\": false, \"message\":\"no\","
                + "\"errorCode\":\"E403\"}");
        assertFalse(denied.isAllowed());
        assertEquals("no", denied.getReason());
        assertEquals("E403", denied.getErrorCode());
        
        AuthorizationResponse errorDescription =
            AuthorizationResponse.fromJson("{\"allowed\":false,\"error_description\":\"bad\","
                + "\"error\":\"E_BAD\"}");
        assertFalse(errorDescription.isAllowed());
        assertEquals("bad", errorDescription.getReason());
        assertEquals("E_BAD", errorDescription.getErrorCode());
    }
    
    @Test
    void testResponseHandlesEmptyAndMalformedJsonValues() {
        AuthorizationResponse empty = AuthorizationResponse.fromJson(" ");
        AuthorizationResponse missingColon = AuthorizationResponse.fromJson("{\"allowed\" true}");
        AuthorizationResponse missingQuote = AuthorizationResponse.fromJson("{\"error\": true}");
        AuthorizationResponse missingEndQuote =
            AuthorizationResponse.fromJson("{\"error\":\"E403}");
        AuthorizationResponse reasonMissingColon =
            AuthorizationResponse.fromJson("{\"reason\" \"bad\"}");
        
        assertFalse(empty.isAllowed());
        assertEquals("Empty response from IdP", empty.getReason());
        assertNull(missingColon.getReason());
        assertNull(missingQuote.getErrorCode());
        assertNull(missingEndQuote.getErrorCode());
        assertNull(reasonMissingColon.getReason());
    }
}
