/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;

/**
 * Authorization response model from IdP authorization endpoint.
 *
 * @author WangzJi
 */
public class AuthorizationResponse {
    
    /**
     * Whether access is allowed.
     */
    private boolean allowed;
    
    /**
     * Reason for denial (if not allowed).
     */
    private String reason;
    
    /**
     * Error code (if any).
     */
    private String errorCode;
    
    public AuthorizationResponse() {
    }
    
    public AuthorizationResponse(boolean allowed) {
        this.allowed = allowed;
    }
    
    public AuthorizationResponse(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }
    
    /**
     * Create a successful (allowed) response.
     *
     * @return allowed response
     */
    public static AuthorizationResponse allowed() {
        return new AuthorizationResponse(true);
    }
    
    /**
     * Create a denied response with reason.
     *
     * @param reason denial reason
     * @return denied response
     */
    public static AuthorizationResponse denied(String reason) {
        return new AuthorizationResponse(false, reason);
    }
    
    /**
     * Parse JSON response from IdP.
     * Supports common formats:
     * - {"allowed": true}
     * - {"allowed": false, "reason": "..."}
     * - {"result": "PERMIT"} / {"result": "DENY"}
     * - {"decision": "Permit"} / {"decision": "Deny"}
     *
     * @param json JSON string
     * @return AuthorizationResponse
     */
    public static AuthorizationResponse fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return denied("Empty response from IdP");
        }
        
        AuthorizationResponse response = new AuthorizationResponse();
        
        // Check for "allowed" field
        if (json.contains(OidcConstants.JSON_FIELD_ALLOWED)) {
            response.allowed = json.contains("\"allowed\":true")
                || json.contains("\"allowed\": true");
        } else if (json.contains(OidcConstants.JSON_FIELD_RESULT)) {
            // Keycloak format
            response.allowed = json.toLowerCase().contains("\"result\":\"permit\"")
                || json.toLowerCase().contains("\"result\": \"permit\"");
        } else if (json.contains(OidcConstants.JSON_FIELD_DECISION)) {
            // Alternative format
            response.allowed = json.toLowerCase().contains("\"decision\":\"permit\"")
                || json.toLowerCase().contains("\"decision\": \"permit\"");
        }
        
        // Extract reason if present
        response.reason = extractJsonValue(json, "reason");
        if (response.reason == null) {
            response.reason = extractJsonValue(json, "message");
        }
        if (response.reason == null) {
            response.reason = extractJsonValue(json, "error_description");
        }
        
        // Extract error code if present
        response.errorCode = extractJsonValue(json, "error");
        if (response.errorCode == null) {
            response.errorCode = extractJsonValue(json, "errorCode");
        }
        
        return response;
    }
    
    /**
     * Simple JSON value extraction.
     *
     * @param json JSON string
     * @param key  key to extract
     * @return value or null
     */
    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }
        
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) {
            return null;
        }
        
        int valueStart = json.indexOf("\"", colonIndex);
        if (valueStart == -1) {
            return null;
        }
        
        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) {
            return null;
        }
        
        return json.substring(valueStart + 1, valueEnd);
    }
    
    // Getters and Setters
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    @Override
    public String toString() {
        return "AuthorizationResponse{allowed=" + allowed + ", reason='" + reason + "'}";
    }
}
