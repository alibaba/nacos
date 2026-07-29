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

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.model.search.AiResourceIndexTask;
import com.alibaba.nacos.ai.model.search.AiResourceIndexTaskPayload;
import com.alibaba.nacos.ai.model.search.AiResourceIndexTaskResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * JDBC repository for durable, coalesced AI resource index tasks.
 *
 * @author nacos
 */
@Repository
@ConditionalOnAiResourceSearchEnabled
public class JdbcAiResourceIndexTaskRepository implements AiResourceIndexTaskRepository {
    
    static final String STATUS_PENDING = "pending";
    
    static final String STATUS_PROCESSING = "processing";
    
    static final String STATUS_COMPLETED = "completed";
    
    private static final int MAX_ERROR_LENGTH = 2000;
    
    private static final RowMapper<AiResourceIndexTask> ROW_MAPPER =
        new AiResourceIndexTaskRowMapper();
    
    private final JdbcTemplate injectedJdbcTemplate;
    
    private final Clock clock;
    
    public JdbcAiResourceIndexTaskRepository() {
        this.injectedJdbcTemplate = null;
        this.clock = Clock.systemUTC();
    }
    
    public JdbcAiResourceIndexTaskRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }
    
    JdbcAiResourceIndexTaskRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.injectedJdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }
    
    @Override
    public void schedule(String namespaceId, String resourceType, String resourceName,
        boolean enhancementRequested) {
        String taskKey = taskKey(namespaceId, resourceType, resourceName);
        String taskPayload = taskPayload(resourceType, resourceName, enhancementRequested);
        long nowEpochMillis = clock.millis();
        int updated = updateExistingLifecycleTask(taskKey, namespaceId, taskPayload,
            nowEpochMillis);
        if (updated > 0) {
            return;
        }
        try {
            insertTask(taskKey, namespaceId, taskPayload, nowEpochMillis);
        } catch (DuplicateKeyException ignored) {
            updateExistingLifecycleTask(taskKey, namespaceId, taskPayload, clock.millis());
        }
    }
    
    @Override
    public void scheduleReconciliation(String namespaceId, String resourceType,
        String resourceName, boolean enhancementRequested) {
        String taskKey = taskKey(namespaceId, resourceType, resourceName);
        String taskPayload = taskPayload(resourceType, resourceName, enhancementRequested);
        long nowEpochMillis = clock.millis();
        int updated = updateExistingReconciliationTask(taskKey, namespaceId, taskPayload,
            nowEpochMillis);
        if (updated > 0) {
            return;
        }
        try {
            insertTask(taskKey, namespaceId, taskPayload, nowEpochMillis);
        } catch (DuplicateKeyException ignored) {
            updateExistingReconciliationTask(taskKey, namespaceId, taskPayload, clock.millis());
        }
    }
    
    @Override
    public List<AiResourceIndexTask> findDueTasks(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        long nowEpochMillis = clock.millis();
        String sql = "SELECT task_key, namespace_id, task_type, task_stage, status, task_payload, "
            + "retry_count, revision FROM ai_resource_task WHERE task_type=? AND "
            + "((status=? AND next_execute_at<=? "
            + "AND (lease_expire_at IS NULL OR lease_expire_at<?)) OR "
            + "(status=? AND lease_expire_at<?)) "
            + "ORDER BY CASE WHEN task_stage=? THEN 0 ELSE 1 END, next_execute_at, gmt_modified";
        return getJdbcTemplate().query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, AiResourceIndexTask.TASK_TYPE);
            statement.setString(2, STATUS_PENDING);
            statement.setLong(3, nowEpochMillis);
            statement.setLong(4, nowEpochMillis);
            statement.setString(5, STATUS_PROCESSING);
            statement.setLong(6, nowEpochMillis);
            statement.setString(7, AiResourceIndexTask.STAGE_BASE_INDEX);
            statement.setMaxRows(limit);
            return statement;
        }, ROW_MAPPER);
    }
    
    @Override
    public boolean claim(AiResourceIndexTask task, long leaseDurationMillis) {
        long nowEpochMillis = clock.millis();
        long leaseExpireAt = nowEpochMillis + leaseDurationMillis;
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET status=?, "
            + "revision=revision+1, lease_expire_at=?, gmt_modified=CURRENT_TIMESTAMP "
            + "WHERE task_key=? AND task_type=? AND revision=? AND task_stage=? "
            + "AND ((status=? AND next_execute_at<=? "
            + "AND (lease_expire_at IS NULL OR lease_expire_at<?)) OR "
            + "(status=? AND lease_expire_at<?))",
            STATUS_PROCESSING, leaseExpireAt, task.getTaskKey(), AiResourceIndexTask.TASK_TYPE,
            task.getRevision(), task.getTaskStage(), STATUS_PENDING, nowEpochMillis,
            nowEpochMillis, STATUS_PROCESSING, nowEpochMillis);
        if (updated == 1) {
            task.setRevision(task.getRevision() + 1);
            task.setStatus(STATUS_PROCESSING);
        }
        return updated == 1;
    }
    
    @Override
    public boolean renewLease(AiResourceIndexTask task, long leaseDurationMillis) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET "
            + "lease_expire_at=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_type=? AND task_stage=? AND status=?",
            clock.millis() + leaseDurationMillis, task.getTaskKey(), task.getRevision(),
            AiResourceIndexTask.TASK_TYPE, task.getTaskStage(), STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean advanceToEnhancement(AiResourceIndexTask task) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET "
            + "task_stage=?, status=?, task_result=NULL, retry_count=0, "
            + "next_execute_at=?, lease_expire_at=NULL, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_type=? AND task_stage=? AND status=?",
            AiResourceIndexTask.STAGE_LLM_ENHANCEMENT, STATUS_PENDING, clock.millis(),
            task.getTaskKey(), task.getRevision(), AiResourceIndexTask.TASK_TYPE,
            task.getTaskStage(), STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean complete(AiResourceIndexTask task, String enhancementFingerprint) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET status=?, "
            + "task_result=?, retry_count=0, "
            + "lease_expire_at=NULL, last_error=NULL, gmt_modified=CURRENT_TIMESTAMP "
            + "WHERE task_key=? AND task_type=? AND revision=? AND task_stage=? AND status=?",
            STATUS_COMPLETED, taskResult(enhancementFingerprint), task.getTaskKey(),
            AiResourceIndexTask.TASK_TYPE, task.getRevision(), task.getTaskStage(),
            STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean remove(AiResourceIndexTask task) {
        int updated = getJdbcTemplate().update(
            "DELETE FROM ai_resource_task WHERE task_key=? AND task_type=? AND revision=? "
                + "AND task_stage=? AND status=?",
            task.getTaskKey(), AiResourceIndexTask.TASK_TYPE, task.getRevision(),
            task.getTaskStage(), STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean retry(AiResourceIndexTask task, long retryDelayMillis, String lastError) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET status=?, "
            + "retry_count=retry_count+1, next_execute_at=?, lease_expire_at=NULL, "
            + "last_error=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_type=? AND task_stage=? AND status=?",
            STATUS_PENDING, clock.millis() + retryDelayMillis, truncate(lastError),
            task.getTaskKey(), task.getRevision(), AiResourceIndexTask.TASK_TYPE,
            task.getTaskStage(), STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public void releaseSuperseded(AiResourceIndexTask task) {
        getJdbcTemplate().update("UPDATE ai_resource_task SET lease_expire_at=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=? "
            + "AND revision<>? AND status=?", task.getTaskKey(), AiResourceIndexTask.TASK_TYPE,
            task.getRevision(), STATUS_PENDING);
    }
    
    private int updateExistingLifecycleTask(String taskKey, String namespaceId,
        String taskPayload, long nowEpochMillis) {
        return getJdbcTemplate().update("UPDATE ai_resource_task SET namespace_id=?, "
            + "task_payload=?, task_stage=?, "
            + "lease_expire_at=CASE WHEN status=? THEN lease_expire_at ELSE NULL END, status=?, "
            + "task_result=NULL, retry_count=0, revision=revision+1, "
            + "next_execute_at=?, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=?", namespaceId,
            taskPayload, AiResourceIndexTask.STAGE_BASE_INDEX, STATUS_PROCESSING, STATUS_PENDING,
            nowEpochMillis, taskKey, AiResourceIndexTask.TASK_TYPE);
    }
    
    private int updateExistingReconciliationTask(String taskKey, String namespaceId,
        String taskPayload, long nowEpochMillis) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET namespace_id=?, "
            + "task_stage=?, "
            + "lease_expire_at=CASE WHEN status=? THEN lease_expire_at ELSE NULL END, status=?, "
            + "task_result=NULL, retry_count=0, revision=revision+1, "
            + "next_execute_at=?, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=? "
            + "AND status IN (?, ?)", namespaceId, AiResourceIndexTask.STAGE_BASE_INDEX,
            STATUS_PROCESSING, STATUS_PENDING, nowEpochMillis, taskKey,
            AiResourceIndexTask.TASK_TYPE, STATUS_PENDING, STATUS_PROCESSING);
        if (updated > 0) {
            return updated;
        }
        return getJdbcTemplate().update("UPDATE ai_resource_task SET namespace_id=?, "
            + "task_payload=?, task_stage=?, status=?, lease_expire_at=NULL, task_result=NULL, "
            + "retry_count=0, revision=revision+1, next_execute_at=?, "
            + "last_error=NULL, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=? "
            + "AND status=?", namespaceId, taskPayload, AiResourceIndexTask.STAGE_BASE_INDEX,
            STATUS_PENDING, nowEpochMillis, taskKey, AiResourceIndexTask.TASK_TYPE,
            STATUS_COMPLETED);
    }
    
    private void insertTask(String taskKey, String namespaceId, String taskPayload,
        long nowEpochMillis) {
        getJdbcTemplate().update("INSERT INTO ai_resource_task "
            + "(task_key, namespace_id, task_type, task_stage, status, task_payload, task_result, "
            + "retry_count, revision, next_execute_at, lease_expire_at, last_error, gmt_create, "
            + "gmt_modified) VALUES (?, ?, ?, ?, ?, ?, NULL, 0, 1, ?, NULL, NULL, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", taskKey, namespaceId,
            AiResourceIndexTask.TASK_TYPE, AiResourceIndexTask.STAGE_BASE_INDEX, STATUS_PENDING,
            taskPayload, nowEpochMillis);
    }
    
    private String taskKey(String namespaceId, String resourceType, String resourceName) {
        String identity = AiResourceIndexTask.TASK_TYPE + '\n' + String.valueOf(namespaceId) + '\n'
            + resourceType + '\n' + resourceName;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
    
    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
    
    private static String taskPayload(String resourceType, String resourceName,
        boolean enhancementRequested) {
        return JacksonUtils.toJson(
            new AiResourceIndexTaskPayload(resourceType, resourceName, enhancementRequested));
    }
    
    private static String taskResult(String enhancementFingerprint) {
        return enhancementFingerprint == null
            ? null : JacksonUtils.toJson(new AiResourceIndexTaskResult(enhancementFingerprint));
    }
    
    private JdbcTemplate getJdbcTemplate() {
        if (injectedJdbcTemplate != null) {
            return injectedJdbcTemplate;
        }
        return DynamicDataSource.getInstance().getDataSource().getJdbcTemplate();
    }
    
    private static final class AiResourceIndexTaskRowMapper
        implements RowMapper<AiResourceIndexTask> {
        
        @Override
        public AiResourceIndexTask mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiResourceIndexTask task = new AiResourceIndexTask();
            task.setTaskKey(rs.getString("task_key"));
            task.setNamespaceId(rs.getString("namespace_id"));
            task.setTaskType(rs.getString("task_type"));
            task.setTaskStage(rs.getString("task_stage"));
            task.setStatus(rs.getString("status"));
            applyPayload(task, rs.getString("task_payload"));
            task.setRetryCount(rs.getInt("retry_count"));
            task.setRevision(rs.getLong("revision"));
            return task;
        }
        
        private void applyPayload(AiResourceIndexTask task, String taskPayload)
            throws SQLException {
            try {
                AiResourceIndexTaskPayload payload = JacksonUtils.toObj(taskPayload,
                    AiResourceIndexTaskPayload.class);
                if (payload == null
                    || payload
                        .getSchemaVersion() != AiResourceIndexTaskPayload.CURRENT_SCHEMA_VERSION
                    || payload.getSubject() == null || payload.getOptions() == null
                    || payload.getSubject().getResourceType() == null
                    || payload.getSubject().getResourceName() == null) {
                    throw new IllegalArgumentException("Invalid search-index task payload");
                }
                task.setResourceType(payload.getSubject().getResourceType());
                task.setResourceName(payload.getSubject().getResourceName());
                task.setEnhancementRequested(payload.getOptions().isEnhancementRequested());
            } catch (Exception e) {
                throw new SQLException("Failed to decode AI resource task " + task.getTaskKey(), e);
            }
        }
    }
}
