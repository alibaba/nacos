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

package com.alibaba.nacos.ai.service.a2a.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsciiAgentIdCodecTest {
    
    private AsciiAgentIdCodec agentIdCodec;
    
    @BeforeEach
    public void setUp() {
        agentIdCodec = new AsciiAgentIdCodec();
    }
    
    // Does not encode when name is already valid
    @Test
    void testUsageDoesNotEncodeValidNames() {
        String input = "abc123";
        assertEquals(input, agentIdCodec.encode(input));
    }
    
    // Round-trip encode/decode when input contains a space
    @Test
    void testEncodeAndDecodeWhenNameContainsSpace() {
        String input = "hello world";
        String encoded = agentIdCodec.encode(input);
        assertEquals("_-.SYSENC:hello_0020world", encoded);
        assertEquals(input, agentIdCodec.decode(encoded));
    }
    
    // Valid special characters (._:-) should be preserved without encoding
    @Test
    void testValidSpecialCharsAreKept() {
        String input = "name_ok.1:2";
        assertEquals(input, agentIdCodec.encode(input));
    }
    
    // Round-trip encode/decode for mixed unicode letters and ASCII
    @Test
    void testRoundTripUnicodeChars() {
        String input = " Ω test";
        String encoded = agentIdCodec.encode(input);
        assertEquals("_-.SYSENC:_0020Ω_0020test", encoded);
        String decoded = agentIdCodec.decode(encoded);
        assertEquals(input, decoded);
    }
    
    // Input starts with underscore followed by hex-like sequence; verify behavior policy
    @Test
    void testUnderscoreFollowedByHexAmbiguityHandledByPolicy() {
        String original = "1 _abcd";
        String encoded = agentIdCodec.encode(original);
        assertEquals("_-.SYSENC:1_0020_005fabcd", encoded);
        assertEquals(original, agentIdCodec.decode(encoded));
    }
    
    // Round-trip for extreme boundary code points (NUL and U+FFFF)
    @Test
    void testBoundaryCharacters() {
        String input = "\u0000\uFFFF";
        String encoded = agentIdCodec.encode(input);
        assertEquals("_-.SYSENC:_0000_ffff", encoded);
        String decoded = agentIdCodec.decode(encoded);
        assertEquals(input, decoded);
    }
    
    // Encoding keeps empty string as-is and preserves a single underscore
    @Test
    void testEncodeKeepsEmptyAndUnderscore() {
        String empty = "";
        String encodedEmpty = agentIdCodec.encode(empty);
        assertEquals("", encodedEmpty);
        
        String underscoreOnly = "_";
        String encodedUnderscore = agentIdCodec.encode(underscoreOnly);
        assertEquals(underscoreOnly, encodedUnderscore);
    }
    
    // Encoding is idempotent for already-encoded output; decode restores original
    @Test
    void testAlreadyEncodedStringIsIdempotentOnEncode() {
        String original = "with space and Ω and tab\t";
        String first = agentIdCodec.encode(original);
        String second = agentIdCodec.encode(first);
        assertEquals("_-.SYSENC:with_0020space_0020and_0020Ω_0020and_0020tab_0009", first);
        // encodeName should not double-encode an already valid string
        assertEquals(first, second);
        // decode should restore original
        assertEquals(original, agentIdCodec.decode(first));
    }
    
    // Round-trip for mixture of ASCII, control (tab), and underscore suffix
    @Test
    void testMixedUnicodeAndControlCharactersRoundTrip() {
        String original = "A B\tC_";
        String encoded = agentIdCodec.encode(original);
        assertEquals("_-.SYSENC:A_0020B_0009C_005f",  encoded);
        String decoded = agentIdCodec.decode(encoded);
        assertEquals(original, decoded);
    }
    
    // Decoding a string with encoded prefix returns body; encoding leaves valid input unchanged
    @Test
    void testDecodeNameWithFakeEncodedPrefixBody() {
        String fake = "_-.SYSENC:hello";
        // This string is already valid; encodeName should return as-is
        assertEquals(fake, agentIdCodec.encode(fake));
        // decodeName should strip prefix and return body unchanged
        assertEquals("hello", agentIdCodec.decode(fake));
    }
}