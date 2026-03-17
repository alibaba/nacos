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

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based test for PipelineExecutionStatus consistency with pipeline node results.
 *
 * <p><b>Validates: Requirements 2.3, 6.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineExecutionStatusPropertyTest {
    
    /**
     * Property 4: Status consistency — APPROVED if and only if all nodes passed.
     *
     * <p>For any completed PipelineExecution, status == APPROVED if and only if
     * all nodes in the pipeline list have passed == true. Conversely, if any node
     * has passed == false, the status must be REJECTED.</p>
     *
     * <p><b>Validates: Requirements 2.3, 6.3</b></p>
     */
    @Property
    void statusApprovedIfAndOnlyIfAllNodesPassed(@ForAll("completedPipelineExecution") PipelineExecution execution) {
        boolean allNodesPassed = execution.getPipeline().stream().allMatch(PipelineNodeResult::isPassed);
        
        if (execution.getStatus() == PipelineExecutionStatus.APPROVED) {
            assertTrue(allNodesPassed,
                    "APPROVED execution must have all nodes passed, but found a failed node");
        }
        
        if (allNodesPassed) {
            assertEquals(PipelineExecutionStatus.APPROVED, execution.getStatus(),
                    "Execution with all nodes passed must be APPROVED, but was " + execution.getStatus());
        }
        
        if (!allNodesPassed) {
            assertEquals(PipelineExecutionStatus.REJECTED, execution.getStatus(),
                    "Execution with a failed node must be REJECTED, but was " + execution.getStatus());
        }
    }
    
    @Provide
    Arbitrary<PipelineExecution> completedPipelineExecution() {
        return Arbitraries.oneOf(approvedExecution(), rejectedExecution());
    }
    
    private Arbitrary<PipelineExecution> approvedExecution() {
        Arbitrary<List<PipelineNodeResult>> allPassedNodes = allPassedNodeResult()
                .list().ofMinSize(1).ofMaxSize(10);
        
        return Combinators.combine(executionId(), resourceType(), resourceName(), namespaceId(), version(),
                        allPassedNodes, timestamp(), timestamp())
                .as((execId, resType, resName, nsId, ver, nodes, createTime, updateTime) -> {
                    PipelineExecution execution = new PipelineExecution();
                    execution.setExecutionId(execId);
                    execution.setResourceType(resType);
                    execution.setResourceName(resName);
                    execution.setNamespaceId(nsId);
                    execution.setVersion(ver);
                    execution.setStatus(PipelineExecutionStatus.APPROVED);
                    execution.setPipeline(nodes);
                    execution.setCreateTime(createTime);
                    execution.setUpdateTime(updateTime);
                    return execution;
                });
    }
    
    private Arbitrary<PipelineExecution> rejectedExecution() {
        Arbitrary<List<PipelineNodeResult>> nodesWithAtLeastOneFailed = mixedNodeResults();
        
        return Combinators.combine(executionId(), resourceType(), resourceName(), namespaceId(), version(),
                        nodesWithAtLeastOneFailed, timestamp(), timestamp())
                .as((execId, resType, resName, nsId, ver, nodes, createTime, updateTime) -> {
                    PipelineExecution execution = new PipelineExecution();
                    execution.setExecutionId(execId);
                    execution.setResourceType(resType);
                    execution.setResourceName(resName);
                    execution.setNamespaceId(nsId);
                    execution.setVersion(ver);
                    execution.setStatus(PipelineExecutionStatus.REJECTED);
                    execution.setPipeline(nodes);
                    execution.setCreateTime(createTime);
                    execution.setUpdateTime(updateTime);
                    return execution;
                });
    }
    
    /**
     * Generates a list of nodes where at least one node has passed=false.
     * Passed nodes come first, followed by exactly one failed node (matching the pipeline
     * fail-fast semantics where no nodes execute after a failure).
     */
    private Arbitrary<List<PipelineNodeResult>> mixedNodeResults() {
        Arbitrary<List<PipelineNodeResult>> passedPrefix = allPassedNodeResult()
                .list().ofMinSize(0).ofMaxSize(5);
        Arbitrary<PipelineNodeResult> failedNode = failedNodeResult();
        
        return Combinators.combine(passedPrefix, failedNode).as((prefix, failed) -> {
            prefix.add(failed);
            return prefix;
        });
    }
    
    private Arbitrary<PipelineNodeResult> allPassedNodeResult() {
        return Combinators.combine(nodeId(), executedAt(), message(), duration())
                .as((nId, execAt, msg, dur) -> {
                    PipelineNodeResult result = new PipelineNodeResult();
                    result.setNodeId(nId);
                    result.setExecutedAt(execAt);
                    result.setPassed(true);
                    result.setMessage(msg);
                    result.setDurationMs(dur);
                    return result;
                });
    }
    
    private Arbitrary<PipelineNodeResult> failedNodeResult() {
        return Combinators.combine(nodeId(), executedAt(), message(), duration())
                .as((nId, execAt, msg, dur) -> {
                    PipelineNodeResult result = new PipelineNodeResult();
                    result.setNodeId(nId);
                    result.setExecutedAt(execAt);
                    result.setPassed(false);
                    result.setMessage(msg);
                    result.setDurationMs(dur);
                    return result;
                });
    }
    
    private Arbitrary<String> executionId() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(36);
    }
    
    private Arbitrary<String> resourceType() {
        return Arbitraries.of("SKILL", "PROMPT", "CONFIG");
    }
    
    private Arbitrary<String> resourceName() {
        return Arbitraries.strings().alpha().numeric().withChars('-', '_').ofMinLength(1).ofMaxLength(30);
    }
    
    private Arbitrary<String> namespaceId() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
    }
    
    private Arbitrary<String> version() {
        return Arbitraries.strings().alpha().numeric().withChars('.').ofMinLength(1).ofMaxLength(10);
    }
    
    private Arbitrary<String> nodeId() {
        return Arbitraries.strings().alpha().numeric().withChars('-').ofMinLength(1).ofMaxLength(20);
    }
    
    private Arbitrary<String> executedAt() {
        return Arbitraries.strings().alpha().numeric().withChars('-', ':', 'T', 'Z')
                .ofMinLength(10).ofMaxLength(25);
    }
    
    private Arbitrary<String> message() {
        return Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                .ofMinLength(0).ofMaxLength(50);
    }
    
    private Arbitrary<Long> duration() {
        return Arbitraries.longs().between(0, 1_000_000L);
    }
    
    private Arbitrary<Long> timestamp() {
        return Arbitraries.longs().between(0, System.currentTimeMillis());
    }
}
