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
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for PipelineExecution persistence round-trip.
 *
 * <p><b>Validates: Requirements 3.4, 3.5</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineExecutionRepositoryTest {
    
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS pipeline_execution ("
        + "execution_id  VARCHAR(64)  PRIMARY KEY, "
        + "resource_type VARCHAR(32)  NOT NULL, "
        + "resource_name VARCHAR(256) NOT NULL, "
        + "namespace_id  VARCHAR(128), "
        + "version       VARCHAR(64), "
        + "status        VARCHAR(32)  NOT NULL, "
        + "pipeline      TEXT         NOT NULL, "
        + "create_time   BIGINT       NOT NULL, "
        + "update_time   BIGINT       NOT NULL)";
    
    private static JdbcTemplate jdbcTemplate;
    
    private static PipelineExecutionRepositoryImpl repository;
    
    @BeforeAll
    static void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pipeline_test;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        repository = new PipelineExecutionRepositoryImpl(jdbcTemplate);
    }
    
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM pipeline_execution");
    }
    
    private static PipelineNodeResult sampleNode(String id, boolean passed) {
        PipelineNodeResult result = new PipelineNodeResult();
        result.setNodeId(id);
        result.setExecutedAt("2024-01-01T00:00:00Z");
        result.setPassed(passed);
        result.setMessage("m");
        result.setDurationMs(1L);
        return result;
    }
    
    private static List<PipelineExecution> samplePipelineExecutions() {
        List<PipelineExecution> list = new ArrayList<>();
        long t = 1_700_000_000_000L;
        for (String resType : new String[] {"SKILL", "PROMPT", "CONFIG"}) {
            for (PipelineExecutionStatus status : new PipelineExecutionStatus[] {
                PipelineExecutionStatus.APPROVED, PipelineExecutionStatus.REJECTED}) {
                PipelineExecution execution = new PipelineExecution();
                execution.setExecutionId(UUID.randomUUID().toString());
                execution.setResourceType(resType);
                execution.setResourceName("res-" + resType);
                execution.setNamespaceId("ns1");
                execution.setVersion("1.0");
                execution.setStatus(status);
                execution
                    .setPipeline(Arrays.asList(sampleNode("n1", true), sampleNode("n2", false)));
                execution.setCreateTime(t++);
                execution.setUpdateTime(t++);
                list.add(execution);
            }
        }
        PipelineExecution minimal = new PipelineExecution();
        minimal.setExecutionId(UUID.randomUUID().toString());
        minimal.setResourceType("SKILL");
        minimal.setResourceName("x");
        minimal.setNamespaceId("y");
        minimal.setVersion("v1");
        minimal.setStatus(PipelineExecutionStatus.APPROVED);
        minimal.setPipeline(new ArrayList<>());
        minimal.setCreateTime(t);
        minimal.setUpdateTime(t);
        list.add(minimal);
        return list;
    }
    
    /**
     * Execution record persistence round-trip.
     *
     * <p><b>Validates: Requirements 3.4, 3.5</b></p>
     */
    @Test
    void persistenceRoundTrip() {
        for (PipelineExecution original : samplePipelineExecutions()) {
            repository.save(original);
            
            PipelineExecution foundById = repository.findById(original.getExecutionId());
            assertNotNull(foundById, "findById should return a non-null record");
            assertExecutionEquals(original, foundById);
            
            PipelineExecution foundByResource = repository.findByResource(
                original.getResourceType(), original.getResourceName(),
                original.getNamespaceId(), original.getVersion());
            assertNotNull(foundByResource, "findByResource should return a non-null record");
            assertExecutionEquals(original, foundByResource);
        }
    }
    
    /**
     * listPipelines filter correctness.
     *
     * <p><b>Validates: Requirements 4.1, 4.2, 4.3, 4.4</b></p>
     */
    @Test
    void filterCorrectness() {
        for (PipelineExecution seed : samplePipelineExecutions()) {
            repository.save(seed);
            for (int i = 0; i < 3; i++) {
                PipelineExecution other = new PipelineExecution();
                other.setExecutionId(UUID.randomUUID().toString());
                other.setResourceType(i == 0 ? seed.getResourceType() : "OTHER_TYPE_" + i);
                other.setResourceName(i <= 1 ? seed.getResourceName() : "other_name_" + i);
                other.setNamespaceId(seed.getNamespaceId());
                other.setVersion(seed.getVersion());
                other.setStatus(seed.getStatus());
                other.setPipeline(new ArrayList<>());
                other.setCreateTime(seed.getCreateTime() - i - 1);
                other.setUpdateTime(seed.getUpdateTime());
                repository.save(other);
            }
            
            List<PipelineExecution> results = repository.findByResourceWithPage(
                seed.getResourceType(), seed.getResourceName(),
                seed.getNamespaceId(), seed.getVersion(), 0, 100);
            
            for (PipelineExecution r : results) {
                assertEquals(seed.getResourceType(), r.getResourceType());
                assertEquals(seed.getResourceName(), r.getResourceName());
                assertEquals(seed.getNamespaceId(), r.getNamespaceId());
                assertEquals(seed.getVersion(), r.getVersion());
            }
            jdbcTemplate.execute("DELETE FROM pipeline_execution");
        }
    }
    
    /**
     * count and find filter consistency.
     *
     * <p><b>Validates: Requirements 4.5, 4.6</b></p>
     */
    @Test
    void countAndFindConsistency() {
        for (PipelineExecution seed : samplePipelineExecutions()) {
            repository.save(seed);
            for (int i = 0; i < 4; i++) {
                PipelineExecution other = new PipelineExecution();
                other.setExecutionId(UUID.randomUUID().toString());
                other.setResourceType(seed.getResourceType());
                other.setResourceName(seed.getResourceName());
                other.setNamespaceId(seed.getNamespaceId());
                other.setVersion(i < 2 ? seed.getVersion() : "diff_ver_" + i);
                other.setStatus(seed.getStatus());
                other.setPipeline(new ArrayList<>());
                other.setCreateTime(seed.getCreateTime() - i - 1);
                other.setUpdateTime(seed.getUpdateTime());
                repository.save(other);
            }
            
            int count = repository.countByResource(
                seed.getResourceType(), seed.getResourceName(),
                seed.getNamespaceId(), seed.getVersion());
            List<PipelineExecution> all = repository.findByResourceWithPage(
                seed.getResourceType(), seed.getResourceName(),
                seed.getNamespaceId(), seed.getVersion(), 0, Integer.MAX_VALUE);
            
            assertEquals(count, all.size(),
                "countByResource should equal findByResourceWithPage size with unlimited pagination");
            jdbcTemplate.execute("DELETE FROM pipeline_execution");
        }
    }
    
    private void assertExecutionEquals(PipelineExecution expected, PipelineExecution actual) {
        assertEquals(expected.getExecutionId(), actual.getExecutionId());
        assertEquals(expected.getResourceType(), actual.getResourceType());
        assertEquals(expected.getResourceName(), actual.getResourceName());
        assertEquals(expected.getNamespaceId(), actual.getNamespaceId());
        assertEquals(expected.getVersion(), actual.getVersion());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getCreateTime(), actual.getCreateTime());
        assertEquals(expected.getUpdateTime(), actual.getUpdateTime());
        assertEquals(expected.getPipeline().size(), actual.getPipeline().size());
        for (int i = 0; i < expected.getPipeline().size(); i++) {
            PipelineNodeResult expectedNode = expected.getPipeline().get(i);
            PipelineNodeResult actualNode = actual.getPipeline().get(i);
            assertEquals(expectedNode, actualNode);
        }
    }
}
