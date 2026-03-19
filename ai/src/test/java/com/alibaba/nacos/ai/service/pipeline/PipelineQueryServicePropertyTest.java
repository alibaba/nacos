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

package com.alibaba.nacos.ai.service.pipeline;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepositoryImpl;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for PipelineQueryService.
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineQueryServicePropertyTest {

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
    
    private static PipelineQueryService service;
    
    @BeforeContainer
    static void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pipeline_query_test;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        repository = new PipelineExecutionRepositoryImpl(jdbcTemplate);
        service = new PipelineQueryService(repository);
    }
    
    @AfterTry
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM pipeline_execution");
    }
    
    /**
     * Property 1: getPipeline round-trip — saved record should be retrievable.
     *
     * <p><b>Validates: Requirement 1.1</b></p>
     */
    @Property(tries = 30)
    void getPipelineRoundTrip(@ForAll("pipelineExecutions") PipelineExecution original) throws Exception {
        repository.save(original);
        PipelineExecution found = service.getPipeline(original.getExecutionId());
        assertNotNull(found);
        assertEquals(original.getExecutionId(), found.getExecutionId());
        assertEquals(original.getResourceType(), found.getResourceType());
        assertEquals(original.getResourceName(), found.getResourceName());
        assertEquals(original.getStatus(), found.getStatus());
    }
    
    /**
     * Property 2: getPipeline with non-existent ID should throw 404.
     *
     * <p><b>Validates: Requirement 1.2</b></p>
     */
    @Property(tries = 30)
    void getPipelineNotFoundThrows404(@ForAll("randomIds") String randomId) {
        NacosApiException ex = assertThrows(NacosApiException.class, () -> service.getPipeline(randomId));
        assertEquals(404, ex.getErrCode());
    }
    
    /**
     * Property 3: listPipelines pagination correctness.
     *
     * <p><b>Validates: Requirements 2.2, 2.3, 2.4</b></p>
     */
    @Property(tries = 20)
    void listPipelinesPaginationCorrectness(@ForAll("paginationInputs") PaginationInput input) throws Exception {
        String resourceType = "TEST_TYPE";
        for (int i = 0; i < input.totalRecords; i++) {
            PipelineExecution exec = createExecution(resourceType, "res", "ns", "v1",
                    1_000_000_000L + i);
            repository.save(exec);
        }
        
        Page<PipelineExecution> page = service.listPipelines(resourceType, "res", "ns", "v1",
                input.pageNo, input.pageSize);
        
        assertTrue(page.getPageItems().size() <= input.pageSize,
                "pageItems.size() should be <= pageSize");
        assertEquals(input.totalRecords, page.getTotalCount(),
                "totalCount should match total records");
        int expectedPages = (input.totalRecords + input.pageSize - 1) / input.pageSize;
        assertEquals(expectedPages, page.getPagesAvailable(),
                "pagesAvailable should be ceil(totalCount / pageSize)");
    }
    
    /**
     * Property 4: listPipelines sort correctness — createTime descending.
     *
     * <p><b>Validates: Requirement 2.1</b></p>
     */
    @Property(tries = 20)
    void listPipelinesSortCorrectness(@ForAll("sortInputCounts") int recordCount) throws Exception {
        String resourceType = "SORT_TYPE";
        for (int i = 0; i < recordCount; i++) {
            PipelineExecution exec = createExecution(resourceType, "res", "ns", "v1",
                    1_000_000_000L + (long) (Math.random() * 1_000_000));
            repository.save(exec);
        }
        
        Page<PipelineExecution> page = service.listPipelines(resourceType, "res", "ns", "v1", 1, 100);
        List<PipelineExecution> items = page.getPageItems();
        
        for (int i = 1; i < items.size(); i++) {
            assertTrue(items.get(i - 1).getCreateTime() >= items.get(i).getCreateTime(),
                    "Results should be ordered by createTime DESC");
        }
    }
    
    private PipelineExecution createExecution(String resourceType, String resourceName,
            String namespaceId, String version, long createTime) {
        PipelineExecution exec = new PipelineExecution();
        exec.setExecutionId(UUID.randomUUID().toString());
        exec.setResourceType(resourceType);
        exec.setResourceName(resourceName);
        exec.setNamespaceId(namespaceId);
        exec.setVersion(version);
        exec.setStatus(PipelineExecutionStatus.APPROVED);
        exec.setPipeline(new ArrayList<>());
        exec.setCreateTime(createTime);
        exec.setUpdateTime(createTime);
        return exec;
    }
    
    @Provide
    Arbitrary<PipelineExecution> pipelineExecutions() {
        Arbitrary<String> executionIds = Arbitraries.create(() -> UUID.randomUUID().toString());
        Arbitrary<String> resourceTypes = Arbitraries.of("SKILL", "PROMPT", "CONFIG");
        Arbitrary<String> resourceNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> namespaceIds = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(10);
        Arbitrary<String> versions = Arbitraries.strings().alpha().numeric().withChars('.').ofMinLength(1).ofMaxLength(8);
        Arbitrary<PipelineExecutionStatus> statuses = Arbitraries.of(
                PipelineExecutionStatus.APPROVED, PipelineExecutionStatus.REJECTED);
        Arbitrary<Long> times = Arbitraries.longs().between(1_000_000_000L, 2_000_000_000L);
        
        return Combinators.combine(executionIds, resourceTypes, resourceNames, namespaceIds,
                        versions, statuses, times)
                .as((id, resType, resName, nsId, ver, status, time) -> {
                    PipelineExecution execution = new PipelineExecution();
                    execution.setExecutionId(id);
                    execution.setResourceType(resType);
                    execution.setResourceName(resName);
                    execution.setNamespaceId(nsId);
                    execution.setVersion(ver);
                    execution.setStatus(status);
                    execution.setPipeline(new ArrayList<>());
                    execution.setCreateTime(time);
                    execution.setUpdateTime(time);
                    return execution;
                });
    }
    
    @Provide
    Arbitrary<String> randomIds() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
    
    @Provide
    Arbitrary<PaginationInput> paginationInputs() {
        Arbitrary<Integer> totalRecords = Arbitraries.integers().between(1, 15);
        Arbitrary<Integer> pageNos = Arbitraries.integers().between(1, 3);
        Arbitrary<Integer> pageSizes = Arbitraries.integers().between(1, 10);
        return Combinators.combine(totalRecords, pageNos, pageSizes).as(PaginationInput::new);
    }
    
    @Provide
    Arbitrary<Integer> sortInputCounts() {
        return Arbitraries.integers().between(2, 10);
    }
    
    static class PaginationInput {
        final int totalRecords;
        final int pageNo;
        final int pageSize;
        
        PaginationInput(int totalRecords, int pageNo, int pageSize) {
            this.totalRecords = totalRecords;
            this.pageNo = pageNo;
            this.pageSize = pageSize;
        }
        
        @Override
        public String toString() {
            return "PaginationInput{totalRecords=" + totalRecords + ", pageNo=" + pageNo
                    + ", pageSize=" + pageSize + "}";
        }
    }
}
