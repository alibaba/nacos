/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ParamUtilsTest {
    
    @Test
    void testIsValid() {
        assertTrue(ParamUtils.isValid("test"));
        assertTrue(ParamUtils.isValid("test1234"));
        assertTrue(ParamUtils.isValid("test_-.:"));
        assertFalse(ParamUtils.isValid("test!"));
        assertFalse(ParamUtils.isValid("test~"));
    }
    
    @Test
    void testCheckParamV1() {
        //dataId is empty
        String dataId = "";
        String group = "test";
        String datumId = "test";
        String content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //group is empty
        dataId = "test";
        group = "";
        datumId = "test";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //datumId is empty
        dataId = "test";
        group = "test";
        datumId = "";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //content is empty
        dataId = "test";
        group = "test";
        datumId = "test";
        content = "";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //dataId invalid
        dataId = "test!";
        group = "test";
        datumId = "test";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //group invalid
        dataId = "test";
        group = "test!";
        datumId = "test";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //datumId invalid
        dataId = "test";
        group = "test";
        datumId = "test!";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //content over length
        dataId = "test";
        group = "test";
        datumId = "test";
        int maxContent = 10 * 1024 * 1024;
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < maxContent + 1; i++) {
            contentBuilder.append("t");
        }
        content = contentBuilder.toString();
        
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testCheckParamV2() {
        //tag invalid
        String tag = "test!";
        try {
            ParamUtils.checkParam(tag);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        //tag over length
        tag = "testtesttesttest1";
        try {
            ParamUtils.checkParam(tag);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
    }
    
    @Test
    void testCheckParamV3() {
        //tag size over 5
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "test,test,test,test,test,test");
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //tag length over 5
        configAdvanceInfo.clear();
        StringBuilder tagBuilder = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            tagBuilder.append("t");
        }
        configAdvanceInfo.put("config_tags", tagBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //desc length over 128
        configAdvanceInfo.clear();
        StringBuilder descBuilder = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            descBuilder.append("t");
        }
        configAdvanceInfo.put("desc", descBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //use length over 32
        configAdvanceInfo.clear();
        StringBuilder useBuilder = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            useBuilder.append("t");
        }
        configAdvanceInfo.put("use", useBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //effect length over 32
        configAdvanceInfo.clear();
        StringBuilder effectBuilder = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            effectBuilder.append("t");
        }
        configAdvanceInfo.put("effect", effectBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //type length over 32
        configAdvanceInfo.clear();
        StringBuilder typeBuilder = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            typeBuilder.append("t");
        }
        configAdvanceInfo.put("type", typeBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //schema length over 32768
        configAdvanceInfo.clear();
        StringBuilder schemaBuilder = new StringBuilder();
        for (int i = 0; i < 32769; i++) {
            schemaBuilder.append("t");
        }
        configAdvanceInfo.put("schema", schemaBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //invalid param
        configAdvanceInfo.clear();
        configAdvanceInfo.put("test", "test");
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testCheckTenant() {
        //tag invalid
        String tenant = "test!";
        try {
            ParamUtils.checkTenant(tenant);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        //tag over length
        int tanantMaxLen = 128;
        StringBuilder tenantBuilder = new StringBuilder();
        for (int i = 0; i < tanantMaxLen + 1; i++) {
            tenantBuilder.append("t");
        }
        tenant = tenantBuilder.toString();
        try {
            ParamUtils.checkTenant(tenant);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testCheckParamWithNamespaceGroupDataId() {
        assertThrows(NacosApiException.class, () -> ParamUtils.checkParam("../", "group", ""));
        assertThrows(NacosApiException.class, () -> ParamUtils.checkParam("dataId", "../", ""));
        assertThrows(NacosApiException.class, () -> ParamUtils.checkParam("dataId", "group", "../"));
        assertDoesNotThrow(() -> ParamUtils.checkParam("dataId", "group", ""));
        assertDoesNotThrow(() -> ParamUtils.checkParam("dataId", "group", UUID.randomUUID().toString()));
    }
    
    // Does not encode when name is already valid
    @Test
    void testUsageDoesNotEncodeValidNames() {
        String input = "abc123";
        assertTrue(ParamUtils.isValid(input));
        String processed = input;
        if (!ParamUtils.isValid(processed)) {
            processed = ParamUtils.encodeName(processed);
        }
        assertEquals(input, processed);
    }
    
    // Round-trip encode/decode when input contains a space
    @Test
    void testEncodeAndDecodeWhenNameContainsSpace() {
        String input = "hello world";
        assertFalse(ParamUtils.isValid(input));
        String encoded = ParamUtils.encodeName(input);
        assertTrue(ParamUtils.isEncoded(encoded));
        assertTrue(encoded.contains("_0020"));
        assertEquals(input, ParamUtils.decodeName(encoded));
    }
    
    // Valid special characters (._:-) should be preserved without encoding
    @Test
    void testValidSpecialCharsAreKept() {
        String input = "name_ok.1:2";
        assertTrue(ParamUtils.isValid(input));
        String processed = input;
        if (!ParamUtils.isValid(processed)) {
            processed = ParamUtils.encodeName(processed);
        }
        assertEquals(input, processed);
    }
    
    // Round-trip encode/decode for mixed unicode letters and ASCII
    @Test
    void testRoundTripUnicodeChars() {
        String input = " Ω test";
        assertFalse(ParamUtils.isValid(input));
        String encoded = ParamUtils.encodeName(input);
        String decoded = ParamUtils.decodeName(encoded);
        assertEquals(input, decoded);
    }
    
    // Input starts with underscore followed by hex-like sequence; verify behavior policy
    @Test
    void testUnderscoreFollowedByHexAmbiguityHandledByPolicy() {
        String original = "1 _abcd";
        if (!ParamUtils.isValid(original)) {
            String processed = ParamUtils.encodeName(original);
            assertEquals(original, ParamUtils.decodeName(processed));
        }
    }
    
    // Round-trip for extreme boundary code points (NUL and U+FFFF)
    @Test
    void testBoundaryCharacters() {
        String input = "\u0000\uFFFF";
        String encoded = ParamUtils.encodeName(input);
        String decoded = ParamUtils.decodeName(encoded);
        assertEquals(input, decoded);
    }
    
    // Encoding keeps empty string as-is and preserves a single underscore
    @Test
    void testEncodeKeepsEmptyAndUnderscore() {
        String empty = "";
        String encodedEmpty = ParamUtils.encodeName(empty);
        assertEquals("", encodedEmpty);
        assertFalse(ParamUtils.isEncoded(encodedEmpty));
        
        String underscoreOnly = "_";
        assertTrue(ParamUtils.isValid(underscoreOnly));
        String encodedUnderscore = ParamUtils.encodeName(underscoreOnly);
        assertEquals(underscoreOnly, encodedUnderscore);
        assertFalse(ParamUtils.isEncoded(encodedUnderscore));
    }
    
    // Encoding is idempotent for already-encoded output; decode restores original
    @Test
    void testAlreadyEncodedStringIsIdempotentOnEncode() {
        String original = "with space and Ω and tab\t";
        String first = ParamUtils.encodeName(original);
        assertTrue(ParamUtils.isEncoded(first));
        String second = ParamUtils.encodeName(first);
        // encodeName should not double-encode an already valid string
        assertEquals(first, second);
        // decode should restore original
        assertEquals(original, ParamUtils.decodeName(first));
    }
    
    // Round-trip for mixture of ASCII, control (tab), and underscore suffix
    @Test
    void testMixedUnicodeAndControlCharactersRoundTrip() {
        String original = "A B\tC_";
        String encoded = ParamUtils.encodeName(original);
        assertTrue(ParamUtils.isEncoded(encoded));
        String decoded = ParamUtils.decodeName(encoded);
        assertEquals(original, decoded);
    }
    
    // Decoding a string with encoded prefix returns body; encoding leaves valid input unchanged
    @Test
    void testDecodeNameWithFakeEncodedPrefixBody() {
        String fake = "_-.SYSENC:hello";
        // This string is already valid; encodeName should return as-is
        assertTrue(ParamUtils.isValid(fake));
        assertEquals(fake, ParamUtils.encodeName(fake));
        // decodeName should strip prefix and return body unchanged
        assertEquals("hello", ParamUtils.decodeName(fake));
    }
}
