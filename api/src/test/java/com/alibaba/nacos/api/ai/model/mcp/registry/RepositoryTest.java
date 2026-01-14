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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        Repository repository = new Repository();
        repository.setUrl("https://github.com/test/repo");
        repository.setSource("github");
        repository.setId("test-repo-id");
        repository.setSubfolder("sub/folder");
        
        String json = mapper.writeValueAsString(repository);
        assertNotNull(json);
        assertTrue(json.contains("\"url\":\"https://github.com/test/repo\""));
        assertTrue(json.contains("\"source\":\"github\""));
        assertTrue(json.contains("\"id\":\"test-repo-id\""));
        assertTrue(json.contains("\"subfolder\":\"sub/folder\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"url\":\"https://github.com/test/repo\",\"source\":\"github\","
                + "\"id\":\"test-repo-id\",\"subfolder\":\"sub/folder\"}";
        
        Repository repository = mapper.readValue(json, Repository.class);
        assertNotNull(repository);
        assertEquals("https://github.com/test/repo", repository.getUrl());
        assertEquals("github", repository.getSource());
        assertEquals("test-repo-id", repository.getId());
        assertEquals("sub/folder", repository.getSubfolder());
    }
}