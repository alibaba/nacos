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

package com.alibaba.nacos.ai.service.trace;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.ai.AiResourceTraceEvent;

/**
 * AI resource trace service for auditing AI resource version operations.
 *
 * <p>Trace records are emitted as {@link AiResourceTraceEvent}. The default subscriber keeps
 * the existing JSON line file output, designed for ELK/Loki integration.</p>
 *
 * <p>Log format example:</p>
 * <pre>
 * {"timestamp":"2026-03-30T10:15:30Z","operator":"admin","resource_type":"skill",
 *  "resource_id":"my-skill","version":"v1.0","operation":"PUBLISH",
 *  "status":"SUCCESS","ip":"192.168.1.1"}
 * </pre>
 *
 * @author nacos
 * @since 3.2.1
 */
public class AiResourceTraceService {
    
    // ==================== Operation Constants ====================
    
    /**
     * Create a new draft version.
     */
    public static final String OP_CREATE_DRAFT = "CREATE_DRAFT";
    
    /**
     * Update an existing draft version.
     */
    public static final String OP_UPDATE_DRAFT = "UPDATE_DRAFT";
    
    /**
     * Delete a draft version.
     */
    public static final String OP_DELETE_DRAFT = "DELETE_DRAFT";
    
    /**
     * Upload a skill/resource.
     */
    public static final String OP_UPLOAD = "UPLOAD";
    
    /**
     * Submit version for review.
     */
    public static final String OP_SUBMIT_REVIEW = "SUBMIT_REVIEW";
    
    /**
     * Review approved.
     */
    public static final String OP_REVIEW_APPROVED = "REVIEW_APPROVED";
    
    /**
     * Review rejected.
     */
    public static final String OP_REVIEW_REJECTED = "REVIEW_REJECTED";
    
    /**
     * Force skip review (admin operation).
     */
    public static final String OP_REVIEW_FORCE_SKIP = "REVIEW_FORCE_SKIP";
    
    /**
     * Re-edit a reviewed version (transition back to draft).
     */
    public static final String OP_REDRAFT = "REDRAFT";
    
    /**
     * Publish a version to online.
     */
    public static final String OP_PUBLISH = "PUBLISH";
    
    /**
     * Force publish (bypass review).
     */
    public static final String OP_FORCE_PUBLISH = "FORCE_PUBLISH";
    
    /**
     * Take a version offline.
     */
    public static final String OP_OFFLINE_VERSION = "OFFLINE_VERSION";
    
    /**
     * Bring a version back online.
     */
    public static final String OP_ONLINE_VERSION = "ONLINE_VERSION";
    
    /**
     * Delete a version.
     */
    public static final String OP_DELETE_VERSION = "DELETE_VERSION";
    
    /**
     * Delete the entire resource (including all versions).
     */
    public static final String OP_DELETE_RESOURCE = "DELETE_RESOURCE";
    
    /**
     * Set/update label for a version.
     */
    public static final String OP_SET_LABEL = "SET_LABEL";
    
    /**
     * Remove label from a version.
     */
    public static final String OP_REMOVE_LABEL = "REMOVE_LABEL";
    
    /**
     * Update labels.
     */
    public static final String OP_UPDATE_LABELS = "UPDATE_LABELS";
    
    /**
     * Update resource scope.
     */
    public static final String OP_UPDATE_SCOPE = "UPDATE_SCOPE";
    
    /**
     * Update resource description.
     */
    public static final String OP_UPDATE_DESCRIPTION = "UPDATE_DESCRIPTION";
    
    /**
     * Update resource bizTags.
     */
    public static final String OP_UPDATE_BIZ_TAGS = "UPDATE_BIZ_TAGS";
    
    /**
     * Enable resource.
     */
    public static final String OP_ENABLE = "ENABLE";
    
    /**
     * Disable resource.
     */
    public static final String OP_DISABLE = "DISABLE";
    
