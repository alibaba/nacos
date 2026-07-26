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

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility for computing the canonical content MD5 of a published {@link Skill}.
 *
 * <p>The MD5 is computed once at publish time and persisted into the {@code storage.contentMd5}
 * field of the corresponding {@code ai_resource_version} row, so the listener path never recomputes
 * it at runtime.
 *
 * <p>The hash input covers SKILL.md plus all referenced resources (the same byte universe that
 * goes into the downloadable ZIP). To stay deterministic across publishes, resources are iterated
 * in ascending order of their {@link SkillResource#getResourceIdentifier() resource identifier},
 * and a single {@code 0x00} byte is used as the field separator.
 *
 * <p>Format (all bytes are UTF-8 unless explicitly stated):
 * <pre>
 *   skillMd UTF-8 bytes
 *   0x00
 *   for each resource sorted by getResourceIdentifier():
 *       resource.getResourceIdentifier() UTF-8 bytes
 *       0x00
 *       resource.getContent() UTF-8 bytes (empty string for null content)
 *       0x00
 * </pre>
 *
 * @author nacos
 * @since 3.2.0
 */
public final class SkillContentDigestUtils {
    
    private static final byte SEPARATOR = 0x00;
    
    private SkillContentDigestUtils() {
    }
    
    /**
     * Compute the canonical content MD5 for a skill.
     *
     * @param skill the skill object; must not be {@code null}
     * @return lowercase hex MD5 string
     * @throws IllegalArgumentException when {@code skill} is null
     */
    public static String computeContentMd5(Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null when computing content MD5");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            String skillMd = skill.getSkillMd();
            if (skillMd != null) {
                buffer.write(skillMd.getBytes(StandardCharsets.UTF_8));
            }
            buffer.write(SEPARATOR);
            
            Map<String, SkillResource> resources = skill.getResource();
            if (resources != null && !resources.isEmpty()) {
                List<SkillResource> sorted = new ArrayList<>(resources.values());
                sorted.removeIf(r -> r == null || StringUtils.isBlank(r.getName()));
                Collections.sort(sorted, (a, b) -> {
                    String ka = safeIdentifier(a);
                    String kb = safeIdentifier(b);
                    return ka.compareTo(kb);
                });
                for (SkillResource resource : sorted) {
                    buffer.write(safeIdentifier(resource).getBytes(StandardCharsets.UTF_8));
                    buffer.write(SEPARATOR);
                    String content = resource.getContent();
                    if (content != null) {
                        buffer.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                    buffer.write(SEPARATOR);
                }
            }
            return MD5Utils.md5Hex(buffer.toByteArray());
        } catch (IOException e) {
            // ByteArrayOutputStream never throws; keep checked-style for completeness.
            throw new IllegalStateException("Failed to assemble skill content for MD5", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available in current JVM", e);
        }
    }
    
    private static String safeIdentifier(SkillResource resource) {
        String id = resource.getResourceIdentifier();
        return id == null ? "" : id;
    }
}
