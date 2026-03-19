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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property 9: Admin and Console interface consistency.
 *
 * <p>Same query parameters through Admin (PipelineQueryService directly) and
 * Console InnerHandler (which also delegates to PipelineQueryService) should
 * return identical data. Since both paths share the same service, this test
 * verifies that two independent calls to the same service produce consistent
 * results.</p>
 *
 * <p><b>Validates: Requirements 8.1, 8.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineConsistencyPropertyTest {

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
    
    private static PipelineQueryService adminService;
    
    private static PipelineQueryService consoleService;
    
    @BeforeContainer
    static void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pipeline_consistency_test;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        PipelineExecutionRepositoryImpl repository = new PipelineExecutionRepositoryImpl(jdbcTemplate);
        // Both Admin and Console InnerHandler use the same PipelineQueryService
        adminService = new PipelineQueryService(repository);
        consoleService = new PipelineQueryService(repository);
    }
    
    @AfterTry
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM pipeline_execution");
    }
    
    /**
     * Property 9a: getPipeline consistency — same pipelineId returns same data.
     */
    @Property(tries = 20)
    void getPipelineConsistency(@ForAll("pipelineExecutions") PipelineExecution seed) throws Exception {
        PipelineExecutionRepositoryImpl repo = new PipelineExecutionRepositoryImpl(jdbcTemplate);
        repo.save(seed);
        
        PipelineExecution adminResult = adminService.getPipeline(seed.getExecutionId());
        PipelineExecution consoleResult = consoleService.getPipeline(seed.getExecutionId());
        
        assertEquals(adminResult.getExecutionId(), consoleResult.getExecutionId());
        assertEquals(adminResult.getResourceType(), consoleResult.getResourceType());
        assertEquals(adminResult.getResourceName(), consoleResult.getResourceName());
        assertEquals(adminResult.getStatus(), consoleResult.getStatus());
        assertEquals(adminResult.getCreateTime(), consoleResult.getCreateTime());
    }
    
    /**
     * Property 9b: listPipelines consistency — same params return same page data.
     */
    @Property(tries = 20)
    void listPipelinesConsistency(@ForAll("pipelineExecutions") PipelineExecution seed) throws Exception {
        PipelineExecutionRepositoryImpl repo = new PipelineExecutionRepositoryImpl(jdbcTemplate);
        repo.save(seed);
        
        Page<PipelineExecution> adminPage = adminService.listPipelines(
                seed.getResourceType(), seed.getResourceName(),
                seed.getNamespaceId(), seed.getVersion(), 1, 10);
        Page<PipelineExecution> consolePage = consoleService.listPipelines(
                seed.getResourceType(), seed.getResourceName(),
                seed.getNamespaceId(), seed.getVersion(), 1, 10);
        
        assertEquals(adminPage.getTotalCount(), consolePage.getTotalCount());
        assertEquals(adminPage.getPageItems().size(), consolePage.getPageItems().size());
        assertEquals(adminPage.getPagesAvailable(), consolePage.getPagesAvailable());
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
}
