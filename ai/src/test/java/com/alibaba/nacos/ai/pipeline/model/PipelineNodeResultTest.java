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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.Checkpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineNodeResultTest {
    
    @Test
    void testJsonSerializationRoundTripForRepresentativeCases() {
        List<List<PipelineNodeResult>> cases = Arrays.asList(
            Collections.<PipelineNodeResult>emptyList(),
            Collections.singletonList(newNodeResult("node-1", true, "ok", "text",
                Collections.<Checkpoint>emptyList(), 10L)),
            Arrays.asList(
                newNodeResult("node-2", false, "rejected by checker", "markdown",
                    Arrays.asList(new Checkpoint("security", false)), 20L),
                newNodeResult("node-3", true, "passed", null,
                    Arrays.asList(new Checkpoint("lint", true), new Checkpoint("syntax", true)),
                    30L)));
        for (List<PipelineNodeResult> original : cases) {
            String json = JacksonUtils.toJson(original);
            List<PipelineNodeResult> deserialized = JacksonUtils.toObj(json,
                new TypeReference<List<PipelineNodeResult>>() {
                });
            assertEquals(original, deserialized);
        }
    }
    
    private PipelineNodeResult newNodeResult(String nodeId, boolean passed, String message,
        String messageType,
        List<Checkpoint> checkpoints, long durationMs) {
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId(nodeId);
        result.setExecutedAt("2026-03-27T12:00:00Z");
        result.setPassed(passed);
        result.setMessage(message);
        result.setMessageType(messageType);
        result.setCheckpoints(checkpoints == null ? null : new ArrayList<Checkpoint>(checkpoints));
        result.setDurationMs(durationMs);
        return result;
    }
}
