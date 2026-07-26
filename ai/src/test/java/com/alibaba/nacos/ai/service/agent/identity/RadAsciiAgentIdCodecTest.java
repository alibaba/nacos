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

package com.alibaba.nacos.ai.service.agent.identity;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadAsciiAgentIdCodecTest {
    
    private static final char MIN_PRINTABLE_ASCII = 0x20;
    
    private static final char MAX_PRINTABLE_ASCII = 0x7E;
    
    private static final Pattern OUTPUT_PATTERN = Pattern.compile("[A-Za-z0-9-]+");
    
    @Test
    void testSafeNamesRemainUnchangedAtLengthBoundaries() {
        assertPassThrough("A");
        assertPassThrough("z");
        assertPassThrough("0");
        assertPassThrough("-");
        assertPassThrough("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-");
        assertPassThrough("Nacos-Agent-RC1-2026-");
        assertPassThrough(repeat('A', 64));
    }
    
    @Test
    void testEveryPrintableAsciiCharacterInEncodedForm() {
        for (char current = MIN_PRINTABLE_ASCII; current <= MAX_PRINTABLE_ASCII; current++) {
            String original = "A" + current + "z!";
            String expected = "enc-A" + encodeCharacter(current) + "z-033";
            String encoded = RadAsciiAgentIdCodec.encode(original);
            assertEquals(expected, encoded, printableDescription(current));
            assertEquals(original, RadAsciiAgentIdCodec.decode(encoded),
                printableDescription(current));
            assertTrue(OUTPUT_PATTERN.matcher(encoded).matches(),
                printableDescription(current));
        }
    }
    
    @Test
    void testAdjacentPrintableCharactersRoundTrip() {
        for (char first = MIN_PRINTABLE_ASCII; first <= MAX_PRINTABLE_ASCII; first++) {
            for (char second = MIN_PRINTABLE_ASCII; second <= MAX_PRINTABLE_ASCII; second++) {
                String original = "A" + first + second + "!";
                String encoded = RadAsciiAgentIdCodec.encode(original);
                assertEquals(original, RadAsciiAgentIdCodec.decode(encoded),
                    printableDescription(first) + ',' + printableDescription(second));
                assertEquals(encoded,
                    RadAsciiAgentIdCodec.encode(RadAsciiAgentIdCodec.decode(encoded)));
            }
        }
    }
    
    @Test
    void testSpecialCharactersAtAllPositionsWithoutTrimming() {
        assertCanonical("enc--095A-046", "_A.");
        assertCanonical("enc-A-046-095", "A._");
        assertCanonical("enc--032A-032", " A ");
        assertCanonical("enc-A-033-064-035", "A!@#");
        assertCanonical("enc-A-045B-046", "A-B.");
        assertCanonical("enc--032-095-045-046-058-047-126-032",
            " _-.:/~ ");
    }
    
    @Test
    void testEncodedLengthBoundariesAndDeterminism() {
        String singleUnsafe = RadAsciiAgentIdCodec.encode("_");
        assertEquals("enc--095", singleUnsafe);
        assertEquals("_", RadAsciiAgentIdCodec.decode(singleUnsafe));
        
        String maximumSafe = repeat('A', 64);
        assertEquals(64, RadAsciiAgentIdCodec.encode(maximumSafe).length());
        
        String maximumUnsafe = repeat('!', 64);
        String encoded = RadAsciiAgentIdCodec.encode(maximumUnsafe);
        assertEquals("enc-" + repeat("-033", 64), encoded);
        assertEquals(260, encoded.length());
        assertEquals(encoded, RadAsciiAgentIdCodec.encode(maximumUnsafe));
        assertEquals(maximumUnsafe, RadAsciiAgentIdCodec.decode(encoded));
        assertTrue(OUTPUT_PATTERN.matcher(encoded).matches());
    }
    
    @Test
    void testRejectInvalidAgentNameLengthsAndSpaces() {
        assertEncodeRejected(null);
        assertEncodeRejected("");
        assertEncodeRejected(" ");
        assertEncodeRejected(repeat(' ', 64));
        assertEncodeRejected(repeat('A', 65));
        assertEncodeRejected(repeat('!', 65));
    }
    
    @Test
    void testRejectControlAndNonAsciiAgentNames() {
        for (char current = 0; current < MIN_PRINTABLE_ASCII; current++) {
            assertEncodeRejected("A" + current + "B");
        }
        assertEncodeRejected("A" + (char) 0x7F + "B");
        assertEncodeRejected("A" + (char) 0x80 + "B");
        assertEncodeRejected("unicode-代理");
        assertEncodeRejected("A" + (char) 0xD800 + "B");
        assertEncodeRejected("A" + (char) 0xDC00 + "B");
        assertEncodeRejected("A" + (char) 0xD83D + (char) 0xDE00 + "B");
    }
    
    @Test
    void testDecodeSafePhysicalValuesAtLengthBoundaries() {
        assertPassThrough("A");
        assertPassThrough("z");
        assertPassThrough("0");
        assertPassThrough("-");
        assertPassThrough("Agent-01-RC1");
        assertPassThrough("Enc-Abc");
        assertPassThrough(repeat('A', 64));
    }
    
    @Test
    void testDecodeCompleteThreeDigitEscapeDomain() {
        for (int value = 0; value <= 999; value++) {
            String encoded = "enc-A-" + threeDigits(value) + "-046";
            if (isCanonicalEscapedValue(value)) {
                String decoded = "A" + (char) value + '.';
                assertEquals(decoded, RadAsciiAgentIdCodec.decode(encoded), encoded);
                assertEquals(encoded, RadAsciiAgentIdCodec.encode(decoded), encoded);
            } else {
                assertDecodeRejected(encoded);
            }
        }
    }
    
    @Test
    void testRejectMalformedEscapeFraming() {
        assertDecodeRejected("enc-A-");
        assertDecodeRejected("enc-A-0");
        assertDecodeRejected("enc-A-03");
        assertDecodeRejected("enc-A-A32");
        assertDecodeRejected("enc-A-0A2");
        assertDecodeRejected("enc-A-03A");
        assertDecodeRejected("enc-A--32");
        assertDecodeRejected("enc-A-032-");
        assertDecodeRejected("enc-A-B-046");
    }
    
    @Test
    void testRejectCharactersOutsidePhysicalAlphabet() {
        assertDecodeRejected(null);
        assertDecodeRejected("");
        for (char current = 0; current <= 0x7F; current++) {
            if (!isPhysicalCharacter(current)) {
                assertDecodeRejected("A" + current + "B");
            }
        }
        assertDecodeRejected("Agent_01");
        assertDecodeRejected("Agent.01");
        assertDecodeRejected("Agent 01");
        assertDecodeRejected("Agent代理");
        assertDecodeRejected(repeat('A', 261));
    }
    
    @Test
    void testRejectDecodedNamesOutsidePublicContract() {
        assertDecodeRejected(repeat('A', 65));
        assertDecodeRejected("enc-" + repeat('A', 64) + "-046");
        assertDecodeRejected("enc--032");
        assertDecodeRejected("enc--032-032");
    }
    
    @Test
    void testRejectNonCanonicalEncodedForms() {
        assertDecodeRejected("enc-A-045B");
        assertDecodeRejected("enc--045");
        assertDecodeRejected("enc-A-065-046");
        assertDecodeRejected("enc-A-097-046");
        assertDecodeRejected("enc-A-048-046");
    }
    
    @Test
    void testAcceptedEncodedPrefixAmbiguity() {
        assertEquals("enc-", RadAsciiAgentIdCodec.encode("enc-"));
        assertDecodeRejected("enc-");
        assertEquals("enc-Abc", RadAsciiAgentIdCodec.encode("enc-Abc"));
        assertDecodeRejected("enc-Abc");
        
        String encodedName = RadAsciiAgentIdCodec.encode("Nacos Agent");
        String rawSafeName = RadAsciiAgentIdCodec.encode("enc-Nacos-032Agent");
        assertEquals("enc-Nacos-032Agent", encodedName);
        assertEquals(encodedName, rawSafeName);
        assertEquals("Nacos Agent", RadAsciiAgentIdCodec.decode(encodedName));
        
        String unsafePrefixedName = "enc-A B";
        String encodedUnsafePrefixedName = RadAsciiAgentIdCodec.encode(unsafePrefixedName);
        assertEquals("enc-enc-045A-032B", encodedUnsafePrefixedName);
        assertEquals(unsafePrefixedName,
            RadAsciiAgentIdCodec.decode(encodedUnsafePrefixedName));
    }
    
    private void assertPassThrough(String value) {
        assertEquals(value, RadAsciiAgentIdCodec.encode(value));
        assertEquals(value, RadAsciiAgentIdCodec.decode(value));
    }
    
    private void assertCanonical(String encoded, String decoded) {
        assertEquals(encoded, RadAsciiAgentIdCodec.encode(decoded));
        assertEquals(decoded, RadAsciiAgentIdCodec.decode(encoded));
    }
    
    private void assertEncodeRejected(String value) {
        assertThrows(IllegalArgumentException.class,
            () -> RadAsciiAgentIdCodec.encode(value), String.valueOf(value));
    }
    
    private void assertDecodeRejected(String value) {
        assertThrows(IllegalArgumentException.class,
            () -> RadAsciiAgentIdCodec.decode(value), String.valueOf(value));
    }
    
    private String encodeCharacter(char value) {
        return isAsciiLetterOrDigit(value) ? String.valueOf(value) : '-' + threeDigits(value);
    }
    
    private boolean isCanonicalEscapedValue(int value) {
        return value >= MIN_PRINTABLE_ASCII && value <= MAX_PRINTABLE_ASCII
            && !isAsciiLetterOrDigit((char) value);
    }
    
    private boolean isAsciiLetterOrDigit(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z'
            || value >= '0' && value <= '9';
    }
    
    private boolean isPhysicalCharacter(char value) {
        return isAsciiLetterOrDigit(value) || value == '-';
    }
    
    private String threeDigits(int value) {
        return new String(new char[] {(char) ('0' + value / 100),
            (char) ('0' + value / 10 % 10), (char) ('0' + value % 10)});
    }
    
    private String printableDescription(char value) {
        return "ASCII " + (int) value + " ('" + value + "')";
    }
    
    private String repeat(char value, int count) {
        return repeat(String.valueOf(value), count);
    }
    
    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
