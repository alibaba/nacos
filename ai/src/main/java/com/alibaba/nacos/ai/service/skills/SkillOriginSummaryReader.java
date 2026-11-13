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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.api.ai.model.skills.SkillOriginSummary;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Map;

/** Safe projection from S3 ext.origin to an ordinary Skill detail. */
public final class SkillOriginSummaryReader {
    
    private SkillOriginSummaryReader() {
    }
    
    /** Project display-only provenance; malformed history must not break ordinary Skill reads. */
    @SuppressWarnings("unchecked")
    public static SkillOriginSummary fromExt(String ext) {
        if (StringUtils.isBlank(ext)) {
            return null;
        }
        try {
            Map<String, Object> root = JacksonUtils.toObj(ext, Map.class);
            if (root == null || !(root.get("origin") instanceof Map)) {
                return null;
            }
            Map<String, Object> raw = (Map<String, Object>) root.get("origin");
            String name = text(raw.get("sourceName"));
            String version = text(raw.get("sourceVersion"));
            if (StringUtils.isBlank(name) || StringUtils.isBlank(version)) {
                return null;
            }
            SkillOriginSummary result = new SkillOriginSummary();
            result.setSourceName(name);
            result.setSourceVersion(version);
            Object rawId = raw.get("hubEntryId");
            String id = rawId instanceof Number ? rawId.toString() : text(rawId);
            if (rawId != null && (id == null || !id.matches("[1-9][0-9]*"))) {
                return null;
            }
            if (id != null && id.matches("[1-9][0-9]*")) {
                try {
                    result.setHubEntryId(Long.parseLong(id));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            result.setImportedAt(text(raw.get("importedAt")));
            result.setMode(result.getHubEntryId() == null ? "DIRECT_COPY" : "HUB_IMPORT");
            return result;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
    
    private static String text(Object value) {
        return value instanceof String ? (String) value : null;
    }
}
