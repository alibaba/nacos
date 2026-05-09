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

package com.alibaba.nacos.ai.pipeline.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutionStatusTest {
    
    @Test
    void testApprovedStatusRequiresAllNodesPassed() {
        PipelineExecution execution = newExecution(PipelineExecutionStatus.APPROVED,
            newNodeResult("parse", true), newNodeResult("scan", true));
        boolean allNodesPassed =
            execution.getPipeline().stream().allMatch(PipelineNodeResult::isPassed);
        assertTrue(allNodesPassed);
        assertEquals(PipelineExecutionStatus.APPROVED, execution.getStatus());
    }
    
    @Test
    void testRejectedStatusWhenAnyNodeFailed() {
        PipelineExecution execution = newExecution(PipelineExecutionStatus.REJECTED,
            newNodeResult("parse", true), newNodeResult("scan", false));
        boolean allNodesPassed =
            execution.getPipeline().stream().allMatch(PipelineNodeResult::isPassed);
        assertTrue(!allNodesPassed);
        assertEquals(PipelineExecutionStatus.REJECTED, execution.getStatus());
    }
    
    @Test
    void testApprovedStatusAlsoHoldsForSinglePassedNode() {
        PipelineExecution execution =
            newExecution(PipelineExecutionStatus.APPROVED, newNodeResult("scan", true));
        boolean allNodesPassed =
            execution.getPipeline().stream().allMatch(PipelineNodeResult::isPassed);
        assertTrue(allNodesPassed);
        assertEquals(PipelineExecutionStatus.APPROVED, execution.getStatus());
    }
    
    private PipelineExecution newExecution(PipelineExecutionStatus status,
        PipelineNodeResult... nodes) {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("exec-1");
        execution.setResourceType("AGENTSPEC");
        execution.setResourceName("demo");
        execution.setNamespaceId("public");
        execution.setVersion("1.0.0");
        execution.setStatus(status);
        execution.setPipeline(
            nodes == null ? Collections.<PipelineNodeResult>emptyList() : Arrays.asList(nodes));
        execution.setCreateTime(1L);
        execution.setUpdateTime(2L);
        return execution;
    }
    
    private PipelineNodeResult newNodeResult(String nodeId, boolean passed) {
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId(nodeId);
        result.setExecutedAt("2026-03-27T12:00:00Z");
        result.setPassed(passed);
        result.setMessage(passed ? "ok" : "failed");
        result.setDurationMs(10L);
        return result;
    }
}
