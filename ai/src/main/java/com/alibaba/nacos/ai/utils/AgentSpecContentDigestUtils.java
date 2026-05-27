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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
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
 * Utility for computing the canonical content MD5 of a published {@link AgentSpec}.
 *
 * <p>The MD5 is computed once at publish time and persisted into the {@code storage.contentMd5}
 * field of the corresponding {@code ai_resource_version} row, so the listener path never
 * recomputes it at runtime.
 *
 * <p>The hash input covers the main content (description + content field) plus all referenced
 * resources. To stay deterministic across publishes, resources are iterated in ascending order
 * of their map keys, and a single {@code 0x00} byte is used as the field separator.
 *
 * <p>Format (all bytes are UTF-8 unless explicitly stated):
 * <pre>
 *   description UTF-8 bytes
 *   0x00
 *   content UTF-8 bytes
 *   0x00
 *   for each resource sorted by map key:
 *       key UTF-8 bytes
 *       0x00
 *       resource.getContent() UTF-8 bytes (empty string for null)
 *       0x00
 * </pre>
 *
 * @author nacos
 * @since 3.2.0
 */
public final class AgentSpecContentDigestUtils {
    
    private static final byte SEPARATOR = 0x00;
    
    private AgentSpecContentDigestUtils() {
    }
    
    /**
     * Compute the canonical content MD5 for an agentspec.
     *
     * @param agentSpec the agentspec object; must not be {@code null}
     * @return lowercase hex MD5 string
     * @throws IllegalArgumentException when {@code agentSpec} is null
     */
    public static String computeContentMd5(AgentSpec agentSpec) {
        if (agentSpec == null) {
            throw new IllegalArgumentException(
                "AgentSpec cannot be null when computing content MD5");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeField(buffer, agentSpec.getDescription());
            buffer.write(SEPARATOR);
            writeField(buffer, agentSpec.getContent());
            buffer.write(SEPARATOR);
            
            Map<String, AgentSpecResource> resources = agentSpec.getResource();
            if (resources != null && !resources.isEmpty()) {
                List<String> sortedKeys = new ArrayList<>(resources.keySet());
                Collections.sort(sortedKeys);
                for (String key : sortedKeys) {
                    buffer.write(key.getBytes(StandardCharsets.UTF_8));
                    buffer.write(SEPARATOR);
                    AgentSpecResource resource = resources.get(key);
                    writeField(buffer,
                        resource != null ? resource.getContent() : null);
                    buffer.write(SEPARATOR);
                }
            }
            return MD5Utils.md5Hex(buffer.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to assemble agentspec content for MD5", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "MD5 algorithm not available in current JVM", e);
        }
    }
    
    private static void writeField(ByteArrayOutputStream buffer, String value)
        throws IOException {
        if (StringUtils.isNotBlank(value)) {
            buffer.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
