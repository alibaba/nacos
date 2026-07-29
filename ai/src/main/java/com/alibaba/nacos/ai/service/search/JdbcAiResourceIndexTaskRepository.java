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
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
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
    
    public JdbcAiResourceIndexTaskRepository() {
        this.injectedJdbcTemplate = null;
    }
    
    public JdbcAiResourceIndexTaskRepository(JdbcTemplate jdbcTemplate) {
        this.injectedJdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void schedule(String namespaceId, String resourceType, String resourceName,
        boolean enhancementRequested) {
        String taskKey = taskKey(namespaceId, resourceType, resourceName);
        String taskPayload = taskPayload(resourceType, resourceName, enhancementRequested);
        int updated = updateExistingLifecycleTask(taskKey, namespaceId, taskPayload);
        if (updated > 0) {
            return;
        }
        try {
            insertTask(taskKey, namespaceId, taskPayload);
        } catch (DuplicateKeyException ignored) {
            updateExistingLifecycleTask(taskKey, namespaceId, taskPayload);
        }
    }
    
    @Override
    public void scheduleReconciliation(String namespaceId, String resourceType,
        String resourceName, boolean enhancementRequested) {
        String taskKey = taskKey(namespaceId, resourceType, resourceName);
        String taskPayload = taskPayload(resourceType, resourceName, enhancementRequested);
        int updated = updateExistingReconciliationTask(taskKey, namespaceId, taskPayload);
        if (updated > 0) {
            return;
        }
        try {
            insertTask(taskKey, namespaceId, taskPayload);
        } catch (DuplicateKeyException ignored) {
            updateExistingReconciliationTask(taskKey, namespaceId, taskPayload);
        }
    }
    
    @Override
    public List<AiResourceIndexTask> findDueTasks(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT task_key, namespace_id, task_type, task_stage, status, task_payload, "
            + "retry_count, revision FROM ai_resource_task WHERE task_type=? AND "
            + "((status=? AND next_execute_time<=CURRENT_TIMESTAMP "
            + "AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)) OR "
            + "(status=? AND lease_until<CURRENT_TIMESTAMP)) "
            + "ORDER BY CASE WHEN task_stage=? THEN 0 ELSE 1 END, next_execute_time, gmt_modified";
        return getJdbcTemplate().query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, AiResourceIndexTask.TASK_TYPE);
            statement.setString(2, STATUS_PENDING);
            statement.setString(3, STATUS_PROCESSING);
            statement.setString(4, AiResourceIndexTask.STAGE_BASE_INDEX);
            statement.setMaxRows(limit);
            return statement;
        }, ROW_MAPPER);
    }
    
    @Override
    public boolean claim(AiResourceIndexTask task, Timestamp leaseUntil) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET status=?, "
            + "revision=revision+1, lease_until=?, gmt_modified=CURRENT_TIMESTAMP "
            + "WHERE task_key=? AND task_type=? AND revision=? AND task_stage=? "
            + "AND ((status=? AND next_execute_time<=CURRENT_TIMESTAMP "
            + "AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)) OR "
            + "(status=? AND lease_until<CURRENT_TIMESTAMP))",
            STATUS_PROCESSING, leaseUntil, task.getTaskKey(), AiResourceIndexTask.TASK_TYPE,
            task.getRevision(), task.getTaskStage(), STATUS_PENDING, STATUS_PROCESSING);
        if (updated == 1) {
            task.setRevision(task.getRevision() + 1);
            task.setStatus(STATUS_PROCESSING);
        }
        return updated == 1;
    }
    
    @Override
    public boolean renewLease(AiResourceIndexTask task, Timestamp leaseUntil) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET "
            + "lease_until=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_type=? AND task_stage=? AND status=?", leaseUntil, task.getTaskKey(),
            task.getRevision(), AiResourceIndexTask.TASK_TYPE, task.getTaskStage(),
            STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean advanceToEnhancement(AiResourceIndexTask task) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET "
            + "task_stage=?, status=?, task_result=NULL, retry_count=0, "
            + "next_execute_time=CURRENT_TIMESTAMP, lease_until=NULL, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_type=? AND task_stage=? AND status=?",
            AiResourceIndexTask.STAGE_LLM_ENHANCEMENT, STATUS_PENDING, task.getTaskKey(),
            task.getRevision(), AiResourceIndexTask.TASK_TYPE, task.getTaskStage(),
            STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean complete(AiResourceIndexTask task, String enhancementFingerprint) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET status=?, "
            + "task_result=?, retry_count=0, "
            + "lease_until=NULL, last_error=NULL, gmt_modified=CURRENT_TIMESTAMP "
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
    public void retry(AiResourceIndexTask task, Timestamp nextExecuteTime, String lastError) {
        getJdbcTemplate().update("UPDATE ai_resource_task SET status=?, "
            + "retry_count=retry_count+1, next_execute_time=?, lease_until=NULL, "
            + "last_error=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_type=? AND task_stage=? AND status=?",
            STATUS_PENDING, nextExecuteTime, truncate(lastError), task.getTaskKey(),
            task.getRevision(), AiResourceIndexTask.TASK_TYPE, task.getTaskStage(),
            STATUS_PROCESSING);
    }
    
    @Override
    public void releaseSuperseded(AiResourceIndexTask task) {
        getJdbcTemplate().update("UPDATE ai_resource_task SET lease_until=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=? "
            + "AND revision<>? AND status=?", task.getTaskKey(), AiResourceIndexTask.TASK_TYPE,
            task.getRevision(), STATUS_PENDING);
    }
    
    private int updateExistingLifecycleTask(String taskKey, String namespaceId,
        String taskPayload) {
        return getJdbcTemplate().update("UPDATE ai_resource_task SET namespace_id=?, "
            + "task_payload=?, task_stage=?, "
            + "lease_until=CASE WHEN status=? THEN lease_until ELSE NULL END, status=?, "
            + "task_result=NULL, retry_count=0, revision=revision+1, "
            + "next_execute_time=CURRENT_TIMESTAMP, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=?", namespaceId,
            taskPayload, AiResourceIndexTask.STAGE_BASE_INDEX, STATUS_PROCESSING, STATUS_PENDING,
            taskKey, AiResourceIndexTask.TASK_TYPE);
    }
    
    private int updateExistingReconciliationTask(String taskKey, String namespaceId,
        String taskPayload) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_task SET namespace_id=?, "
            + "task_stage=?, "
            + "lease_until=CASE WHEN status=? THEN lease_until ELSE NULL END, status=?, "
            + "task_result=NULL, retry_count=0, revision=revision+1, "
            + "next_execute_time=CURRENT_TIMESTAMP, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=? "
            + "AND status IN (?, ?)", namespaceId, AiResourceIndexTask.STAGE_BASE_INDEX,
            STATUS_PROCESSING, STATUS_PENDING, taskKey, AiResourceIndexTask.TASK_TYPE,
            STATUS_PENDING, STATUS_PROCESSING);
        if (updated > 0) {
            return updated;
        }
        return getJdbcTemplate().update("UPDATE ai_resource_task SET namespace_id=?, "
            + "task_payload=?, task_stage=?, status=?, lease_until=NULL, task_result=NULL, "
            + "retry_count=0, revision=revision+1, next_execute_time=CURRENT_TIMESTAMP, "
            + "last_error=NULL, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND task_type=? "
            + "AND status=?", namespaceId, taskPayload, AiResourceIndexTask.STAGE_BASE_INDEX,
            STATUS_PENDING, taskKey, AiResourceIndexTask.TASK_TYPE, STATUS_COMPLETED);
    }
    
    private void insertTask(String taskKey, String namespaceId, String taskPayload) {
        getJdbcTemplate().update("INSERT INTO ai_resource_task "
            + "(task_key, namespace_id, task_type, task_stage, status, task_payload, task_result, "
            + "retry_count, revision, next_execute_time, lease_until, last_error, gmt_create, "
            + "gmt_modified) VALUES (?, ?, ?, ?, ?, ?, NULL, 0, 1, CURRENT_TIMESTAMP, NULL, NULL, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", taskKey, namespaceId,
            AiResourceIndexTask.TASK_TYPE, AiResourceIndexTask.STAGE_BASE_INDEX, STATUS_PENDING,
            taskPayload);
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
