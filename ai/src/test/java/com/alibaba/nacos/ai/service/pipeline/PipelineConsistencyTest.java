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
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Admin and Console interface consistency.
 *
 * <p><b>Validates: Requirements 8.1, 8.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineConsistencyTest {
    
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
    
    @BeforeAll
    static void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pipeline_consistency_test;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        PipelineExecutionRepositoryImpl repository =
            new PipelineExecutionRepositoryImpl(jdbcTemplate);
        adminService = new PipelineQueryService(repository);
        consoleService = new PipelineQueryService(repository);
    }
    
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM pipeline_execution");
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
                execution.setResourceName("r-" + resType);
                execution.setNamespaceId("ns");
                execution.setVersion("v1");
                execution.setStatus(status);
                execution.setPipeline(new ArrayList<>());
                execution.setCreateTime(t++);
                execution.setUpdateTime(t++);
                list.add(execution);
            }
        }
        return list;
    }
    
    /**
     * getPipeline consistency — same pipelineId returns same data.
     */
    @Test
    void getPipelineConsistency() throws Exception {
        for (PipelineExecution seed : samplePipelineExecutions()) {
            PipelineExecutionRepositoryImpl repo =
                new PipelineExecutionRepositoryImpl(jdbcTemplate);
            repo.save(seed);
            
            PipelineExecution adminResult = adminService.getPipeline(seed.getExecutionId());
            PipelineExecution consoleResult = consoleService.getPipeline(seed.getExecutionId());
            
            assertEquals(adminResult.getExecutionId(), consoleResult.getExecutionId());
            assertEquals(adminResult.getResourceType(), consoleResult.getResourceType());
            assertEquals(adminResult.getResourceName(), consoleResult.getResourceName());
            assertEquals(adminResult.getStatus(), consoleResult.getStatus());
            assertEquals(adminResult.getCreateTime(), consoleResult.getCreateTime());
        }
    }
    
    /**
     * listPipelines consistency — same params return same page data.
     */
    @Test
    void listPipelinesConsistency() throws Exception {
        for (PipelineExecution seed : samplePipelineExecutions()) {
            PipelineExecutionRepositoryImpl repo =
                new PipelineExecutionRepositoryImpl(jdbcTemplate);
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
    }
}
