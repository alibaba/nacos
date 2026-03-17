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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Property-based test for PipelineExecution persistence round-trip.
 *
 * <p><b>Validates: Requirements 3.4, 3.5</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineExecutionRepositoryPropertyTest {

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
    
    @BeforeContainer
    static void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pipeline_test;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        repository = new PipelineExecutionRepositoryImpl(jdbcTemplate);
    }
    
    @AfterTry
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM pipeline_execution");
    }
    
    /**
     * Property 8: Execution record persistence round-trip.
     *
     * <p>For any PipelineExecution, saving it and then finding by executionId should return
     * an equivalent record. Finding by resource info should also return a matching record.</p>
     *
     * <p><b>Validates: Requirements 3.4, 3.5</b></p>
     */
    @Property(tries = 50)
    void persistenceRoundTrip(@ForAll("pipelineExecutions") PipelineExecution original) {
        repository.save(original);
        
        // Verify findById returns equivalent record
        PipelineExecution foundById = repository.findById(original.getExecutionId());
        assertNotNull(foundById, "findById should return a non-null record");
        assertExecutionEquals(original, foundById);
        
        // Verify findByResource returns matching record
        PipelineExecution foundByResource = repository.findByResource(
                original.getResourceType(), original.getResourceName(),
                original.getNamespaceId(), original.getVersion());
        assertNotNull(foundByResource, "findByResource should return a non-null record");
        assertExecutionEquals(original, foundByResource);
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
    
    @Provide
    Arbitrary<PipelineExecution> pipelineExecutions() {
        Arbitrary<String> executionIds = Arbitraries.create(() -> UUID.randomUUID().toString());
        Arbitrary<String> resourceTypes = Arbitraries.of("SKILL", "PROMPT", "CONFIG");
        Arbitrary<String> resourceNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<String> namespaceIds = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> versions = Arbitraries.strings().alpha().numeric().withChars('.').ofMinLength(1).ofMaxLength(10);
        Arbitrary<PipelineExecutionStatus> statuses = Arbitraries.of(
                PipelineExecutionStatus.APPROVED, PipelineExecutionStatus.REJECTED);
        Arbitrary<List<PipelineNodeResult>> pipelines = pipelineNodeResult().list().ofMinSize(0).ofMaxSize(5);
        Arbitrary<Long> times = Arbitraries.longs().between(1_000_000_000L, 2_000_000_000L);
        
        return Combinators.combine(executionIds, resourceTypes, resourceNames, namespaceIds,
                        versions, statuses, pipelines, times)
                .as((id, resType, resName, nsId, ver, status, pipeline, time) -> {
                    PipelineExecution execution = new PipelineExecution();
                    execution.setExecutionId(id);
                    execution.setResourceType(resType);
                    execution.setResourceName(resName);
                    execution.setNamespaceId(nsId);
                    execution.setVersion(ver);
                    execution.setStatus(status);
                    execution.setPipeline(pipeline != null ? pipeline : new ArrayList<>());
                    execution.setCreateTime(time);
                    execution.setUpdateTime(time);
                    return execution;
                });
    }
    
    private Arbitrary<PipelineNodeResult> pipelineNodeResult() {
        Arbitrary<String> nodeIds = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> executedAts = Arbitraries.strings().alpha().numeric()
                .withChars('-', ':', 'T', 'Z').ofMinLength(10).ofMaxLength(25);
        Arbitrary<Boolean> passedValues = Arbitraries.of(true, false);
        Arbitrary<String> messages = Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                .ofMinLength(0).ofMaxLength(50);
        Arbitrary<Long> durations = Arbitraries.longs().between(0, 1_000_000L);
        
        return Combinators.combine(nodeIds, executedAts, passedValues, messages, durations)
                .as((nodeId, executedAt, passed, message, durationMs) -> {
                    PipelineNodeResult result = new PipelineNodeResult();
                    result.setNodeId(nodeId);
                    result.setExecutedAt(executedAt);
                    result.setPassed(passed);
                    result.setMessage(message);
                    result.setDurationMs(durationMs);
                    return result;
                });
    }
}
