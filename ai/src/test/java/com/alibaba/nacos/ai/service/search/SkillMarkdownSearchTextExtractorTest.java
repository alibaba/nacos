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

package com.alibaba.nacos.ai.service.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SkillMarkdownSearchTextExtractor}.
 *
 * @author nacos
 */
class SkillMarkdownSearchTextExtractorTest {
    
    @Test
    void extractShouldKeepHighValueSkillMarkdownText() {
        String markdown = "---\n"
            + "name: ai-video-avatar\n"
            + "description: Create AI avatar and talking head videos.\n"
            + "---\n"
            + "# AI Avatar Video\n"
            + "General markdown paragraph for avatar generation.\n"
            + "```bash\ncurl http://example\n```\n"
            + "## Triggers\n"
            + "- ai avatar\n"
            + "- talking head\n"
            + "## Usage\n"
            + "$ npm install\n";
        
        List<String> chunks = new SkillMarkdownSearchTextExtractor().extract(markdown);
        
        assertTrue(chunks.stream().anyMatch(text -> text.contains("talking head")));
        assertTrue(chunks.stream().anyMatch(text -> text.contains("description:")));
        assertFalse(chunks.stream().anyMatch(text -> text.contains("curl")));
        assertFalse(chunks.stream().anyMatch(text -> text.contains("npm install")));
    }
}
