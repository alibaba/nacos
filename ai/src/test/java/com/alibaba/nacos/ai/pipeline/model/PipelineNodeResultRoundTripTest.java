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

/**
 * Tests for PipelineNodeResult JSON serialization round-trip.
 *
 * <p><b>Validates: Requirements 6.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineNodeResultRoundTripTest {
    
    private static PipelineNodeResult node(String nodeId, boolean passed, String messageType,
        List<Checkpoint> checkpoints) {
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId(nodeId);
        result.setExecutedAt("2024-06-15T12:00:00Z");
        result.setPassed(passed);
        result.setMessage("msg-" + nodeId);
        result.setDurationMs(100L);
        result.setMessageType(messageType);
        result.setCheckpoints(checkpoints);
        return result;
    }
    
    private static List<List<PipelineNodeResult>> sampleLists() {
        List<List<PipelineNodeResult>> lists = new ArrayList<>();
        lists.add(Collections.emptyList());
        lists.add(Collections.singletonList(node("n1", true, "text", null)));
        lists.add(Arrays.asList(
            node("a", true, "json", Collections.singletonList(new Checkpoint("cp1", true))),
            node("b", false, null, Collections.emptyList())));
        lists.add(Arrays.asList(
            node("x", false, "markdown", null),
            node("y", true, "html",
                Arrays.asList(new Checkpoint("c1", false), new Checkpoint("c2", true)))));
        return lists;
    }
    
    /**
     * PipelineNodeResult JSON serialization round-trip.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Test
    void jsonSerializationRoundTrip() {
        for (List<PipelineNodeResult> original : sampleLists()) {
            String json = JacksonUtils.toJson(original);
            List<PipelineNodeResult> deserialized =
                JacksonUtils.toObj(json, new TypeReference<List<PipelineNodeResult>>() {
                });
            assertEquals(original, deserialized);
        }
    }
}
