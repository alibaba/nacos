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
import com.fasterxml.jackson.core.type.TypeReference;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based test for PipelineNodeResult JSON serialization round-trip.
 *
 * <p><b>Validates: Requirements 6.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineNodeResultPropertyTest {
    
    /**
     * Property 9: PipelineNodeResult JSON serialization round-trip.
     *
     * <p>For any list of PipelineNodeResult, serializing to JSON and deserializing back
     * should produce an equivalent list of objects.</p>
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property
    void jsonSerializationRoundTrip(@ForAll("pipelineNodeResultList") List<PipelineNodeResult> original) {
        String json = JacksonUtils.toJson(original);
        List<PipelineNodeResult> deserialized = JacksonUtils.toObj(json, new TypeReference<List<PipelineNodeResult>>() { });
        assertEquals(original, deserialized);
    }
    
    @Provide
    Arbitrary<List<PipelineNodeResult>> pipelineNodeResultList() {
        return pipelineNodeResult().list().ofMinSize(0).ofMaxSize(10);
    }
    
    private Arbitrary<PipelineNodeResult> pipelineNodeResult() {
        Arbitrary<String> nodeIds = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> executedAts = Arbitraries.strings().alpha().numeric()
                .withChars('-', ':', 'T', 'Z').ofMinLength(10).ofMaxLength(25);
        Arbitrary<Boolean> passedValues = Arbitraries.of(true, false);
        Arbitrary<String> messages = Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                .ofMinLength(0).ofMaxLength(50);
        Arbitrary<Long> durations = Arbitraries.longs().between(0, 1_000_000L);
        
        return Combinators.combine(nodeIds, executedAts, passedValues, messages, durations)
                .as((nodeId, executedAt, passed, message, durationMs) -> {
                    PipelineNodeResult result = new PipelineNodeResult();
                    result.setNodeId(nodeId);
                    result.setExecutedAt(executedAt);
                    result.setPassed(passed);
                    result.setMessage(message);
                    result.setDurationMs(durationMs);
                    return result;
                });
    }
}
