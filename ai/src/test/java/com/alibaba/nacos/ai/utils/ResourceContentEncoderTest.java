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

package com.alibaba.nacos.ai.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceContentEncoderTest {
    
    @Test
    void isTextShouldRecognizeCommonTextExtensions() {
        for (String name : Arrays.asList("SKILL.md", "manifest.json", "config.yaml",
            "README.txt", "build.gradle", "schema.proto", "script.py", "service.go",
            "Main.java", "index.ts", "styles.css", "query.sql")) {
            assertTrue(ResourceContentEncoder.isText(name), name + " should be text");
        }
    }
    
    @Test
    void isTextShouldRejectKnownBinaryExtensions() {
        for (String name : Arrays.asList("logo.png", "preview.jpg", "icon.ico",
            "doc.pdf", "font.ttf", "font.woff2", "blob.bin")) {
            assertFalse(ResourceContentEncoder.isText(name), name + " should be binary");
        }
    }
    
    @Test
    void isTextShouldRejectUnlistedBinaryExtensions() {
        // Files previously slipping through the legacy binary whitelist now fall back to binary.
        for (String name : Arrays.asList("payload.zip", "lib.jar", "audio.mp3",
            "video.mp4", "module.wasm", "report.docx", "deck.pptx", "data.xlsx")) {
            assertFalse(ResourceContentEncoder.isText(name), name + " should be binary");
        }
    }
    
    @Test
    void isTextShouldRecognizeWellKnownNoExtensionFiles() {
        for (String name : Arrays.asList("Dockerfile", "Makefile", "Jenkinsfile",
            "LICENSE", "NOTICE", "README", "CHANGELOG")) {
            assertTrue(ResourceContentEncoder.isText(name), name + " should be text");
        }
    }
    
    @Test
    void isTextShouldRecognizeCommonDotfiles() {
        for (String name : Arrays.asList(".gitignore", ".gitattributes", ".dockerignore",
            ".editorconfig", ".env", ".npmrc", ".prettierrc")) {
            assertTrue(ResourceContentEncoder.isText(name), name + " should be text");
        }
    }
    
    @Test
    void isTextShouldBeCaseInsensitive() {
        assertTrue(ResourceContentEncoder.isText("README.MD"));
        assertTrue(ResourceContentEncoder.isText("DOCKERFILE"));
        assertTrue(ResourceContentEncoder.isText("Notes.JSON"));
    }
    
    @Test
    void isTextShouldStripPathPrefix() {
        assertTrue(ResourceContentEncoder.isText("skill-name/scripts/run.sh"));
        assertTrue(ResourceContentEncoder.isText("nested/dir/Dockerfile"));
        assertFalse(ResourceContentEncoder.isText("assets/images/logo.png"));
    }
    
    @Test
    void isTextShouldRejectBlankAndNoExtensionUnknownFiles() {
        assertFalse(ResourceContentEncoder.isText(null));
        assertFalse(ResourceContentEncoder.isText(""));
        assertFalse(ResourceContentEncoder.isText("   "));
        // Unknown file with no extension and not in the dotfile/no-ext whitelist
        assertFalse(ResourceContentEncoder.isText("randomfile"));
        assertFalse(ResourceContentEncoder.isText("script."));
    }
    
    @Test
    void isBinaryShouldBeInverseOfIsText() {
        assertTrue(ResourceContentEncoder.isBinary("logo.png"));
        assertTrue(ResourceContentEncoder.isBinary("payload.zip"));
        assertFalse(ResourceContentEncoder.isBinary("README.md"));
        assertFalse(ResourceContentEncoder.isBinary("Dockerfile"));
    }
    
    @Test
    void encodeShouldReturnUtf8StringWithoutMetadataForTextFiles() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        ResourceContentEncoder.EncodedContent encoded =
            ResourceContentEncoder.encode(data, "notes.md");
        assertEquals("hello world", encoded.getContent());
        assertNull(encoded.getMetadata());
    }
    
    @Test
    void encodeShouldReturnBase64StringWithMetadataForBinaryFiles() {
        byte[] data = new byte[] {0x00, 0x01, (byte) 0xFF, (byte) 0xFE, 0x42};
        ResourceContentEncoder.EncodedContent encoded =
            ResourceContentEncoder.encode(data, "blob.bin");
        assertEquals(Base64.getEncoder().encodeToString(data), encoded.getContent());
        Map<String, Object> meta = encoded.getMetadata();
        assertNotNull(meta);
        assertEquals(ResourceContentEncoder.METADATA_ENCODING_BASE64,
            meta.get(ResourceContentEncoder.METADATA_ENCODING));
    }
    
    @Test
    void encodeShouldRoundTripBinaryBytes() {
        byte[] original = new byte[256];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) i;
        }
        ResourceContentEncoder.EncodedContent encoded =
            ResourceContentEncoder.encode(original, "payload.zip");
        byte[] decoded = Base64.getDecoder().decode(encoded.getContent());
        assertArrayEquals(original, decoded);
    }
    
    @Test
    void encodeShouldHandleNullAndEmptyData() {
        ResourceContentEncoder.EncodedContent forNull =
            ResourceContentEncoder.encode(null, "notes.md");
        assertEquals("", forNull.getContent());
        assertNull(forNull.getMetadata());
        
        ResourceContentEncoder.EncodedContent forEmpty =
            ResourceContentEncoder.encode(new byte[0], "blob.bin");
        assertEquals("", forEmpty.getContent());
        assertNull(forEmpty.getMetadata());
    }
    
    @Test
    void base64MetadataShouldContainOnlyEncodingFlag() {
        Map<String, Object> meta = ResourceContentEncoder.base64Metadata();
        assertEquals(1, meta.size());
        assertEquals(ResourceContentEncoder.METADATA_ENCODING_BASE64,
            meta.get(ResourceContentEncoder.METADATA_ENCODING));
    }
    
    @Test
    void publicMetadataConstantsShouldUseExpectedValues() {
        assertEquals("encoding", ResourceContentEncoder.METADATA_ENCODING);
        assertEquals("base64", ResourceContentEncoder.METADATA_ENCODING_BASE64);
    }
}
