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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillScannerMarkdownFindingParser} unit tests.
 *
 * @author qiacheng.cxy
 */
class SkillScannerMarkdownFindingParserTest {
    
    @Test
    void extractFindingTitlesEmptyInput() {
        assertTrue(SkillScannerMarkdownFindingParser.extractFindingTitles(null).isEmpty());
        assertTrue(SkillScannerMarkdownFindingParser.extractFindingTitles("").isEmpty());
    }
    
    @Test
    void extractFindingTitlesWithoutSectionBody() {
        assertTrue(SkillScannerMarkdownFindingParser.extractFindingTitles("## Findings").isEmpty());
    }
    
    @Test
    void extractFindingTitlesSingleHeading() {
        String md = ""
            + "## Summary\n\nok\n"
            + "## Findings\n\n"
            + "### HIGH — Prompt injection\n\n"
            + "detail line\n";
        List<String> titles = SkillScannerMarkdownFindingParser.extractFindingTitles(md);
        assertEquals(1, titles.size());
        assertEquals("HIGH — Prompt injection", titles.get(0));
    }
    
    @Test
    void extractFindingTitlesMultipleHeadings() {
        String md = ""
            + "## Findings\n\n"
            + "### HIGH — Rule A\n\n"
            + "x\n"
            + "### MEDIUM — Rule B\n\n"
            + "y\n";
        List<String> titles = SkillScannerMarkdownFindingParser.extractFindingTitles(md);
        assertEquals(2, titles.size());
        assertEquals("HIGH — Rule A", titles.get(0));
        assertEquals("MEDIUM — Rule B", titles.get(1));
    }
    
    @Test
    void extractFindingTitlesStopsAtNextH2() {
        String md = ""
            + "## Findings\n\n"
            + "### HIGH — Only this\n\n"
            + "## Other section\n\n"
            + "### should not capture\n";
        List<String> titles = SkillScannerMarkdownFindingParser.extractFindingTitles(md);
        assertEquals(1, titles.size());
        assertEquals("HIGH — Only this", titles.get(0));
    }
    
    @Test
    void extractFindingTitlesCaseInsensitiveSection() {
        String md = "## FINDINGS\n\n### CRITICAL — X\n";
        List<String> titles = SkillScannerMarkdownFindingParser.extractFindingTitles(md);
        assertEquals(1, titles.size());
        assertEquals("CRITICAL — X", titles.get(0));
    }
    
    @Test
    void buildRejectCheckpointsUsesParsedTitles() {
        String md = "## Findings\n\n### HIGH — A\n\n### HIGH — B\n";
        List<Checkpoint> cps = SkillScannerMarkdownFindingParser.buildRejectCheckpoints(md);
        assertEquals(2, cps.size());
        assertEquals("HIGH — A", cps.get(0).getTitle());
        assertFalse(cps.get(0).getPassed());
        assertEquals("HIGH — B", cps.get(1).getTitle());
        assertFalse(cps.get(1).getPassed());
    }
    
    @Test
    void buildRejectCheckpointsFallbackWhenNoFindings() {
        List<Checkpoint> cps =
            SkillScannerMarkdownFindingParser.buildRejectCheckpoints("no structured report");
        assertEquals(1, cps.size());
        assertEquals("HIGH/CRITICAL 风险检测", cps.get(0).getTitle());
        assertFalse(cps.get(0).getPassed());
    }
    
    @Test
    void buildPassCheckpointsBaseChecks() {
        List<Checkpoint> cps =
            SkillScannerMarkdownFindingParser.buildPassCheckpoints(SkillScannerScanOptions.none());
        assertEquals(5, cps.size());
        assertEquals("Prompt injection 检查", cps.get(0).getTitle());
        assertTrue(cps.get(0).getPassed());
        assertEquals("Data exfiltration 检查", cps.get(1).getTitle());
        assertTrue(cps.get(1).getPassed());
    }
    
    @Test
    void buildPassCheckpointsIncludesLlmAndMetaWhenEnabled() {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty(SkillScannerScanOptions.PROP_USE_LLM, "true");
        properties.setProperty(SkillScannerScanOptions.PROP_ENABLE_META, "true");
        List<Checkpoint> cps = SkillScannerMarkdownFindingParser.buildPassCheckpoints(
            SkillScannerScanOptions.fromProperties(properties));
        assertEquals(7, cps.size());
        assertEquals("LLM semantic analysis 检查", cps.get(5).getTitle());
        assertTrue(cps.get(5).getPassed());
        assertEquals("Meta-analyzer filtering 检查", cps.get(6).getTitle());
        assertTrue(cps.get(6).getPassed());
    }
}
