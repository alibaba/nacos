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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Durable import reservation shared by all AI resource types. */
public final class AiResourceImportUtils {
    
    private AiResourceImportUtils() {
    }
    
    /** Keep recovery coordinates in the first reservation, before any external writes. */
    public static String pendingExt(String extJson, Map<String, Object> recovery) {
        if (recovery == null || recovery.isEmpty()) {
            throw new IllegalArgumentException("Import recovery information is required");
        }
        Map<String, Object> ext = new LinkedHashMap<>(JacksonUtils.toObj(extJson, Map.class));
        ext.put("importPending", true);
        ext.put("importRecovery", recovery);
        return JacksonUtils.toJson(ext);
    }
    
    /** Pending imports remain unwritable even when visibility/authentication is disabled. */
    public static boolean isPending(AiResource resource) {
        if (resource == null || StringUtils.isBlank(resource.getExt())) {
            return false;
        }
        Map<?, ?> ext = JacksonUtils.toObj(resource.getExt(), Map.class);
        if (ext == null) {
            return false;
        }
        return Boolean.TRUE.equals(ext.get("importPending"));
    }
    
    /** Reject ordinary mutations until the import completion CAS has opened the draft. */
    public static void checkNotPending(AiResource resource) throws NacosException {
        if (isPending(resource)) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Resource import is pending or awaiting cleanup: " + resource.getName());
        }
    }
    
    /** Validate the paired server-owned reservation fields in typed metadata. */
    public static void validateState(Object pending, Object recovery) {
        if (pending == null && recovery == null) {
            return;
        }
        if (!Boolean.TRUE.equals(pending) || !(recovery instanceof Map)
            || ((Map<?, ?>) recovery).isEmpty()) {
            throw new IllegalArgumentException("Invalid import reservation metadata");
        }
    }
}
