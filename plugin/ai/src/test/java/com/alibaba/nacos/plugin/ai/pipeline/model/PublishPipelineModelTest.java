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

package com.alibaba.nacos.plugin.ai.pipeline.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishPipelineModelTest {
    
    @Test
    void testPublishPipelineContextAccessors() {
        PublishPipelineContext context = new PublishPipelineContext();
        
        context.setResourceType(PublishPipelineResourceType.PROMPT);
        context.setResourceName("prompt");
        context.setNamespaceId("namespace");
        context.setVersion("v1");
        
        assertEquals(PublishPipelineResourceType.PROMPT, context.getResourceType());
        assertEquals("prompt", context.getResourceName());
        assertEquals("namespace", context.getNamespaceId());
        assertEquals("v1", context.getVersion());
    }
    
    @Test
    void testResourceFilesPipelineContextLoadsFilesLazilyOnce() {
        ResourceFileContent file = new ResourceFileContent("SKILL.md", "content");
        AtomicInteger loadCount = new AtomicInteger();
        ResourceFilesPipelineContext context = new ResourceFilesPipelineContext();
        context.setFilesLoader(() -> {
            loadCount.incrementAndGet();
            return Collections.singletonList(file);
        });
        
        assertSame(context.getFiles(), context.getFiles());
        assertEquals(1, loadCount.get());
        assertEquals("SKILL.md", context.getFiles().get(0).getFilePath());
        assertEquals("content", context.getFiles().get(0).getContent());
        assertEquals("ResourceFileContent{filePath='SKILL.md'}", file.toString());
    }
    
    @Test
    void testResourceFilesPipelineContextUsesExplicitFiles() {
        List<ResourceFileContent> files = Collections.singletonList(new ResourceFileContent());
        ResourceFilesPipelineContext context = new ResourceFilesPipelineContext();
        
        context.setFiles(files);
        
        assertSame(files, context.getFiles());
        assertNull(context.getFilesLoader());
    }
    
    @Test
    void testTypedPipelineContextsSetResourceType() {
        assertEquals(PublishPipelineResourceType.SKILL,
            new SkillPipelineContext().getResourceType());
        assertEquals(PublishPipelineResourceType.AGENTSPEC,
            new AgentSpecPipelineContext().getResourceType());
    }
    
    @Test
    void testPublishPipelineResultFactoriesAndAccessors() {
        List<Checkpoint> checkpoints = Arrays.asList(new Checkpoint("format", true),
            new Checkpoint("security", false));
        
        PublishPipelineResult passed =
            PublishPipelineResult.pass("ok", PublishPipelineMessageType.MARKDOWN, checkpoints);
        PublishPipelineResult rejected = PublishPipelineResult.reject("bad", null, checkpoints);
        PublishPipelineResult constructed = new PublishPipelineResult(true, "created");
        PublishPipelineResult simplePassed = PublishPipelineResult.pass("simple-ok");
        PublishPipelineResult simpleRejected = PublishPipelineResult.reject("simple-bad");
        
        assertTrue(passed.isPassed());
        assertEquals("ok", passed.getMessage());
        assertEquals(PublishPipelineMessageType.MARKDOWN, passed.getType());
        assertSame(checkpoints, passed.getCheckpoints());
        assertTrue(passed.toString().contains("passed=true"));
        assertFalse(rejected.isPassed());
        assertEquals(PublishPipelineMessageType.TEXT, rejected.getType());
        assertEquals(PublishPipelineMessageType.TEXT, constructed.getType());
        assertTrue(simplePassed.isPassed());
        assertFalse(simpleRejected.isPassed());
        
        constructed.setPassed(false);
        constructed.setMessage("updated");
        constructed.setType(PublishPipelineMessageType.JSON);
        constructed.setCheckpoints(checkpoints);
        assertFalse(constructed.isPassed());
        assertEquals("updated", constructed.getMessage());
        assertEquals(PublishPipelineMessageType.JSON, constructed.getType());
        assertSame(checkpoints, constructed.getCheckpoints());
    }
    
    @Test
    void testPublishPipelineMessageTypeFromCode() {
        assertEquals(PublishPipelineMessageType.TEXT, PublishPipelineMessageType.fromCode("TEXT"));
        assertEquals(PublishPipelineMessageType.JSON, PublishPipelineMessageType.fromCode("json"));
        assertEquals(PublishPipelineMessageType.MARKDOWN,
            PublishPipelineMessageType.fromCode("markdown"));
        assertEquals(PublishPipelineMessageType.HTML, PublishPipelineMessageType.fromCode("html"));
        assertNull(PublishPipelineMessageType.fromCode(null));
        assertNull(PublishPipelineMessageType.fromCode(""));
        assertNull(PublishPipelineMessageType.fromCode("unknown"));
        assertEquals("markdown", PublishPipelineMessageType.MARKDOWN.getCode());
    }
    
    @Test
    void testResourceFileContentAccessors() {
        ResourceFileContent file = new ResourceFileContent();
        
        file.setFilePath("README.md");
        file.setContent("content");
        
        assertEquals("README.md", file.getFilePath());
        assertEquals("content", file.getContent());
    }
    
    @Test
    void testCheckpointEqualsAndHashCode() {
        Checkpoint checkpoint = new Checkpoint("format", true);
        Checkpoint same = new Checkpoint();
        same.setTitle("format");
        same.setPassed(true);
        
        assertEquals(checkpoint, checkpoint);
        assertEquals(checkpoint, same);
        assertEquals(checkpoint.hashCode(), same.hashCode());
        assertNotEquals(checkpoint, new Checkpoint("format", false));
        assertNotEquals(checkpoint, null);
        assertNotEquals(checkpoint, "format");
        assertEquals("format", same.getTitle());
        assertTrue(same.getPassed());
    }
}
