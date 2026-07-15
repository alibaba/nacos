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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.plugin.ai.pipeline.model.Checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts skill-scanner {@code --format markdown} finding titles from stdout for {@link Checkpoint} rows.
 *
 * <p>Looks for an {@code ## Findings} section and collects each {@code ### ...} heading line as one
 * finding title (e.g. {@code ### HIGH — Prompt injection} → {@code HIGH — Prompt injection}).</p>
 *
 * @author qiacheng.cxy
 * @since 3.2.0
 */
final class SkillScannerMarkdownFindingParser {
    
    private static final Pattern NEXT_H2_NOT_H3 = Pattern.compile("(?m)^## [^#]");
    
    private static final String CHECK_PROMPT_INJECTION = "Prompt injection 检查";
    
    private static final String CHECK_DATA_EXFILTRATION = "Data exfiltration 检查";
    
    private static final String CHECK_MALICIOUS_PATTERN = "Malicious code patterns 检查";
    
    private static final String CHECK_PIPELINE_TAINT = "Pipeline taint analysis 检查";
    
    private static final String CHECK_BYTECODE_INTEGRITY = "Bytecode integrity 检查";
    
    private static final String CHECK_LLM_SEMANTIC = "LLM semantic analysis 检查";
    
    private static final String CHECK_META_FILTERING = "Meta-analyzer filtering 检查";
    
    private SkillScannerMarkdownFindingParser() {
    }
    
    /**
     * Builds reject checkpoints: one failed checkpoint per finding heading under {@code ## Findings}.
     * If no headings are found, returns a single fallback checkpoint so callers still get a structured result.
     */
    static List<Checkpoint> buildRejectCheckpoints(String markdown) {
        List<String> titles = extractFindingTitles(markdown);
        if (titles.isEmpty()) {
            return Collections.singletonList(new Checkpoint("HIGH/CRITICAL 风险检测", false));
        }
        List<Checkpoint> list = new ArrayList<>(titles.size());
        for (String title : titles) {
            list.add(new Checkpoint(title, false));
        }
        return list;
    }
    
    /**
     * Builds pass checkpoints when the scanner exits successfully: either a generic pass row, or
     * derived from report text if needed later.
     */
    static List<Checkpoint> buildPassCheckpoints(SkillScannerScanOptions scanOptions) {
        List<Checkpoint> list = new ArrayList<>();
        list.add(new Checkpoint(CHECK_PROMPT_INJECTION, true));
        list.add(new Checkpoint(CHECK_DATA_EXFILTRATION, true));
        list.add(new Checkpoint(CHECK_MALICIOUS_PATTERN, true));
        list.add(new Checkpoint(CHECK_PIPELINE_TAINT, true));
        list.add(new Checkpoint(CHECK_BYTECODE_INTEGRITY, true));
        if (scanOptions != null && scanOptions.isUseLlm()) {
            list.add(new Checkpoint(CHECK_LLM_SEMANTIC, true));
            if (scanOptions.isEnableMeta()) {
                list.add(new Checkpoint(CHECK_META_FILTERING, true));
            }
        }
        return list;
    }
    
    /**
     * Extracts heading text from each {@code ### } line inside the {@code ## Findings} section.
     */
    static List<String> extractFindingTitles(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Collections.emptyList();
        }
        int findingsStart = indexOfIgnoreCase(markdown, "## Findings");
        if (findingsStart < 0) {
            return Collections.emptyList();
        }
        int lineAfterHeading = markdown.indexOf('\n', findingsStart);
        if (lineAfterHeading < 0) {
            return Collections.emptyList();
        }
        int bodyStart = lineAfterHeading + 1;
        int bodyEnd = findNextH2SectionStart(markdown, bodyStart);
        String section =
            bodyEnd < 0 ? markdown.substring(bodyStart) : markdown.substring(bodyStart, bodyEnd);
        return extractH3Titles(section);
    }
    
    private static int findNextH2SectionStart(String markdown, int from) {
        Matcher m = NEXT_H2_NOT_H3.matcher(markdown);
        if (m.find(from)) {
            return m.start();
        }
        return -1;
    }
    
    private static List<String> extractH3Titles(String section) {
        Pattern h3 = Pattern.compile("(?m)^###\\s+(.+)$");
        Matcher m = h3.matcher(section);
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            String t = m.group(1).trim();
            if (!t.isEmpty()) {
                titles.add(t);
            }
        }
        return titles;
    }
    
    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }
}
