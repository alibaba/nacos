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

package com.alibaba.nacos.api.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NacosAiConfigKeyCodec}.
 */
class NacosAiConfigKeyCodecTest {
    
    @Test
    void identityWhenAlreadyValid() {
        assertEquals("my-skill_v1.0", NacosAiConfigKeyCodec.encodeSegment("my-skill_v1.0"));
        assertEquals("my-skill_v1.0", NacosAiConfigKeyCodec.decodeSegment("my-skill_v1.0"));
    }
    
    @Test
    void roundTripWithSpacesAndUnicode() {
        String raw = "my worker 测试";
        String enc = NacosAiConfigKeyCodec.encodeSegment(raw);
        assertTrue(enc.startsWith(NacosAiConfigKeyCodec.ENCODED_PREFIX));
        assertTrue(NacosAiConfigKeyCodec.isValidNacosConfigParam(enc));
        assertEquals(raw, NacosAiConfigKeyCodec.decodeSegment(enc));
    }
    
    @Test
    void roundTripAtSign() {
        String raw = "skill@corp/name";
        String enc = NacosAiConfigKeyCodec.encodeSegment(raw);
        assertTrue(NacosAiConfigKeyCodec.isValidNacosConfigParam(enc));
        assertEquals(raw, NacosAiConfigKeyCodec.decodeSegment(enc));
    }
    
    @Test
    void nullAndEmpty() {
        assertEquals(null, NacosAiConfigKeyCodec.encodeSegment(null));
        assertEquals(null, NacosAiConfigKeyCodec.decodeSegment(null));
        assertEquals("", NacosAiConfigKeyCodec.encodeSegment(""));
        assertEquals("", NacosAiConfigKeyCodec.decodeSegment(""));
    }
    
    @Test
    void decodeBadHexThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> NacosAiConfigKeyCodec.decodeSegment(NacosAiConfigKeyCodec.ENCODED_PREFIX + "zz"));
    }
    
    @Test
    void versionedGroupSegmentAlwaysEncodedAndRoundTrips() {
        String enc = NacosAiConfigKeyCodec.encodeVersionedGroupSegment("v1");
        assertTrue(enc.startsWith(NacosAiConfigKeyCodec.ENCODED_PREFIX));
        assertEquals("v1", NacosAiConfigKeyCodec.decodeSegment(enc));
        assertEquals("_", NacosAiConfigKeyCodec.decodeSegment(
            NacosAiConfigKeyCodec.encodeVersionedGroupSegment("_")));
    }
}
