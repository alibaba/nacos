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
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for PipelineQueryService.
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineQueryServiceTest {
    
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
    
    @BeforeAll
    static void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pipeline_query_test;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(CREATE_TABLE_SQL);
        repository = new PipelineExecutionRepositoryImpl(jdbcTemplate);
        service = new PipelineQueryService(repository);
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
                execution.setResourceName("name-" + resType);
                execution.setNamespaceId("nsx");
                execution.setVersion("1.2");
                execution.setStatus(status);
                execution.setPipeline(new ArrayList<>());
                execution.setCreateTime(t++);
                execution.setUpdateTime(t++);
                list.add(execution);
            }
        }
        return list;
    }
    
    private static String[] randomIds() {
        return new String[] {
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "00000000-0000-0000-0000-000000000001"
        };
    }
    
    private static List<PaginationInput> paginationInputs() {
        List<PaginationInput> list = new ArrayList<>();
        for (int total : new int[] {1, 5, 10, 15}) {
            for (int pageNo : new int[] {1, 2, 3}) {
                for (int pageSize : new int[] {1, 5, 10}) {
                    if (pageNo <= (total + pageSize - 1) / pageSize) {
                        list.add(new PaginationInput(total, pageNo, pageSize));
                    }
                }
            }
        }
        return list;
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
    
    /**
     * getPipeline round-trip — saved record should be retrievable.
     */
    @Test
    void getPipelineRoundTrip() throws Exception {
        for (PipelineExecution original : samplePipelineExecutions()) {
            repository.save(original);
            PipelineExecution found = service.getPipeline(original.getExecutionId());
            assertNotNull(found);
            assertEquals(original.getExecutionId(), found.getExecutionId());
            assertEquals(original.getResourceType(), found.getResourceType());
            assertEquals(original.getResourceName(), found.getResourceName());
            assertEquals(original.getStatus(), found.getStatus());
        }
    }
    
    /**
     * getPipeline with non-existent ID should throw 404.
     */
    @Test
    void getPipelineNotFoundThrows404() {
        for (String randomId : randomIds()) {
            NacosApiException ex =
                assertThrows(NacosApiException.class, () -> service.getPipeline(randomId));
            assertEquals(404, ex.getErrCode());
        }
    }
    
    /**
     * listPipelines pagination correctness.
     */
    @Test
    void listPipelinesPaginationCorrectness() throws Exception {
        for (PaginationInput input : paginationInputs()) {
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
            jdbcTemplate.execute("DELETE FROM pipeline_execution");
        }
    }
    
    /**
     * listPipelines sort correctness — createTime descending.
     */
    @Test
    void listPipelinesSortCorrectness() throws Exception {
        for (int recordCount : new int[] {2, 5, 10}) {
            String resourceType = "SORT_TYPE";
            for (int i = 0; i < recordCount; i++) {
                PipelineExecution exec = createExecution(resourceType, "res", "ns", "v1",
                    1_000_000_000L + (long) i * 1000L);
                repository.save(exec);
            }
            
            Page<PipelineExecution> page =
                service.listPipelines(resourceType, "res", "ns", "v1", 1, 100);
            List<PipelineExecution> items = page.getPageItems();
            
            for (int i = 1; i < items.size(); i++) {
                assertTrue(items.get(i - 1).getCreateTime() >= items.get(i).getCreateTime(),
                    "Results should be ordered by createTime DESC");
            }
        }
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
    }
}
