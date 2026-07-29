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
import com.alibaba.nacos.ai.model.search.AiResourceIndexTaskPayload;
import com.alibaba.nacos.ai.model.search.AiResourceIndexTaskResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JdbcAiResourceIndexTaskRepository}.
 *
 * @author nacos
 */
class JdbcAiResourceIndexTaskRepositoryTest {
    
    private static final long INITIAL_EPOCH_MILLIS = 1_800_000_000_000L;
    
    private JdbcTemplate jdbcTemplate;
    
    private JdbcAiResourceIndexTaskRepository repository;
    
    private long currentEpochMillis;
    
    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ai_resource_task_repository;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS ai_resource_task");
        jdbcTemplate.execute("CREATE TABLE ai_resource_task ("
            + "task_key varchar(64) PRIMARY KEY, namespace_id varchar(128) NOT NULL,"
            + "task_type varchar(64) NOT NULL, task_stage varchar(32) NOT NULL,"
            + "status varchar(16) NOT NULL, task_payload clob NOT NULL, task_result clob,"
            + "retry_count int NOT NULL, revision bigint NOT NULL,"
            + "next_execute_at bigint NOT NULL,"
            + "lease_expire_at bigint, last_error varchar(2000),"
            + "gmt_create timestamp NOT NULL, gmt_modified timestamp NOT NULL)");
        setCurrentEpochMillis(INITIAL_EPOCH_MILLIS);
    }
    
    @Test
    void newerScheduleShouldWaitForClaimedRevisionAndThenRunBaseStage() {
        repository.schedule("public", "skill", "avatar", true);
        AiResourceIndexTask firstRevision = repository.findDueTasks(10).get(0);
        assertTrue(repository.claim(firstRevision, 60_000L));
        
        repository.schedule("public", "skill", "avatar", true);
        assertFalse(repository.complete(firstRevision, "old-fingerprint"));
        assertFalse(repository.retry(firstRevision, 30_000L, "old failure"));
        assertTrue(repository.findDueTasks(10).isEmpty());
        repository.releaseSuperseded(firstRevision);
        
        List<AiResourceIndexTask> tasks = repository.findDueTasks(10);
        assertEquals(1, tasks.size());
        assertEquals(3L, tasks.get(0).getRevision());
        assertEquals(AiResourceIndexTask.STAGE_BASE_INDEX, tasks.get(0).getTaskStage());
        assertEquals(JdbcAiResourceIndexTaskRepository.STATUS_PENDING, tasks.get(0).getStatus());
    }
    
    @Test
    void baseTaskShouldAdvanceAndRetainEnhancementCheckpoint() {
        repository.schedule("public", "skill", "avatar", true);
        AiResourceIndexTask baseTask = repository.findDueTasks(10).get(0);
        assertTrue(baseTask.isEnhancementRequested());
        assertTrue(repository.claim(baseTask, 60_000L));
        assertEquals(2L, baseTask.getRevision());
        assertTrue(repository.advanceToEnhancement(baseTask));
        
        AiResourceIndexTask enhancementTask = repository.findDueTasks(10).get(0);
        assertEquals(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT,
            enhancementTask.getTaskStage());
        assertTrue(repository.claim(enhancementTask, 60_000L));
        assertTrue(repository.complete(enhancementTask, "fingerprint-v1"));
        assertTrue(repository.findDueTasks(10).isEmpty());
        
        Map<String, Object> checkpoint = jdbcTemplate.queryForMap(
            "SELECT task_type, task_stage, status FROM ai_resource_task");
        AiResourceIndexTaskPayload payload = JacksonUtils.toObj(
            jdbcTemplate.queryForObject("SELECT task_payload FROM ai_resource_task", String.class),
            AiResourceIndexTaskPayload.class);
        AiResourceIndexTaskResult result = JacksonUtils.toObj(
            jdbcTemplate.queryForObject("SELECT task_result FROM ai_resource_task", String.class),
            AiResourceIndexTaskResult.class);
        assertEquals(AiResourceIndexTask.TASK_TYPE, checkpoint.get("TASK_TYPE"));
        assertEquals(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT,
            checkpoint.get("TASK_STAGE"));
        assertEquals(JdbcAiResourceIndexTaskRepository.STATUS_COMPLETED,
            checkpoint.get("STATUS"));
        assertEquals(AiResourceIndexTaskPayload.CURRENT_SCHEMA_VERSION,
            payload.getSchemaVersion());
        assertEquals("skill", payload.getSubject().getResourceType());
        assertEquals("avatar", payload.getSubject().getResourceName());
        assertTrue(payload.getOptions().isEnhancementRequested());
        assertEquals(AiResourceIndexTaskResult.CURRENT_SCHEMA_VERSION,
            result.getSchemaVersion());
        assertEquals("fingerprint-v1", result.getEnhancementFingerprint());
    }
    
    @Test
    void claimedEnhancementTaskShouldRenewItsLease() {
        AiResourceIndexTask task = scheduleEnhancementTask();
        assertTrue(repository.claim(task, 60_000L));
        assertEquals(currentEpochMillis + 60_000L, jdbcTemplate.queryForObject(
            "SELECT lease_expire_at FROM ai_resource_task", Long.class));
        
        advanceTime(20_000L);
        assertTrue(repository.renewLease(task, 120_000L));
        assertEquals(currentEpochMillis + 120_000L, jdbcTemplate.queryForObject(
            "SELECT lease_expire_at FROM ai_resource_task", Long.class));
    }
    
    @Test
    void expiredLeaseShouldBeClaimedByANewerRevision() {
        AiResourceIndexTask expired = scheduleEnhancementTask();
        assertTrue(repository.claim(expired, 1_000L));
        assertTrue(repository.findDueTasks(10).isEmpty());
        advanceTime(1_001L);
        
        AiResourceIndexTask takeover = repository.findDueTasks(10).get(0);
        assertTrue(repository.claim(takeover, 60_000L));
        
        assertEquals(expired.getRevision() + 1, takeover.getRevision());
        assertFalse(repository.complete(expired, "stale-fingerprint"));
        assertTrue(repository.complete(takeover, "fingerprint-v1"));
    }
    
    @Test
    void failedEnhancementShouldBackOffUsingEpochMillis() {
        AiResourceIndexTask task = scheduleEnhancementTask();
        assertTrue(repository.claim(task, 60_000L));
        
        assertTrue(repository.retry(task, 1_800_000L, "llm unavailable"));
        assertEquals(currentEpochMillis + 1_800_000L, jdbcTemplate.queryForObject(
            "SELECT next_execute_at FROM ai_resource_task", Long.class));
        assertTrue(repository.findDueTasks(10).isEmpty());
        advanceTime(1_799_999L);
        assertTrue(repository.findDueTasks(10).isEmpty());
        advanceTime(1L);
        
        AiResourceIndexTask retry = repository.findDueTasks(10).get(0);
        assertEquals(AiResourceIndexTask.STAGE_LLM_ENHANCEMENT, retry.getTaskStage());
        assertEquals(JdbcAiResourceIndexTaskRepository.STATUS_PENDING, retry.getStatus());
        assertEquals(1, retry.getRetryCount());
    }
    
    @Test
    void completedEnhancementShouldWaitForResourceChange() {
        AiResourceIndexTask task = scheduleEnhancementTask();
        assertTrue(repository.claim(task, 60_000L));
        assertTrue(repository.complete(task, "fingerprint-v1"));
        
        assertTrue(repository.findDueTasks(10).isEmpty());
        assertEquals("fingerprint-v1", completedEnhancementFingerprint());
        
        repository.schedule("public", "skill", "avatar", true);
        AiResourceIndexTask baseTask = repository.findDueTasks(10).get(0);
        assertEquals(AiResourceIndexTask.STAGE_BASE_INDEX, baseTask.getTaskStage());
        assertTrue(repository.claim(baseTask, 60_000L));
        assertTrue(repository.advanceToEnhancement(baseTask));
        
        AiResourceIndexTask enhancementTask = repository.findDueTasks(10).get(0);
        assertTrue(repository.claim(enhancementTask, 60_000L));
        assertTrue(repository.complete(enhancementTask, "fingerprint-v2"));
        assertEquals("fingerprint-v2", completedEnhancementFingerprint());
    }
    
    @Test
    void reconciliationTaskShouldRequestEnabledEnhancement() {
        repository.scheduleReconciliation("public", "skill", "avatar", true);
        
        AiResourceIndexTask task = repository.findDueTasks(10).get(0);
        
        assertTrue(task.isEnhancementRequested());
    }
    
    @Test
    void reconciliationTaskShouldNotRequestDisabledEnhancement() {
        repository.scheduleReconciliation("public", "skill", "avatar", false);
        
        AiResourceIndexTask task = repository.findDueTasks(10).get(0);
        
        assertFalse(task.isEnhancementRequested());
    }
    
    @Test
    void reconciliationShouldPreservePendingLifecycleEnhancementRequest() {
        repository.schedule("public", "skill", "avatar", true);
        
        repository.scheduleReconciliation("public", "skill", "avatar", false);
        
        assertTrue(repository.findDueTasks(10).get(0).isEnhancementRequested());
    }
    
    @Test
    void reconciliationShouldPreservePendingLifecycleWithoutEnhancement() {
        repository.schedule("public", "skill", "avatar", false);
        
        repository.scheduleReconciliation("public", "skill", "avatar", true);
        
        assertFalse(repository.findDueTasks(10).get(0).isEnhancementRequested());
    }
    
    @Test
    void inconsistentCompletedIndexShouldRequestEnabledEnhancement() {
        AiResourceIndexTask enhancementTask = scheduleEnhancementTask();
        assertTrue(repository.claim(enhancementTask, 60_000L));
        assertTrue(repository.complete(enhancementTask, "fingerprint-v1"));
        
        repository.scheduleReconciliation("public", "skill", "avatar", true);
        
        AiResourceIndexTask reconciliationTask = repository.findDueTasks(10).get(0);
        assertTrue(reconciliationTask.isEnhancementRequested());
    }
    
    @Test
    void findDueTasksShouldApplyRequestedRowBound() {
        repository.schedule("public", "skill", "one", false);
        repository.schedule("public", "skill", "two", false);
        repository.schedule("public", "skill", "three", false);
        
        assertEquals(2, repository.findDueTasks(2).size());
    }
    
    @Test
    void findDueTasksShouldOnlyReturnSearchIndexTasks() {
        jdbcTemplate.update("INSERT INTO ai_resource_task "
            + "(task_key, namespace_id, task_type, task_stage, status, task_payload, "
            + "task_result, retry_count, revision, next_execute_at, lease_expire_at, "
            + "last_error, gmt_create, gmt_modified) VALUES (?, ?, ?, ?, ?, ?, NULL, 0, 1, "
            + "?, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "other-task", "public", "metadata_extract", "extract", "pending", "{}",
            currentEpochMillis);
        repository.schedule("public", "skill", "avatar", false);
        
        List<AiResourceIndexTask> tasks = repository.findDueTasks(10);
        
        assertEquals(1, tasks.size());
        assertEquals(AiResourceIndexTask.TASK_TYPE, tasks.get(0).getTaskType());
    }
    
    private AiResourceIndexTask scheduleEnhancementTask() {
        repository.schedule("public", "skill", "avatar", true);
        AiResourceIndexTask baseTask = repository.findDueTasks(10).get(0);
        assertTrue(repository.claim(baseTask, 60_000L));
        assertTrue(repository.advanceToEnhancement(baseTask));
        return repository.findDueTasks(10).get(0);
    }
    
    private void advanceTime(long millis) {
        setCurrentEpochMillis(currentEpochMillis + millis);
    }
    
    private void setCurrentEpochMillis(long millis) {
        currentEpochMillis = millis;
        Clock clock = Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
        repository = new JdbcAiResourceIndexTaskRepository(jdbcTemplate, clock);
    }
    
    private String completedEnhancementFingerprint() {
        String resultJson = jdbcTemplate.queryForObject(
            "SELECT task_result FROM ai_resource_task", String.class);
        return JacksonUtils.toObj(resultJson, AiResourceIndexTaskResult.class)
            .getEnhancementFingerprint();
    }
}
