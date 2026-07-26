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

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void roundTripWithUnicodeAndNoOtherInvalidCharacters() {
        String raw = "resource_docs_说明__md.json";
        assertFalse(NacosAiConfigKeyCodec.isValidNacosConfigParam(raw));
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
    void encodedPrefixNamespaceIsReserved() {
        for (String raw : Arrays.asList("enc.", "enc.61", "ENC.61", "enc.not-hex")) {
            String encoded = NacosAiConfigKeyCodec.encodeSegment(raw);
            assertNotEquals(raw, encoded);
            assertEquals(raw, NacosAiConfigKeyCodec.decodeSegment(encoded));
            
            String manifestSegment = NacosAiConfigKeyCodec.encodeManifestGroupNameSegment(raw);
            assertNotEquals(raw, manifestSegment);
            assertEquals(raw, NacosAiConfigKeyCodec.decodeSegment(manifestSegment));
        }
        
        assertNotEquals(NacosAiConfigKeyCodec.encodeSegment("a/b"),
            NacosAiConfigKeyCodec.encodeSegment("enc.612f62"));
        assertNotEquals(NacosAiConfigKeyCodec.encodeManifestGroupNameSegment("a/b"),
            NacosAiConfigKeyCodec.encodeManifestGroupNameSegment("enc.612f62"));
        
        String hashedLiteral = NacosAiConfigKeyCodec.HASHED_PREFIX + repeat('0', 64);
        String manifestSegment =
            NacosAiConfigKeyCodec.encodeManifestGroupNameSegment(hashedLiteral);
        assertNotEquals(hashedLiteral, manifestSegment);
        assertEquals(hashedLiteral, NacosAiConfigKeyCodec.decodeSegment(manifestSegment));
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
    
    @Test
    void physicalDataIdKeepsValuesWithinLimit() {
        String maxLength = repeat('a', NacosAiConfigKeyCodec.MAX_DATA_ID_LENGTH);
        assertEquals(null, NacosAiConfigKeyCodec.toPhysicalDataId(null));
        assertEquals("", NacosAiConfigKeyCodec.toPhysicalDataId(""));
        assertEquals("plain.json", NacosAiConfigKeyCodec.toPhysicalDataId("plain.json"));
        assertEquals(maxLength, NacosAiConfigKeyCodec.toPhysicalDataId(maxLength));
        
        String unicode = NacosAiConfigKeyCodec.toPhysicalDataId("说明.md");
        assertTrue(unicode.startsWith(NacosAiConfigKeyCodec.ENCODED_PREFIX));
        assertEquals("说明.md", NacosAiConfigKeyCodec.decodeSegment(unicode));
    }
    
    @Test
    void physicalDataIdHashesEncodedValuesOverLimit() {
        String overLimit = repeat('a', NacosAiConfigKeyCodec.MAX_DATA_ID_LENGTH + 1);
        assertEquals(
            "sha256.02d7160d77e18c6447be80c2e355c7ed4388545271702c50253b0914c65ce5fe",
            NacosAiConfigKeyCodec.toPhysicalDataId(overLimit));
        
        String longUnicode = repeat("说明", 50);
        String physical = NacosAiConfigKeyCodec.toPhysicalDataId(longUnicode);
        assertTrue(physical.startsWith(NacosAiConfigKeyCodec.HASHED_PREFIX));
        assertEquals(NacosAiConfigKeyCodec.HASHED_PREFIX.length() + 64, physical.length());
        assertTrue(NacosAiConfigKeyCodec.isValidNacosConfigParam(physical));
        assertEquals(physical, NacosAiConfigKeyCodec.toPhysicalDataId(longUnicode));
        assertNotEquals(physical, NacosAiConfigKeyCodec.toPhysicalDataId(longUnicode + "x"));
    }
    
    @Test
    void physicalMappingsDoNotAliasEncodedLiterals() {
        String longRaw = repeat("说明", 50);
        String encodedLiteral = NacosAiConfigKeyCodec.encodeSegment(longRaw);
        assertNotEquals(NacosAiConfigKeyCodec.toPhysicalDataId(longRaw),
            NacosAiConfigKeyCodec.toPhysicalDataId(encodedLiteral));
        
        String prefix = "skill_";
        String manifestEncodedLiteral =
            NacosAiConfigKeyCodec.encodeManifestGroupNameSegment(longRaw);
        String encodedGroup = prefix + manifestEncodedLiteral;
        String literalGroup = prefix
            + NacosAiConfigKeyCodec.encodeManifestGroupNameSegment(manifestEncodedLiteral);
        assertNotEquals(NacosAiConfigKeyCodec.toPhysicalGroup(encodedGroup, prefix),
            NacosAiConfigKeyCodec.toPhysicalGroup(literalGroup, prefix));
    }
    
    @Test
    void physicalDataIdReservesHashedKeyNamespace() {
        String reserved = NacosAiConfigKeyCodec.HASHED_PREFIX + repeat('0', 64);
        String physical = NacosAiConfigKeyCodec.toPhysicalDataId(reserved);
        assertNotEquals(reserved, physical);
        assertTrue(physical.startsWith(NacosAiConfigKeyCodec.HASHED_PREFIX));
        assertEquals(NacosAiConfigKeyCodec.HASHED_PREFIX.length() + 64, physical.length());
        
        String uppercaseReserved = "SHA256." + repeat('A', 64);
        assertNotEquals(uppercaseReserved,
            NacosAiConfigKeyCodec.toPhysicalDataId(uppercaseReserved));
        assertNotEquals(NacosAiConfigKeyCodec.toPhysicalDataId("a/b"),
            NacosAiConfigKeyCodec.toPhysicalDataId("enc.612f62"));
    }
    
    @Test
    void physicalGroupKeepsBoundaryAndHashesOverLimit() {
        String prefix = "agentspec__";
        String atLimit = prefix
            + repeat('g', NacosAiConfigKeyCodec.MAX_GROUP_LENGTH - prefix.length());
        assertEquals(atLimit, NacosAiConfigKeyCodec.toPhysicalGroup(atLimit, prefix));
        
        String overLimit = prefix
            + repeat('g', NacosAiConfigKeyCodec.MAX_GROUP_LENGTH - prefix.length() + 1);
        assertEquals(
            "agentspec__sha256.06b8feaba08446b1be5cf77b1bfb032b7271f660a301d33d08b35d1f5ef882eb",
            NacosAiConfigKeyCodec.toPhysicalGroup(overLimit, prefix));
        assertTrue(NacosAiConfigKeyCodec.toPhysicalGroup(overLimit, prefix)
            .length() <= NacosAiConfigKeyCodec.MAX_GROUP_LENGTH);
    }
    
    @Test
    void physicalGroupSupportsAllAiResourcePrefixesAndReservesHashNamespace() {
        for (String prefix : Arrays.asList("skill_", "agentspec__", "prompt__")) {
            String overLimit = prefix + repeat('x', NacosAiConfigKeyCodec.MAX_GROUP_LENGTH);
            String physical = NacosAiConfigKeyCodec.toPhysicalGroup(overLimit, prefix);
            assertTrue(physical.startsWith(prefix + NacosAiConfigKeyCodec.HASHED_PREFIX));
            assertTrue(physical.length() <= NacosAiConfigKeyCodec.MAX_GROUP_LENGTH);
            assertEquals(physical, NacosAiConfigKeyCodec.toPhysicalGroup(overLimit, prefix));
            
            String reserved = prefix + NacosAiConfigKeyCodec.HASHED_PREFIX + repeat('0', 64);
            assertNotEquals(reserved, NacosAiConfigKeyCodec.toPhysicalGroup(reserved, prefix));
            
            String uppercaseReserved = prefix + "SHA256." + repeat('A', 64);
            assertNotEquals(uppercaseReserved,
                NacosAiConfigKeyCodec.toPhysicalGroup(uppercaseReserved, prefix));
        }
    }
    
    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ch);
        return new String(chars);
    }
    
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
