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

import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility to export a prompt version as a Markdown document.
 *
 * <p>Output layout: a YAML frontmatter with identity and governance fields, followed by a
 * human-readable description section and the raw template body enclosed in a fenced code block
 * so placeholders like {@code {{variable}}} are preserved verbatim.</p>
 *
 * @author nacos
 */
public final class PromptMarkdownBuilder {
    
    private static final String NEW_LINE = "\n";
    
    private static final String YAML_DELIMITER = "---";
    
    private static final String FENCE = "```";
    
    private PromptMarkdownBuilder() {
    }
    
    /**
     * Render a {@link PromptVersionInfo} into a Markdown document string.
     *
     * @param info prompt version detail, must not be {@code null}
     * @return Markdown content
     */
    public static String build(PromptVersionInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("PromptVersionInfo must not be null");
        }
        StringBuilder sb = new StringBuilder(512);
        
        // YAML frontmatter
        sb.append(YAML_DELIMITER).append(NEW_LINE);
        appendYamlScalar(sb, "promptKey", info.getPromptKey());
        appendYamlScalar(sb, "version", info.getVersion());
        appendYamlScalar(sb, "status", info.getStatus());
        appendYamlScalar(sb, "srcUser", info.getSrcUser());
        appendYamlScalar(sb, "md5", info.getMd5());
        if (info.getGmtModified() != null) {
            sb.append("gmtModified: ").append(info.getGmtModified()).append(NEW_LINE);
        }
        appendVariablesYaml(sb, info.getVariables());
        sb.append(YAML_DELIMITER).append(NEW_LINE).append(NEW_LINE);
        
        // Title
        sb.append("# ").append(safe(info.getPromptKey()));
        if (StringUtils.isNotBlank(info.getVersion())) {
            sb.append(" @ ").append(info.getVersion());
        }
        sb.append(NEW_LINE).append(NEW_LINE);
        
        // Commit message
        if (StringUtils.isNotBlank(info.getCommitMsg())) {
            sb.append("> ").append(info.getCommitMsg().replace(NEW_LINE, " ")).append(NEW_LINE)
                .append(NEW_LINE);
        }
        
        // Variables section (human readable)
        if (info.getVariables() != null && !info.getVariables().isEmpty()) {
            sb.append("## Variables").append(NEW_LINE).append(NEW_LINE);
            sb.append("| Name | Default | Description |").append(NEW_LINE);
            sb.append("| --- | --- | --- |").append(NEW_LINE);
            for (PromptVariable v : info.getVariables()) {
                sb.append("| ").append(mdCell(v.getName()))
                    .append(" | ").append(mdCell(v.getDefaultValue()))
                    .append(" | ").append(mdCell(v.getDescription()))
                    .append(" |").append(NEW_LINE);
            }
            sb.append(NEW_LINE);
        }
        
        // Template body
        sb.append("## Template").append(NEW_LINE).append(NEW_LINE);
        sb.append(FENCE).append(NEW_LINE);
        sb.append(info.getTemplate() == null ? "" : info.getTemplate());
        if (info.getTemplate() != null && !info.getTemplate().endsWith(NEW_LINE)) {
            sb.append(NEW_LINE);
        }
        sb.append(FENCE).append(NEW_LINE);
        
        return sb.toString();
    }
    
    /**
     * Build a safe filename for the exported Markdown document.
     *
     * @param promptKey prompt key
     * @param version   version string
     * @return sanitized filename with {@code .md} extension
     */
    public static String buildFilename(String promptKey, String version) {
        String safeKey = sanitize(promptKey, "prompt");
        String safeVersion = sanitize(version, "unknown");
        return safeKey + "_" + safeVersion + ".md";
    }
    
    /**
     * Build a Markdown download {@link ResponseEntity} from a {@link PromptVersionInfo} object.
     *
     * <p>Shared by all controllers that need to export a prompt version as Markdown.</p>
     *
     * @param info prompt version detail, must not be {@code null}
     * @return ResponseEntity containing Markdown bytes with proper headers
     */
    public static ResponseEntity<byte[]> buildMarkdownResponse(PromptVersionInfo info) {
        byte[] markdownBytes = build(info).getBytes(StandardCharsets.UTF_8);
        String filename = buildFilename(info.getPromptKey(), info.getVersion());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/markdown;charset=UTF-8"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + filename);
        return new ResponseEntity<>(markdownBytes, headers, HttpStatus.OK);
    }
    
    private static void appendYamlScalar(StringBuilder sb, String key, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        sb.append(key).append(": ").append(yamlQuote(value)).append(NEW_LINE);
    }
    
    private static void appendVariablesYaml(StringBuilder sb, List<PromptVariable> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        sb.append("variables:").append(NEW_LINE);
        for (PromptVariable v : variables) {
            sb.append("  - name: ").append(yamlQuote(safe(v.getName()))).append(NEW_LINE);
            if (v.getDefaultValue() != null) {
                sb.append("    defaultValue: ").append(yamlQuote(v.getDefaultValue()))
                    .append(NEW_LINE);
            }
            if (StringUtils.isNotBlank(v.getDescription())) {
                sb.append("    description: ").append(yamlQuote(v.getDescription()))
                    .append(NEW_LINE);
            }
        }
    }
    
    private static String yamlQuote(String raw) {
        String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
    
    private static String mdCell(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }
    
    private static String safe(String raw) {
        return raw == null ? "" : raw;
    }
    
    private static String sanitize(String raw, String fallback) {
        if (StringUtils.isBlank(raw)) {
            return fallback;
        }
        String replaced = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        return replaced.isEmpty() ? fallback : replaced;
    }
}
