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

package com.alibaba.nacos.client.ai.remote.capability;

import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiCapabilitySnapshotTest {
    
    @Test
    void shouldKeepEachFeatureIndependentAndIgnoreUnknownKeys() throws Exception {
        AiCapabilitySnapshot result = AiCapabilitySnapshot.parse(
            "{\"code\":0,\"data\":{\"schemaVersion\":1,\"capabilities\":"
                + "{\"radV1\":true,\"mcp\":false,\"skill\":\"true\",\"prompt\":null,"
                + "\"agentSpec\":true,\"agent\":true,\"future\":true}}}");
        assertEquals(AbilityStatus.SUPPORTED, result.get("radV1"));
        assertEquals(AbilityStatus.NOT_SUPPORTED, result.get("mcp"));
        assertEquals(AbilityStatus.UNKNOWN, result.get("skill"));
        assertEquals(AbilityStatus.UNKNOWN, result.get("prompt"));
        assertEquals(AbilityStatus.SUPPORTED, result.get("agentSpec"));
        assertEquals(AbilityStatus.UNKNOWN, result.get("agent"));
        assertEquals(AbilityStatus.UNKNOWN, result.get("future"));
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"<html>gateway</html>", "null", "[]", "{}", "{", "{\"code\":0}",
        "{\"code\":\"0\",\"data\":{}}", "{\"code\":0.2,\"data\":{}}",
        "{\"code\":0,\"data\":{\"schemaVersion\":2,\"capabilities\":{\"radV1\":true}}}",
        "{\"code\":0,\"data\":{\"schemaVersion\":\"1\",\"capabilities\":{\"radV1\":true}}}",
        "{\"code\":0,\"data\":{\"schemaVersion\":1,\"capabilities\":[]}}",
        "{\"code\":0,\"data\":{\"schemaVersion\":1,\"capabilities\":{\"agent\":true}}}"})
    void shouldNotInferNegativeEvidenceFromInvalidOrIncompleteResponses(String body)
        throws Exception {
        assertEquals(AbilityStatus.UNKNOWN, AiCapabilitySnapshot.parse(body).get("radV1"));
    }
    
    @Test
    void shouldPreserveExplicitApplicationErrorWithoutEchoingUntrustedPayload() {
        NacosException error = assertThrows(NacosException.class,
            () -> AiCapabilitySnapshot.parse("{\"code\":10001,\"message\":\"secret\"}"));
        assertEquals(10001, error.getErrCode());
        assertEquals("AI capability request was rejected.", error.getErrMsg());
    }
}
