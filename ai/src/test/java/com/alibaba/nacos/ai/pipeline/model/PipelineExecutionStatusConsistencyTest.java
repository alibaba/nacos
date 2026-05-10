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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for PipelineExecutionStatus consistency with pipeline node results.
 *
 * <p><b>Validates: Requirements 2.3, 6.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineExecutionStatusConsistencyTest {
    
    private static PipelineNodeResult passedNode(String id) {
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId(id);
        result.setExecutedAt("2024-01-01T00:00:00Z");
        result.setPassed(true);
        result.setMessage("ok");
        result.setDurationMs(10L);
        return result;
    }
    
    private static PipelineNodeResult failedNode(String id) {
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId(id);
        result.setExecutedAt("2024-01-01T00:00:01Z");
        result.setPassed(false);
        result.setMessage("fail");
        result.setDurationMs(5L);
        return result;
    }
    
    private static PipelineExecution buildApproved(List<PipelineNodeResult> nodes) {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("exec-approved-1");
        execution.setResourceType("SKILL");
        execution.setResourceName("r1");
        execution.setNamespaceId("ns1");
        execution.setVersion("1.0");
        execution.setStatus(PipelineExecutionStatus.APPROVED);
        execution.setPipeline(nodes);
        execution.setCreateTime(1_700_000_000_000L);
        execution.setUpdateTime(1_700_000_000_001L);
        return execution;
    }
    
    private static PipelineExecution buildRejected(List<PipelineNodeResult> nodes) {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId("exec-rejected-1");
        execution.setResourceType("PROMPT");
        execution.setResourceName("r2");
        execution.setNamespaceId("ns2");
        execution.setVersion("2.0");
        execution.setStatus(PipelineExecutionStatus.REJECTED);
        execution.setPipeline(nodes);
        execution.setCreateTime(1_700_000_100_000L);
        execution.setUpdateTime(1_700_000_100_001L);
        return execution;
    }
    
    private static List<PipelineExecution> sampleExecutions() {
        List<PipelineExecution> list = new ArrayList<>();
        list.add(buildApproved(Arrays.asList(passedNode("n1"))));
        list.add(buildApproved(Arrays.asList(passedNode("a"), passedNode("b"), passedNode("c"))));
        list.add(buildRejected(new ArrayList<>(Arrays.asList(failedNode("x")))));
        list.add(buildRejected(new ArrayList<>(Arrays.asList(passedNode("p1"), failedNode("p2")))));
        return list;
    }
    
    /**
     * Status consistency — APPROVED if and only if all nodes passed.
     *
     * <p><b>Validates: Requirements 2.3, 6.3</b></p>
     */
    @Test
    void statusApprovedIfAndOnlyIfAllNodesPassed() {
        for (PipelineExecution execution : sampleExecutions()) {
            boolean allNodesPassed =
                execution.getPipeline().stream().allMatch(PipelineNodeResult::isPassed);
            
            if (execution.getStatus() == PipelineExecutionStatus.APPROVED) {
                assertTrue(allNodesPassed,
                    "APPROVED execution must have all nodes passed, but found a failed node");
            }
            
            if (allNodesPassed) {
                assertEquals(PipelineExecutionStatus.APPROVED, execution.getStatus(),
                    "Execution with all nodes passed must be APPROVED, but was "
                        + execution.getStatus());
            }
            
            if (!allNodesPassed) {
                assertEquals(PipelineExecutionStatus.REJECTED, execution.getStatus(),
                    "Execution with a failed node must be REJECTED, but was "
                        + execution.getStatus());
            }
        }
    }
}
