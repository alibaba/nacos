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

package com.alibaba.nacos.ai.form.mcp.admin;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpImportFormTest {
    
    @Test
    void testValidateSuccessWithJsonType() throws NacosApiException {
        McpImportForm form = new McpImportForm();
        form.setImportType("json");
        form.setData("{\"test\": \"data\"}");
        form.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateSuccessWithUrlType() throws NacosApiException {
        McpImportForm form = new McpImportForm();
        form.setImportType("url");
        form.setData("http://example.com/registry.json");
        form.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateSuccessWithFileType() throws NacosApiException {
        McpImportForm form = new McpImportForm();
        form.setImportType("file");
        form.setData("/path/to/registry.json");
        form.validate();
        // Should not throw exception
    }
    
    @Test
    void testValidateWithEmptyImportTypeShouldThrowException() {
        McpImportForm form = new McpImportForm();
        form.setImportType("");
        form.setData("{\"test\": \"data\"}");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testValidateWithNullImportTypeShouldThrowException() {
        McpImportForm form = new McpImportForm();
        form.setImportType(null);
        form.setData("{\"test\": \"data\"}");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testValidateWithEmptyDataShouldThrowException() {
        McpImportForm form = new McpImportForm();
        form.setImportType("json");
        form.setData("");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testValidateWithNullDataShouldThrowException() {
        McpImportForm form = new McpImportForm();
        form.setImportType("json");
        form.setData(null);
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testValidateWithInvalidImportTypeShouldThrowException() {
        McpImportForm form = new McpImportForm();
        form.setImportType("invalid");
        form.setData("{\"test\": \"data\"}");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testValidateShouldFillDefaultValue() throws NacosApiException {
        McpImportForm form = new McpImportForm();
        form.setImportType("json");
        form.setData("{\"test\": \"data\"}");
        form.validate();
        assertEquals("public", form.getNamespaceId());
    }
    
    @Test
    void testGetterAndSetter() {
        McpImportForm form = new McpImportForm();
        
        // Test basic fields
        form.setImportType("json");
        form.setData("{\"test\": \"data\"}");
        form.setOverrideExisting(true);
        form.setValidateOnly(true);
        form.setSkipInvalid(true);
        form.setCursor("cursor123");
        form.setLimit(10);
        form.setSearch("test");
        
        String[] selectedServers = {"server1", "server2"};
        form.setSelectedServers(selectedServers);
        
        assertEquals("json", form.getImportType());
        assertEquals("{\"test\": \"data\"}", form.getData());
        assertTrue(form.isOverrideExisting());
        assertTrue(form.isValidateOnly());
        assertTrue(form.isSkipInvalid());
        assertArrayEquals(selectedServers, form.getSelectedServers());
        assertEquals("cursor123", form.getCursor());
        assertEquals(10, form.getLimit());
        assertEquals("test", form.getSearch());
    }
    
    @Test
    void testDefaultValueOfBooleanFields() {
        McpImportForm form = new McpImportForm();
        
        assertFalse(form.isOverrideExisting());
        assertFalse(form.isValidateOnly());
        assertFalse(form.isSkipInvalid());
        assertNull(form.getSelectedServers());
        assertNull(form.getCursor());
        assertNull(form.getLimit());
        assertNull(form.getSearch());
    }
}