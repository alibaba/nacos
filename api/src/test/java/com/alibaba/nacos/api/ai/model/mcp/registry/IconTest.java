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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconTest extends BasicRequestTest {
    
    @Test
    void testSerializePngIcon() throws JsonProcessingException {
        Icon icon = new Icon();
        icon.setSrc("https://example.com/icon.png");
        icon.setMimeType(Icon.MimeType.IMAGE_PNG);
        icon.setSizes(Arrays.asList("16x16", "32x32", "64x64"));
        icon.setTheme(Icon.Theme.LIGHT);
        
        String json = mapper.writeValueAsString(icon);
        assertNotNull(json);
        assertTrue(json.contains("\"src\":\"https://example.com/icon.png\""));
        assertTrue(json.contains("\"mimeType\":\"image/png\""));
        assertTrue(json.contains("\"sizes\":[\"16x16\",\"32x32\",\"64x64\"]"));
        assertTrue(json.contains("\"theme\":\"light\""));
    }
    
    @Test
    void testSerializeSvgIcon() throws JsonProcessingException {
        Icon icon = new Icon();
        icon.setSrc("https://example.com/icon.svg");
        icon.setMimeType(Icon.MimeType.IMAGE_SVG_XML);
        icon.setTheme(Icon.Theme.DARK);
        
        String json = mapper.writeValueAsString(icon);
        assertTrue(json.contains("\"mimeType\":\"image/svg+xml\""));
        assertTrue(json.contains("\"theme\":\"dark\""));
    }
    
    @Test
    void testDeserializeIcon() throws JsonProcessingException {
        String json = "{\"src\":\"https://example.com/icon.png\",\"mimeType\":\"image/png\","
                + "\"sizes\":[\"16x16\",\"32x32\"],\"theme\":\"light\"}";
        Icon icon = mapper.readValue(json, Icon.class);
        
        assertNotNull(icon);
        assertEquals("https://example.com/icon.png", icon.getSrc());
        assertEquals(Icon.MimeType.IMAGE_PNG, icon.getMimeType());
        assertEquals(2, icon.getSizes().size());
        assertEquals("16x16", icon.getSizes().get(0));
        assertEquals(Icon.Theme.LIGHT, icon.getTheme());
    }
    
    @Test
    void testMimeTypeEnumValues() {
        assertEquals("image/png", Icon.MimeType.IMAGE_PNG.getValue());
        assertEquals("image/jpeg", Icon.MimeType.IMAGE_JPEG.getValue());
        assertEquals("image/jpg", Icon.MimeType.IMAGE_JPG.getValue());
        assertEquals("image/svg+xml", Icon.MimeType.IMAGE_SVG_XML.getValue());
        assertEquals("image/webp", Icon.MimeType.IMAGE_WEBP.getValue());
    }
    
    @Test
    void testThemeEnumValues() {
        assertEquals("light", Icon.Theme.LIGHT.getValue());
        assertEquals("dark", Icon.Theme.DARK.getValue());
    }
    
    @Test
    void testMimeTypeFromValue() {
        assertEquals(Icon.MimeType.IMAGE_PNG, Icon.MimeType.fromValue("image/png"));
        assertEquals(Icon.MimeType.IMAGE_SVG_XML,
                Icon.MimeType.fromValue("image/svg+xml"));
        assertEquals(Icon.MimeType.IMAGE_WEBP, Icon.MimeType.fromValue("image/webp"));
    }
    
    @Test
    void testThemeFromValue() {
        assertEquals(Icon.Theme.LIGHT, Icon.Theme.fromValue("light"));
        assertEquals(Icon.Theme.DARK, Icon.Theme.fromValue("dark"));
    }
    
    @Test
    void testMimeTypeFromValueCaseInsensitive() {
        assertEquals(Icon.MimeType.IMAGE_PNG, Icon.MimeType.fromValue("IMAGE/PNG"));
        assertEquals(Icon.MimeType.IMAGE_SVG_XML, Icon.MimeType.fromValue("IMAGE/SVG+XML"));
    }
    
    @Test
    void testIconMinimalRequired() throws JsonProcessingException {
        Icon icon = new Icon();
        icon.setSrc("https://example.com/required.png");
        
        String json = mapper.writeValueAsString(icon);
        assertTrue(json.contains("\"src\":\"https://example.com/required.png\""));
        assertTrue(json.contains("\"mimeType\":null") || !json.contains("mimeType"));
    }
}
