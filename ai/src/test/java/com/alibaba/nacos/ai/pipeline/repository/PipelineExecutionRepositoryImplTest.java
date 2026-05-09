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

package com.alibaba.nacos.ai.pipeline.repository;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineExecutionRepositoryImplTest {
    
    private static final Object[] QUERY_PARAMS = {"SKILL", "demo", "public", "v1"};
    
    private JdbcTemplate jdbcTemplate;
    
    private PipelineExecutionRepositoryImpl repository;
    
    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new PipelineExecutionRepositoryImpl(jdbcTemplate);
    }
    
    @Test
    void findByResourceShouldUseDialectAgnosticQuery() {
        PipelineExecution newest = createExecution("latest");
        PipelineExecution older = createExecution("older");
        String expectedSql =
            "SELECT * FROM pipeline_execution WHERE resource_type=? AND resource_name=? "
                + "AND namespace_id=? AND version=? ORDER BY create_time DESC";
        when(jdbcTemplate.query(any(String.class), anyPipelineRowMapper(), eq(QUERY_PARAMS)))
            .thenReturn(
                List.of(newest, older));
        
        PipelineExecution actual = repository.findByResource("SKILL", "demo", "public", "v1");
        
        assertEquals("latest", actual.getExecutionId());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), anyPipelineRowMapper(), eq(QUERY_PARAMS));
        assertEquals(expectedSql, sqlCaptor.getValue());
        assertTrue(!sqlCaptor.getValue().contains("LIMIT"));
    }
    
    @Test
    void findByResourceShouldReturnNullWhenNoResult() {
        when(jdbcTemplate.query(any(String.class), anyPipelineRowMapper(), eq(QUERY_PARAMS)))
            .thenReturn(List.of());
        
        assertNull(repository.findByResource("SKILL", "demo", "public", "v1"));
    }
    
    @Test
    void findByResourceWithPageShouldPageInMemoryWithoutLimitOffsetSql() {
        PipelineExecution first = createExecution("first");
        PipelineExecution second = createExecution("second");
        PipelineExecution third = createExecution("third");
        String expectedSql =
            "SELECT * FROM pipeline_execution WHERE resource_type = ? AND resource_name = ? "
                + "AND namespace_id = ? AND version = ? ORDER BY create_time DESC";
        when(jdbcTemplate.query(any(String.class), anyPipelineRowMapper(), eq(QUERY_PARAMS)))
            .thenReturn(
                List.of(first, second, third));
        
        List<PipelineExecution> actual =
            repository.findByResourceWithPage("SKILL", "demo", "public", "v1", 1, 1);
        
        assertEquals(1, actual.size());
        assertEquals("second", actual.get(0).getExecutionId());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), anyPipelineRowMapper(), eq(QUERY_PARAMS));
        assertEquals(expectedSql, sqlCaptor.getValue());
        assertTrue(!sqlCaptor.getValue().contains("LIMIT"));
        assertTrue(!sqlCaptor.getValue().contains("OFFSET"));
    }
    
    @SuppressWarnings("unchecked")
    private RowMapper<PipelineExecution> anyPipelineRowMapper() {
        return (RowMapper<PipelineExecution>) any(RowMapper.class);
    }
    
    private PipelineExecution createExecution(String executionId) {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId(executionId);
        execution.setStatus(PipelineExecutionStatus.APPROVED);
        return execution;
    }
}
