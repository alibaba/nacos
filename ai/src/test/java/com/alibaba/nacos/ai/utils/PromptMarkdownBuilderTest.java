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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptMarkdownBuilderTest {
    
    @Test
    void buildShouldContainAllFrontmatterAndTemplate() {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey("greeting");
        info.setVersion("1.0.0");
        info.setStatus("online");
        info.setSrcUser("alice");
        info.setMd5("abc123");
        info.setGmtModified(1714435200000L);
        info.setCommitMsg("initial version");
        info.setTemplate("Hello {{name}}, welcome to {{place}}!");
        info.setVariables(Arrays.asList(
            new PromptVariable("name", "guest", "user name"),
            new PromptVariable("place", null, "location")));
        
        String md = PromptMarkdownBuilder.build(info);
        
        assertTrue(md.startsWith("---\n"), "should start with YAML frontmatter");
        assertTrue(md.contains("promptKey: \"greeting\""));
        assertTrue(md.contains("version: \"1.0.0\""));
        assertTrue(md.contains("status: \"online\""));
        assertTrue(md.contains("srcUser: \"alice\""));
        assertTrue(md.contains("md5: \"abc123\""));
        assertTrue(md.contains("gmtModified: 1714435200000"));
        assertTrue(md.contains("variables:"));
        assertTrue(md.contains("- name: \"name\""));
        assertTrue(md.contains("defaultValue: \"guest\""));
        assertTrue(md.contains("# greeting @ 1.0.0"));
        assertTrue(md.contains("> initial version"));
        assertTrue(md.contains("## Variables"));
        assertTrue(md.contains("| name | guest | user name |"));
        assertTrue(md.contains("## Template"));
        assertTrue(md.contains("Hello {{name}}, welcome to {{place}}!"));
    }
    
    @Test
    void buildShouldHandleMinimalInfo() {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey("p");
        info.setVersion("0.0.1");
        info.setTemplate("raw");
        
        String md = PromptMarkdownBuilder.build(info);
        
        assertTrue(md.contains("# p @ 0.0.1"));
        assertTrue(md.contains("```\nraw\n```"));
        // no Variables section when variables are null
        assertTrue(!md.contains("## Variables"));
    }
    
    @Test
    void buildShouldEscapeMarkdownTableCells() {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey("p");
        info.setVersion("1.0.0");
        info.setTemplate("body");
        info.setVariables(Arrays.asList(
            new PromptVariable("col", "a|b", "line1\nline2")));
        
        String md = PromptMarkdownBuilder.build(info);
        
        assertTrue(md.contains("| col | a\\|b | line1 line2 |"),
            "pipe should be escaped and newline collapsed to space");
    }
    
    @Test
    void buildShouldThrowOnNullInput() {
        assertThrows(IllegalArgumentException.class, () -> PromptMarkdownBuilder.build(null));
    }
    
    @Test
    void buildFilenameShouldSanitizeUnsafeChars() {
        assertEquals("my_prompt_1.0.0.md",
            PromptMarkdownBuilder.buildFilename("my/prompt", "1.0.0"));
        assertEquals("a_b__.md", PromptMarkdownBuilder.buildFilename("a b", "?"));
    }
    
    @Test
    void buildFilenameShouldFallbackWhenBlank() {
        assertEquals("prompt_unknown.md", PromptMarkdownBuilder.buildFilename(null, null));
        assertEquals("prompt_unknown.md", PromptMarkdownBuilder.buildFilename("", "  "));
    }
    
    @Test
    void buildMarkdownResponseShouldAttachProperHeadersAndBody() {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey("greeting");
        info.setVersion("1.0.0");
        info.setTemplate("Hello world");
        
        ResponseEntity<byte[]> response = PromptMarkdownBuilder.buildMarkdownResponse(info);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentType().toString().toLowerCase()
            .contains("text/markdown"));
        
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment;"));
        assertTrue(disposition.contains("greeting_1.0.0.md"));
        
        byte[] body = response.getBody();
        assertNotNull(body);
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("Hello world"));
        assertTrue(bodyStr.contains("# greeting @ 1.0.0"));
    }
}
