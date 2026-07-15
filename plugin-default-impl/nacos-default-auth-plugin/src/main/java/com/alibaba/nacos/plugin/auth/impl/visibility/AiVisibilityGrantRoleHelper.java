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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Helper for internal AI visibility grant role naming.
 *
 * @author Zhengcy05
 */
final class AiVisibilityGrantRoleHelper {
    
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    
    private AiVisibilityGrantRoleHelper() {
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

    // base64(namespace).base64(resourceType).base64(resourceName)
    static String buildRolePrefix(String namespaceId, String resourceType, String resourceName) {
        // Encode each segment so internal role names stay reversible without leaking delimiter rules
        // into resource names.
        return AuthConstants.AI_VISIBILITY_GRANT_ROLE_PREFIX
            + encode(normalizeNamespaceId(namespaceId))
            + "." + encode(normalizeResourceType(resourceType)) + "." + encode(resourceName) + ".";
    }
    
    static String buildRoleName(String namespaceId, String resourceType, String resourceName,
        String action) {
        return buildRolePrefix(namespaceId, resourceType, resourceName)
            + normalizeStoredAction(action);
    }
    
    static String buildResourceIdentifier(String namespaceId, String resourceType,
        String resourceName) {
        return "@@visibility/" + normalizeNamespaceId(namespaceId) + "/"
            + normalizeResourceType(resourceType) + "/" + resourceName;
    }
    
    static ParsedGrantRole tryParse(String roleName) {
        if (StringUtils.isBlank(roleName)
            || !roleName.startsWith(AuthConstants.AI_VISIBILITY_GRANT_ROLE_PREFIX)) {
            return null;
        }
        String body = roleName.substring(AuthConstants.AI_VISIBILITY_GRANT_ROLE_PREFIX.length());
        String[] parts = body.split("\\.", 4);
        if (parts.length != 4) {
            return null;
        }
        try {
            return new ParsedGrantRole(decode(parts[0]), decode(parts[1]), decode(parts[2]),
                normalizeStoredAction(parts[3]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    
    private static String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }
    
    static final class ParsedGrantRole {
        
        private final String namespaceId;
        
        private final String resourceType;
        
        private final String resourceName;
        
        private final String storedAction;
        
        private ParsedGrantRole(String namespaceId, String resourceType, String resourceName,
            String storedAction) {
            this.namespaceId = namespaceId;
            this.resourceType = resourceType;
            this.resourceName = resourceName;
            this.storedAction = storedAction;
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
        
        String getStoredAction() {
            return storedAction;
        }
    }
}
