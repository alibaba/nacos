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

package com.alibaba.nacos.api.ai.model.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineModelTest {
    
    @Test
    void testPipelineExecutionAccessors() {
        List<PipelineNodeResult> pipeline = Collections.singletonList(newNodeResult("node-a"));
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("execution-id");
        execution.setResourceType("SKILL");
        execution.setResourceName("demo");
        execution.setNamespaceId("public");
        execution.setVersion("1.0.0");
        execution.setStatus(PipelineExecutionStatus.APPROVED);
        execution.setPipeline(pipeline);
        execution.setCreateTime(1L);
        execution.setUpdateTime(2L);
        
        assertEquals("execution-id", execution.getExecutionId());
        assertEquals("SKILL", execution.getResourceType());
        assertEquals("demo", execution.getResourceName());
        assertEquals("public", execution.getNamespaceId());
        assertEquals("1.0.0", execution.getVersion());
        assertEquals(PipelineExecutionStatus.APPROVED, execution.getStatus());
        assertEquals(pipeline, execution.getPipeline());
        assertEquals(1L, execution.getCreateTime());
        assertEquals(2L, execution.getUpdateTime());
    }
    
    @Test
    void testPipelineExecutionResultAccessors() {
        List<PipelineNodeResult> pipeline = Collections.singletonList(newNodeResult("node-b"));
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId("execution-id");
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(pipeline);
        
        assertEquals("execution-id", result.getExecutionId());
        assertEquals(PipelineExecutionStatus.REJECTED, result.getStatus());
        assertEquals(pipeline, result.getPipeline());
    }
    
    @Test
    void testPipelineNodeResultAccessors() {
        List<Checkpoint> checkpoints = Collections.singletonList(new Checkpoint("format", true));
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId("node-id");
        result.setExecutedAt("2026-06-24T00:00:00Z");
        result.setPassed(true);
        result.setMessage("ok");
        result.setMessageType("markdown");
        result.setCheckpoints(checkpoints);
        result.setDurationMs(10L);
        
        assertEquals("node-id", result.getNodeId());
        assertEquals("2026-06-24T00:00:00Z", result.getExecutedAt());
        assertTrue(result.isPassed());
        assertEquals("ok", result.getMessage());
        assertEquals("markdown", result.getMessageType());
        assertEquals(checkpoints, result.getCheckpoints());
        assertEquals(10L, result.getDurationMs());
    }
    
    @Test
    void testPipelineNodeResultEqualsAndHashCode() {
        PipelineNodeResult result = newNodeResult("node-id");
        PipelineNodeResult same = newNodeResult("node-id");
        PipelineNodeResult different = newNodeResult("another-node");
        
        assertEquals(result, result);
        assertEquals(result, same);
        assertEquals(result.hashCode(), same.hashCode());
        assertNotEquals(result, different);
        assertNotEquals(result, null);
        assertNotEquals(result, "node-id");
    }
    
    @Test
    void testCheckpointAccessorsAndConstructors() {
        Checkpoint defaultCheckpoint = new Checkpoint();
        defaultCheckpoint.setTitle("format");
        defaultCheckpoint.setPassed(true);
        
        Checkpoint constructed = new Checkpoint("format", true);
        
        assertEquals("format", defaultCheckpoint.getTitle());
        assertTrue(defaultCheckpoint.getPassed());
        assertEquals(defaultCheckpoint, constructed);
        assertEquals(defaultCheckpoint.hashCode(), constructed.hashCode());
    }
    
    @Test
    void testCheckpointEqualsAndHashCode() {
        Checkpoint checkpoint = new Checkpoint("format", true);
        Checkpoint same = new Checkpoint("format", true);
        Checkpoint differentTitle = new Checkpoint("security", true);
        Checkpoint differentPassed = new Checkpoint("format", false);
        
        assertEquals(checkpoint, checkpoint);
        assertEquals(checkpoint, same);
        assertEquals(checkpoint.hashCode(), same.hashCode());
        assertNotEquals(checkpoint, differentTitle);
        assertNotEquals(checkpoint, differentPassed);
        assertNotEquals(checkpoint, null);
        assertNotEquals(checkpoint, "format");
        assertFalse(differentPassed.getPassed());
    }
    
    @Test
    void testPipelineExecutionStatusValues() {
        assertEquals(PipelineExecutionStatus.IN_PROGRESS,
            PipelineExecutionStatus.valueOf("IN_PROGRESS"));
        assertEquals(PipelineExecutionStatus.APPROVED,
            PipelineExecutionStatus.valueOf("APPROVED"));
        assertEquals(PipelineExecutionStatus.REJECTED,
            PipelineExecutionStatus.valueOf("REJECTED"));
        assertEquals(3, PipelineExecutionStatus.values().length);
    }
    
    private PipelineNodeResult newNodeResult(String nodeId) {
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId(nodeId);
        result.setExecutedAt("2026-06-24T00:00:00Z");
        result.setPassed(true);
        result.setMessage("ok");
        result.setMessageType("text");
        result.setCheckpoints(Collections.singletonList(new Checkpoint("format", true)));
        result.setDurationMs(10L);
        return result;
    }
}
