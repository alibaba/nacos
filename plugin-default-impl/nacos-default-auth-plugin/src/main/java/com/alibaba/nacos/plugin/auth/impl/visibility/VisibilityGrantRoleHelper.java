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

package com.alibaba.nacos.plugin.auth.impl.visibility;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Helper for internal visibility grant persistence keys.
 *
 * @author Zhengcy05
 */
final class VisibilityGrantRoleHelper {
    
    private static final String RESOURCE_IDENTIFIER_PREFIX = "@@visibility/";
    
    private static final String USER_ROLE_MARKER = "u.";
    
    private static final int USER_ROLE_HASH_HEX_LENGTH = 32;
    
    private VisibilityGrantRoleHelper() {
    }
    
    static String normalizeNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId) ? Constants.DEFAULT_NAMESPACE_ID : namespaceId;
    }
    
    static String normalizeResourceType(String resourceType) {
        return StringUtils.isBlank(resourceType) ? resourceType
            : resourceType.trim().toLowerCase(Locale.ROOT);
    }
    
    static String normalizeStoredAction(String action) {
        if (StringUtils.isBlank(action)) {
            throw new IllegalArgumentException("action is blank");
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        if ("r".equals(normalized)) {
            return "r";
        }
        if ("w".equals(normalized) || "rw".equals(normalized)) {
            return "rw";
        }
        throw new IllegalArgumentException("unsupported action: " + action);
    }
    
    static boolean matchesRequestedAction(String storedAction, String requestedAction) {
        String normalizedRequested = normalizeStoredAction(requestedAction);
        if ("rw".equals(normalizedRequested)) {
            return "rw".equals(storedAction);
        }
        return "r".equals(storedAction) || "rw".equals(storedAction);
    }
    
    static String buildUserRoleName(String username) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username is blank");
        }
        // Use a deterministic short SHA-256 prefix so internal role names stay within
        // the existing roles.role varchar(50) limit and do not expose user names.
        return buildUserRoleNamePrefix() + sha256LowerHex(username).substring(0,
            USER_ROLE_HASH_HEX_LENGTH);
    }
    
    static String buildUserRoleNamePrefix() {
        return AuthConstants.VISIBILITY_GRANT_ROLE_PREFIX + USER_ROLE_MARKER;
    }
    
    static boolean isUserGrantRole(String roleName) {
        return StringUtils.isNotBlank(roleName) && roleName.startsWith(buildUserRoleNamePrefix());
    }
    
    static String buildResourceIdentifier(String namespaceId, String resourceType,
        String resourceName) {
        return RESOURCE_IDENTIFIER_PREFIX + normalizeNamespaceId(namespaceId) + "/"
            + normalizeResourceType(resourceType) + "/" + resourceName;
    }
    
    static ParsedGrantResource tryParseResourceIdentifier(String resourceIdentifier) {
        if (StringUtils.isBlank(resourceIdentifier)
            || !resourceIdentifier.startsWith(RESOURCE_IDENTIFIER_PREFIX)) {
            return null;
        }
        String body = resourceIdentifier.substring(RESOURCE_IDENTIFIER_PREFIX.length());
        String[] parts = body.split("/", 3);
        if (parts.length != 3 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])
            || StringUtils.isBlank(parts[2])) {
            return null;
        }
        return new ParsedGrantResource(parts[0], parts[1], parts[2]);
    }
    
    private static String sha256LowerHex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte each : bytes) {
                result.append(String.format("%02x", each));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
    
    static final class ParsedGrantResource {
        
        private final String namespaceId;
        
        private final String resourceType;
        
        private final String resourceName;
        
        private ParsedGrantResource(String namespaceId, String resourceType,
            String resourceName) {
            this.namespaceId = namespaceId;
            this.resourceType = resourceType;
            this.resourceName = resourceName;
        }
        
        String getNamespaceId() {
            return namespaceId;
        }
        
        String getResourceType() {
            return resourceType;
        }
        
        String getResourceName() {
            return resourceName;
        }
    }
}
