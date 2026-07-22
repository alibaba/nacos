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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadServiceNameComposerTest {
    
    private static final char MIN_PRINTABLE_ASCII = 0x20;
    
    private static final char MAX_PRINTABLE_ASCII = 0x7E;
    
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9-]+");
    
    @Test
    void testComposeSafeAndEncodedAgentNames() {
        assertEquals("rad-Nacos-Agent-a2a",
            RadServiceNameComposer.compose("Nacos-Agent", "a2a"));
        assertEquals("rad-enc-Nacos-032Agent-a2a",
            RadServiceNameComposer.compose("Nacos Agent", "a2a"));
        assertEquals("rad-enc-Agent-09501-A2A-v1",
            RadServiceNameComposer.compose("Agent_01", "A2A-v1"));
    }
    
    @Test
    void testAcceptedAgentProtocolTupleCollision() {
        String protocolContainsSeparator = RadServiceNameComposer.compose("A", "B-C");
        String agentNameContainsSeparator = RadServiceNameComposer.compose("A-B", "C");
        assertEquals("rad-A-B-C", protocolContainsSeparator);
        assertEquals(protocolContainsSeparator, agentNameContainsSeparator);
    }
    
    @Test
    void testMinimumAndMaximumServiceNameLengths() {
        String minimum = RadServiceNameComposer.compose("A", "a");
        assertEquals("rad-A-a", minimum);
        assertEquals(7, minimum.length());
        
        String maximum = RadServiceNameComposer.compose(repeat('!', 64), repeat('a', 32));
        assertEquals(297, maximum.length());
        assertTrue(maximum.startsWith("rad-enc--033"));
        assertTrue(SERVICE_NAME_PATTERN.matcher(maximum).matches());
        assertTrue(maximum.length() <= 512);
    }
    
    @Test
    void testProtocolLengthAndShapeBoundaries() {
        assertValidProtocol("a");
        assertValidProtocol("1");
        assertValidProtocol("A-");
        assertValidProtocol("1-A--");
        assertValidProtocol("A" + repeat('-', 30) + "9");
        assertInvalidProtocol(null);
        assertInvalidProtocol("");
        assertInvalidProtocol(repeat('a', 33));
    }
    
    @Test
    void testCompletePrintableProtocolCharacterDomain() {
        for (char current = MIN_PRINTABLE_ASCII; current <= MAX_PRINTABLE_ASCII; current++) {
            String firstCharacter = String.valueOf(current) + 'a';
            if (isAsciiLetterOrDigit(current)) {
                assertValidProtocol(firstCharacter);
            } else {
                assertInvalidProtocol(firstCharacter);
            }
            
            String subsequentCharacter = "a" + current;
            if (isAsciiLetterOrDigit(current) || current == '-') {
                assertValidProtocol(subsequentCharacter);
            } else {
                assertInvalidProtocol(subsequentCharacter);
            }
        }
    }
    
    @Test
    void testRejectControlAndNonAsciiProtocolCharacters() {
        for (char current = 0; current < MIN_PRINTABLE_ASCII; current++) {
            assertInvalidProtocol(String.valueOf(current) + 'a');
            assertInvalidProtocol("a" + current);
        }
        assertInvalidProtocol("a" + (char) 0x7F);
        assertInvalidProtocol("a" + (char) 0x80);
        assertInvalidProtocol("a代理");
    }
    
    @Test
    void testComposerPreservesCase() {
        String upperAgent = RadServiceNameComposer.compose("Agent", "a2a");
        String lowerAgent = RadServiceNameComposer.compose("agent", "a2a");
        String upperProtocol = RadServiceNameComposer.compose("Agent", "A2A");
        assertEquals("rad-Agent-a2a", upperAgent);
        assertEquals("rad-agent-a2a", lowerAgent);
        assertEquals("rad-Agent-A2A", upperProtocol);
        assertNotEquals(upperAgent, lowerAgent);
        assertNotEquals(upperAgent, upperProtocol);
    }
    
    @Test
    void testAcceptedCodecCollisionProducesSamePhysicalService() {
        String encodedName = RadServiceNameComposer.compose("Nacos Agent", "a2a");
        String rawSafeName =
            RadServiceNameComposer.compose("enc-Nacos-032Agent", "a2a");
        assertEquals("rad-enc-Nacos-032Agent-a2a", encodedName);
        assertEquals(encodedName, rawSafeName);
    }
    
    @Test
    void testRejectInvalidAgentNames() {
        assertInvalidAgentName(null);
        assertInvalidAgentName("");
        assertInvalidAgentName("   ");
        assertInvalidAgentName("Agent\nName");
        assertInvalidAgentName("Agent代理");
        assertInvalidAgentName(repeat('A', 65));
    }
    
    private void assertValidProtocol(String protocol) {
        String serviceName = RadServiceNameComposer.compose("Agent", protocol);
        assertTrue(SERVICE_NAME_PATTERN.matcher(serviceName).matches(), String.valueOf(protocol));
        assertTrue(serviceName.endsWith('-' + protocol), serviceName);
    }
    
    private void assertInvalidProtocol(String protocol) {
        assertThrows(IllegalArgumentException.class,
            () -> RadServiceNameComposer.compose("Agent", protocol), String.valueOf(protocol));
    }
    
    private void assertInvalidAgentName(String agentName) {
        assertThrows(IllegalArgumentException.class,
            () -> RadServiceNameComposer.compose(agentName, "a2a"), String.valueOf(agentName));
    }
    
    private boolean isAsciiLetterOrDigit(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z'
            || value >= '0' && value <= '9';
    }
    
    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
