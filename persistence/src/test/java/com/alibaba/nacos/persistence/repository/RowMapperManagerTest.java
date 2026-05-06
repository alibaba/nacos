/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RowMapperManagerTest {
    
    @Mock
    ResultSet resultSet;
    
    @Mock
    ResultSetMetaData resultSetMetaData;
    
    @BeforeEach
    void setUp() throws SQLException {
    }
    
    @AfterEach
    void tearDown() {
        RowMapperManager.mapperMap.clear();
        RowMapperManager.registerRowMapper(RowMapperManager.MAP_ROW_MAPPER.getClass().getCanonicalName(),
                RowMapperManager.MAP_ROW_MAPPER);
    }
    
    @Test
    void testRegisterRowMapper() {
        MockMapRowMapper mapper1 = new MockMapRowMapper();
        RowMapperManager.registerRowMapper(MockMapRowMapperObj.class.getCanonicalName(), mapper1);
        assertEquals(mapper1, RowMapperManager.getRowMapper(MockMapRowMapperObj.class.getCanonicalName()));
        MockMapRowMapper mapper2 = new MockMapRowMapper();
        RowMapperManager.registerRowMapper(MockMapRowMapperObj.class.getCanonicalName(), mapper2);
        assertEquals(mapper2, RowMapperManager.getRowMapper(MockMapRowMapperObj.class.getCanonicalName()));
    }
    
    @Test
    void testDefaultRowMapper() throws SQLException {
        when(resultSet.getObject(1)).thenReturn(1L);
        when(resultSet.getObject(2)).thenReturn("test");
        when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
        when(resultSetMetaData.getColumnCount()).thenReturn(2);
        when(resultSetMetaData.getColumnLabel(1)).thenReturn("id");
        when(resultSetMetaData.getColumnLabel(2)).thenReturn("name");
        Map<String, Object> actual = RowMapperManager.MAP_ROW_MAPPER.mapRow(resultSet, 1);
        assertEquals(1L, actual.get("id"));
        assertEquals("test", actual.get("name"));
    }
    
    private static class MockMapRowMapper implements RowMapper<MockMapRowMapperObj> {
        
        @Override
        public MockMapRowMapperObj mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MockMapRowMapperObj();
        }
    }
    
    private static class MockMapRowMapperObj {
    
    }
}