    /**
     * Search external AI resource import candidates.
     */
    public static final String OP_IMPORT_SEARCH = "IMPORT_SEARCH";
    
    /**
     * Validate selected external AI resource import candidates.
     */
    public static final String OP_IMPORT_VALIDATE = "IMPORT_VALIDATE";
    
    /**
     * Execute external AI resource import.
     */
    public static final String OP_IMPORT_EXECUTE = "IMPORT_EXECUTE";
    
    // ==================== Status Constants ====================
    
    /**
     * Operation succeeded.
     */
    public static final String STATUS_SUCCESS = "SUCCESS";
    
    /**
     * Operation failed.
     */
    public static final String STATUS_FAILURE = "FAILURE";
    
    /**
     * Operation skipped by request policy.
     */
    public static final String STATUS_SKIPPED = "SKIPPED";
    
    // ==================== Logging Methods ====================
    
    /**
     * Log a successful AI resource operation.
     *
     * @param resourceType the type of resource (e.g., "skill", "agentspec", "mcp", "prompt")
     * @param resourceId   the resource identifier (name)
     * @param version      the version being operated on (nullable)
     * @param operation    the operation type (use OP_* constants)
     * @param operator     the operator identity (user id or username)
     * @param clientIp     the client IP address
     */
    public static void logSuccess(String resourceType, String resourceId, String version,
        String operation,
        String operator, String clientIp) {
        log(resourceType, resourceId, version, operation, STATUS_SUCCESS, operator, clientIp, null);
    }
    
    /**
     * Log a successful AI resource operation with extra info.
     *
     * @param resourceType the type of resource (e.g., "skill", "agentspec", "mcp", "prompt")
     * @param resourceId   the resource identifier (name)
     * @param version      the version being operated on (nullable)
     * @param operation    the operation type (use OP_* constants)
     * @param operator     the operator identity (user id or username)
     * @param clientIp     the client IP address
     * @param ext          extra information (nullable)
     */
    public static void logSuccess(String resourceType, String resourceId, String version,
        String operation,
        String operator, String clientIp, String ext) {
        log(resourceType, resourceId, version, operation, STATUS_SUCCESS, operator, clientIp, ext);
    }
    
    /**
     * Log a failed AI resource operation.
     *
     * @param resourceType the type of resource (e.g., "skill", "agentspec", "mcp", "prompt")
     * @param resourceId   the resource identifier (name)
     * @param version      the version being operated on (nullable)
     * @param operation    the operation type (use OP_* constants)
     * @param operator     the operator identity (user id or username)
     * @param clientIp     the client IP address
     * @param errorMsg     the error message
     */
    public static void logFailure(String resourceType, String resourceId, String version,
        String operation,
        String operator, String clientIp, String errorMsg) {
        log(resourceType, resourceId, version, operation, STATUS_FAILURE, operator, clientIp,
            errorMsg);
    }
    
    /**
     * Log an AI resource operation event.
     *
     * @param resourceType the type of resource (e.g., "skill", "agentspec", "mcp", "prompt")
     * @param resourceId   the resource identifier (name)
     * @param version      the version being operated on (nullable)
     * @param operation    the operation type (use OP_* constants)
     * @param status       the operation status (SUCCESS or FAILURE)
     * @param operator     the operator identity (user id or username)
     * @param clientIp     the client IP address
     * @param ext          extra information or error message (nullable)
     */
    public static void log(String resourceType, String resourceId, String version, String operation,
        String status,
        String operator, String clientIp, String ext) {
        NotifyCenter.publishEvent(buildTraceEvent(resourceType, resourceId, version, operation,
            status, operator, clientIp, ext));
    }
    
    static AiResourceTraceEvent buildTraceEvent(String resourceType, String resourceId,
        String version, String operation, String status, String operator, String clientIp,
        String ext) {
        return new AiResourceTraceEvent(System.currentTimeMillis(), operator, resourceType,
            resourceId, version, operation, status, clientIp, ext);
    }
}
