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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rule-based search text extractor for Skill markdown.
 *
 * @author nacos
 */
class SkillMarkdownSearchTextExtractor {
    
    private static final int MAX_CHUNKS = 8;
    
    private static final int MAX_CHARS_PER_CHUNK = 1000;
    
    private static final int MIN_SHORT_PARAGRAPH_CHARS = 12;
    
    private static final int MAX_SHORT_PARAGRAPH_CHARS = 500;
    
    private static final String[] FRONT_MATTER_KEYS = {
        "name", "description", "trigger", "triggers", "tag", "tags", "capability",
        "capabilities", "usecase", "usecases", "use_case", "use_cases"
    };
    
    private static final String[] HIGH_VALUE_KEYWORDS = {
        "trigger", "triggers", "use case", "use cases", "usecase", "usecases", "capability",
        "capabilities", "when to use", "example", "examples", "query", "queries", "user asks",
        "触发词", "触发", "适用场景", "使用场景", "能力", "示例", "查询", "用户问题", "用户输入"
    };
    
    List<String> extract(String markdown) {
        if (StringUtils.isBlank(markdown)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String body = addFrontMatter(markdown, result);
        addBody(body, result);
        return result;
    }
    
    private String addFrontMatter(String markdown, List<String> result) {
        FrontMatter frontMatter = parseFrontMatter(markdown);
        for (String line : frontMatter.lines) {
            String clean = cleanMarkdownText(line);
            if (isCandidate(clean) && frontMatterKey(clean)) {
                add(result, clean);
            }
        }
        return frontMatter.body;
    }
    
    private FrontMatter parseFrontMatter(String markdown) {
        List<String> lines = markdown.lines().toList();
        if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
            return new FrontMatter(List.of(), markdown);
        }
        int end = -1;
        for (int i = 1; i < lines.size(); i++) {
            if ("---".equals(lines.get(i).trim())) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return new FrontMatter(List.of(), markdown);
        }
        List<String> frontMatterLines = new ArrayList<>();
        for (int i = 1; i < end; i++) {
            frontMatterLines.add(lines.get(i));
        }
        StringBuilder body = new StringBuilder();
        for (int i = end + 1; i < lines.size(); i++) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(lines.get(i));
        }
        return new FrontMatter(frontMatterLines, body.toString());
    }
    
    private boolean frontMatterKey(String text) {
        int separator = text.indexOf(':');
        if (separator <= 0) {
            return false;
        }
        String key = text.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        for (String expected : FRONT_MATTER_KEYS) {
            if (expected.equals(key)) {
                return true;
            }
        }
        return false;
    }
    
    private void addBody(String markdown, List<String> result) {
        boolean inFence = false;
        boolean highValueSection = false;
        for (String rawLine : markdown.lines().toList()) {
            String line = rawLine.trim();
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inFence = !inFence;
                continue;
            }
            if (inFence || StringUtils.isBlank(line)) {
                continue;
            }
            if (line.startsWith("#")) {
                String heading = cleanMarkdownText(line.replaceFirst("^#{1,6}\\s*", ""));
                highValueSection = highValueText(heading);
                if (isCandidate(heading)) {
                    add(result, heading);
                }
                continue;
            }
            String text = cleanMarkdownText(line.replaceFirst("^[-*+]\\s+", "")
                .replaceFirst("^\\d+[.)]\\s+", ""));
            if (!isCandidate(text)) {
                continue;
            }
            if (highValueSection || highValueText(text) || shortParagraph(text)) {
                add(result, text);
            }
            if (result.size() >= MAX_CHUNKS) {
                return;
            }
        }
    }
    
    private boolean highValueText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : HIGH_VALUE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean shortParagraph(String text) {
        return text.length() >= MIN_SHORT_PARAGRAPH_CHARS
            && text.length() <= MAX_SHORT_PARAGRAPH_CHARS;
    }
    
    private boolean isCandidate(String text) {
        return StringUtils.isNotBlank(text) && !looksLikeCommand(text)
            && !looksLikeTableSeparator(text);
    }
    
    private boolean looksLikeCommand(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("$ ") || lower.startsWith("curl ")
            || lower.startsWith("mvn ") || lower.startsWith("npm ")
            || lower.startsWith("pnpm ") || lower.startsWith("yarn ")
            || lower.startsWith("python ") || lower.startsWith("python3 ");
    }
    
    private boolean looksLikeTableSeparator(String text) {
        return text.matches("^[|\\-: ]+$");
    }
    
    private String cleanMarkdownText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("`", "").replaceAll("\\s+", " ").trim();
    }
    
    private void add(List<String> result, String text) {
        if (result.size() >= MAX_CHUNKS) {
            return;
        }
        String value = limit(text);
        for (String existing : result) {
            if (existing.equalsIgnoreCase(value)) {
                return;
            }
        }
        result.add(value);
    }
    
    private String limit(String text) {
        if (text.length() <= MAX_CHARS_PER_CHUNK) {
            return text;
        }
        return text.substring(0, MAX_CHARS_PER_CHUNK);
    }
    
    private static class FrontMatter {
        
        private final List<String> lines;
        
        private final String body;
        
        private FrontMatter(List<String> lines, String body) {
            this.lines = lines;
            this.body = body;
        }
    }
}
