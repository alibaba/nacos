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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.model.mcp.registry.Icon;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class McpServerBasicInfoEnhancedFieldsTest {
    
    @Test
    void testNamespaceIdField() {
        McpServerBasicInfo basicInfo = new McpServerBasicInfo();
        basicInfo.setNamespaceId("public");
        
        assertEquals("public", basicInfo.getNamespaceId());
    }
    
    @Test
    void testIconsField() {
        McpServerBasicInfo basicInfo = new McpServerBasicInfo();
        
        Icon icon1 = new Icon();
        icon1.setSrc("https://example.com/icon-light.png");
        icon1.setMimeType(Icon.MimeType.IMAGE_PNG);
        icon1.setTheme(Icon.Theme.LIGHT);
        
        Icon icon2 = new Icon();
        icon2.setSrc("https://example.com/icon-dark.png");
        icon2.setMimeType(Icon.MimeType.IMAGE_PNG);
        icon2.setTheme(Icon.Theme.DARK);
        
        basicInfo.setIcons(Arrays.asList(icon1, icon2));
        
        assertNotNull(basicInfo.getIcons());
        assertEquals(2, basicInfo.getIcons().size());
        assertEquals("light", basicInfo.getIcons().get(0).getTheme().getValue());
        assertEquals("dark", basicInfo.getIcons().get(1).getTheme().getValue());
    }
    
    @Test
    void testWebsiteUrlField() {
        McpServerBasicInfo basicInfo = new McpServerBasicInfo();
        basicInfo.setWebsiteUrl("https://example.com");
        
        assertEquals("https://example.com", basicInfo.getWebsiteUrl());
    }
    
    @Test
    void testAllEnhancedFieldsTogether() {
        McpServerBasicInfo basicInfo = new McpServerBasicInfo();
        basicInfo.setId("server-1");
        basicInfo.setName("Test Server");
        basicInfo.setNamespaceId("custom");
        basicInfo.setWebsiteUrl("https://test.example.com");
        basicInfo.setDescription("A test server");
        
        Icon icon = new Icon();
        icon.setSrc("https://example.com/logo.svg");
        icon.setMimeType(Icon.MimeType.IMAGE_SVG_XML);
        basicInfo.setIcons(Arrays.asList(icon));
        
        // Verify all fields are accessible
        assertEquals("server-1", basicInfo.getId());
        assertEquals("Test Server", basicInfo.getName());
        assertEquals("custom", basicInfo.getNamespaceId());
        assertEquals("https://test.example.com", basicInfo.getWebsiteUrl());
        assertEquals("A test server", basicInfo.getDescription());
        assertEquals(1, basicInfo.getIcons().size());
    }
    
    @Test
    void testNamespaceIdInheritedByDetailInfo() {
        McpServerDetailInfo detailInfo = new McpServerDetailInfo();
        detailInfo.setNamespaceId("public");
        
        assertEquals("public", detailInfo.getNamespaceId());
    }
}
