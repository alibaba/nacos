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

package com.alibaba.nacos.ai.service.repository;

import com.alibaba.nacos.ai.model.AiResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * {@link AiResourceRowMappers} unit test for scope/owner mapping.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class AiResourceRowMappersTest {
    
    @Mock
    private ResultSet resultSet;
    
    @Test
    void testAiResourceRowMapperIncludesScopeAndOwner() throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getTimestamp("gmt_create")).thenReturn(now);
        when(resultSet.getTimestamp("gmt_modified")).thenReturn(now);
        when(resultSet.getString("name")).thenReturn("test-skill");
        when(resultSet.getString("type")).thenReturn("skill");
        when(resultSet.getString("c_desc")).thenReturn("desc");
        when(resultSet.getString("status")).thenReturn("enabled");
        when(resultSet.getString("namespace_id")).thenReturn("public");
        when(resultSet.getString("biz_tags")).thenReturn("");
        when(resultSet.getString("ext")).thenReturn("{}");
        when(resultSet.getString("c_from")).thenReturn("import");
        when(resultSet.getString("version_info")).thenReturn("{}");
        when(resultSet.getLong("meta_version")).thenReturn(1L);
        when(resultSet.getString("scope")).thenReturn("PUBLIC");
        when(resultSet.getString("owner")).thenReturn("alice");
        
        AiResource resource = AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER.mapRow(resultSet, 0);
        
        assertNotNull(resource);
        assertEquals(1L, resource.getId());
        assertEquals("test-skill", resource.getName());
        assertEquals("import", resource.getFrom());
        assertEquals("PUBLIC", resource.getScope());
        assertEquals("alice", resource.getOwner());
    }
    
    @Test
    void testAiResourceRowMapperWithPrivateScope() throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        when(resultSet.getLong("id")).thenReturn(2L);
        when(resultSet.getTimestamp("gmt_create")).thenReturn(now);
        when(resultSet.getTimestamp("gmt_modified")).thenReturn(now);
        when(resultSet.getString("name")).thenReturn("private-skill");
        when(resultSet.getString("type")).thenReturn("skill");
        when(resultSet.getString("c_desc")).thenReturn(null);
        when(resultSet.getString("status")).thenReturn(null);
        when(resultSet.getString("namespace_id")).thenReturn("");
        when(resultSet.getString("biz_tags")).thenReturn(null);
        when(resultSet.getString("ext")).thenReturn(null);
        when(resultSet.getString("c_from")).thenReturn("local");
        when(resultSet.getString("version_info")).thenReturn(null);
        when(resultSet.getLong("meta_version")).thenReturn(1L);
        when(resultSet.getString("scope")).thenReturn("PRIVATE");
        when(resultSet.getString("owner")).thenReturn("bob");
        
        AiResource resource = AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER.mapRow(resultSet, 0);
        
        assertNotNull(resource);
        assertEquals("local", resource.getFrom());
        assertEquals("PRIVATE", resource.getScope());
        assertEquals("bob", resource.getOwner());
    }
}
