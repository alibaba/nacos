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

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for PublishPipelineResourceType enum.
 *
 * <p><b>Validates: Requirements 1.2, 1.3, 1.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PublishPipelineResourceTypePropertyTest {
    
    /**
     * Property 1: Enum serialization/deserialization roundtrip consistency.
     *
     * <p>For any PublishPipelineResourceType enum value (including AGENTSPEC),
     * {@code valueOf(e.name())} should return an enum equal to the original value.</p>
     *
     * <p><b>Validates: Requirements 1.2, 1.3, 1.4</b></p>
     */
    @Property
    void enumSerializationRoundtrip(@ForAll("resourceTypes") PublishPipelineResourceType original) {
        String serialized = original.name();
        PublishPipelineResourceType deserialized = PublishPipelineResourceType.valueOf(serialized);
        assertEquals(original, deserialized,
                "valueOf(name()) should return the original enum value for " + original);
    }
    
    @Provide
    Arbitrary<PublishPipelineResourceType> resourceTypes() {
        return Arbitraries.of(PublishPipelineResourceType.values());
    }
}
