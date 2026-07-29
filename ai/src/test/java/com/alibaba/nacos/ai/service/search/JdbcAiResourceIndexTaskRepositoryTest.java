/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.model.search.AiResourceIndexTask;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JdbcAiResourceIndexTaskRepository}.
 *
 * @author nacos
 */
class JdbcAiResourceIndexTaskRepositoryTest {
    
    private JdbcTemplate jdbcTemplate;
    
    private JdbcAiResourceIndexTaskRepository repository;
    
    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ard_index_task_repository;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS ai_resource_search_index_task");
        jdbcTemplate.execute("CREATE TABLE ai_resource_search_index_task ("
            + "task_key varchar(64) PRIMARY KEY, namespace_id varchar(128) NOT NULL,"
            + "resource_type varchar(32) NOT NULL, resource_name varchar(256) NOT NULL,"
            + "status varchar(32) NOT NULL, attempt_count int NOT NULL,"
            + "revision bigint NOT NULL, next_retry_time timestamp NOT NULL,"
            + "lease_until timestamp, last_error varchar(2000),"
            + "gmt_create timestamp NOT NULL, gmt_modified timestamp NOT NULL)");
        repository = new JdbcAiResourceIndexTaskRepository(jdbcTemplate);
    }
    
    @Test
    void newerScheduleShouldSurviveCompletionOfClaimedRevision() {
        repository.schedule("public", "skill", "avatar");
        AiResourceIndexTask firstRevision = repository.findDueTasks(10).get(0);
        assertTrue(repository.claim(firstRevision,
            new Timestamp(System.currentTimeMillis() + 60_000L)));
        
        repository.schedule("public", "skill", "avatar");
        repository.complete(firstRevision);
        
        List<AiResourceIndexTask> tasks = repository.findDueTasks(10);
        assertEquals(1, tasks.size());
        assertEquals(2L, tasks.get(0).getRevision());
        assertEquals(JdbcAiResourceIndexTaskRepository.STATUS_PENDING, tasks.get(0).getStatus());
    }
    
    @Test
    void findDueTasksShouldApplyRequestedRowBound() {
        repository.schedule("public", "skill", "one");
        repository.schedule("public", "skill", "two");
        repository.schedule("public", "skill", "three");
        
        assertEquals(2, repository.findDueTasks(2).size());
    }
}
