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
    
    static final String STATUS_RETRY = "retry";
    
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
        int updated = updateExistingLifecycleTask(taskKey, namespaceId, resourceType, resourceName,
            enhancementRequested);
        if (updated > 0) {
            return;
        }
        try {
            insertTask(taskKey, namespaceId, resourceType, resourceName, enhancementRequested);
        } catch (DuplicateKeyException ignored) {
            updateExistingLifecycleTask(taskKey, namespaceId, resourceType, resourceName,
                enhancementRequested);
        }
    }
    
    @Override
    public void scheduleReconciliation(String namespaceId, String resourceType,
        String resourceName, boolean enhancementRequested) {
        String taskKey = taskKey(namespaceId, resourceType, resourceName);
        int updated = updateExistingReconciliationTask(taskKey, namespaceId, resourceType,
            resourceName, enhancementRequested);
        if (updated > 0) {
            return;
        }
        try {
            insertTask(taskKey, namespaceId, resourceType, resourceName, enhancementRequested);
        } catch (DuplicateKeyException ignored) {
            updateExistingReconciliationTask(taskKey, namespaceId, resourceType, resourceName,
                enhancementRequested);
        }
    }
    
    @Override
    public List<AiResourceIndexTask> findDueTasks(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT task_key, namespace_id, resource_type, resource_name, task_stage, "
            + "status, enhancement_requested, enhancement_fingerprint, attempt_count, revision "
            + "FROM ai_resource_search_index_task WHERE "
            + "((status IN (?, ?) AND next_retry_time<=CURRENT_TIMESTAMP "
            + "AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)) OR "
            + "(status=? AND lease_until<CURRENT_TIMESTAMP)) "
            + "ORDER BY CASE WHEN task_stage=? THEN 0 ELSE 1 END, next_retry_time, gmt_modified";
        return getJdbcTemplate().query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, STATUS_PENDING);
            statement.setString(2, STATUS_RETRY);
            statement.setString(3, STATUS_PROCESSING);
            statement.setString(4, AiResourceIndexTask.STAGE_BASE_INDEX);
            statement.setMaxRows(limit);
            return statement;
        }, ROW_MAPPER);
    }
    
    @Override
    public boolean claim(AiResourceIndexTask task, Timestamp leaseUntil) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET status=?, "
            + "revision=revision+1, lease_until=?, gmt_modified=CURRENT_TIMESTAMP "
            + "WHERE task_key=? AND revision=? AND task_stage=? "
            + "AND ((status IN (?, ?) AND next_retry_time<=CURRENT_TIMESTAMP "
            + "AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)) OR "
            + "(status=? AND lease_until<CURRENT_TIMESTAMP))",
            STATUS_PROCESSING, leaseUntil, task.getTaskKey(), task.getRevision(),
            task.getTaskStage(), STATUS_PENDING, STATUS_RETRY, STATUS_PROCESSING);
        if (updated == 1) {
            task.setRevision(task.getRevision() + 1);
            task.setStatus(STATUS_PROCESSING);
        }
        return updated == 1;
    }
    
    @Override
    public boolean renewLease(AiResourceIndexTask task, Timestamp leaseUntil) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET "
            + "lease_until=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_stage=? AND status=?", leaseUntil, task.getTaskKey(),
            task.getRevision(), task.getTaskStage(), STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean advanceToEnhancement(AiResourceIndexTask task) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET "
            + "task_stage=?, status=?, enhancement_fingerprint=NULL, attempt_count=0, "
            + "next_retry_time=CURRENT_TIMESTAMP, lease_until=NULL, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_stage=? AND status=?", AiResourceIndexTask.STAGE_LLM_ENHANCEMENT,
            STATUS_PENDING, task.getTaskKey(), task.getRevision(), task.getTaskStage(),
            STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean complete(AiResourceIndexTask task, String enhancementFingerprint) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET status=?, "
            + "enhancement_requested=?, enhancement_fingerprint=?, attempt_count=0, "
            + "lease_until=NULL, last_error=NULL, gmt_modified=CURRENT_TIMESTAMP "
            + "WHERE task_key=? AND revision=? AND task_stage=? AND status=?", STATUS_COMPLETED,
            enhancementFingerprint != null ? 1 : 0, enhancementFingerprint, task.getTaskKey(),
            task.getRevision(), task.getTaskStage(), STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public boolean remove(AiResourceIndexTask task) {
        int updated = getJdbcTemplate().update(
            "DELETE FROM ai_resource_search_index_task WHERE task_key=? AND revision=? "
                + "AND task_stage=? AND status=?",
            task.getTaskKey(), task.getRevision(), task.getTaskStage(), STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public void retry(AiResourceIndexTask task, Timestamp nextRetryTime, String lastError) {
        getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET status=?, "
            + "attempt_count=attempt_count+1, next_retry_time=?, lease_until=NULL, "
            + "last_error=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND task_stage=? AND status=?",
            STATUS_RETRY, nextRetryTime, truncate(lastError), task.getTaskKey(),
            task.getRevision(), task.getTaskStage(), STATUS_PROCESSING);
    }
    
    @Override
    public void releaseSuperseded(AiResourceIndexTask task) {
        getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET lease_until=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision<>? AND status=?",
            task.getTaskKey(), task.getRevision(), STATUS_PENDING);
    }
    
    private int updateExistingLifecycleTask(String taskKey, String namespaceId, String resourceType,
        String resourceName, boolean enhancementRequested) {
        return getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET namespace_id=?, "
            + "resource_type=?, resource_name=?, task_stage=?, "
            + "lease_until=CASE WHEN status=? THEN lease_until ELSE NULL END, status=?, "
            + "enhancement_requested=?, enhancement_fingerprint=NULL, attempt_count=0, "
            + "revision=revision+1, next_retry_time=CURRENT_TIMESTAMP, "
            + "last_error=NULL, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=?", namespaceId,
            resourceType, resourceName, AiResourceIndexTask.STAGE_BASE_INDEX, STATUS_PROCESSING,
            STATUS_PENDING, enhancementRequested ? 1 : 0, taskKey);
    }
    
    private int updateExistingReconciliationTask(String taskKey, String namespaceId,
        String resourceType, String resourceName, boolean enhancementRequested) {
        return getJdbcTemplate().update("UPDATE ai_resource_search_index_task SET namespace_id=?, "
            + "resource_type=?, resource_name=?, task_stage=?, "
            + "enhancement_requested=CASE WHEN status IN (?, ?, ?) "
            + "THEN enhancement_requested ELSE ? END, "
            + "lease_until=CASE WHEN status=? THEN lease_until ELSE NULL END, status=?, "
            + "enhancement_fingerprint=NULL, attempt_count=0, revision=revision+1, "
            + "next_retry_time=CURRENT_TIMESTAMP, last_error=NULL, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE task_key=?", namespaceId, resourceType,
            resourceName, AiResourceIndexTask.STAGE_BASE_INDEX, STATUS_PENDING, STATUS_RETRY,
            STATUS_PROCESSING, enhancementRequested ? 1 : 0, STATUS_PROCESSING, STATUS_PENDING,
            taskKey);
    }
    
    private void insertTask(String taskKey, String namespaceId, String resourceType,
        String resourceName, boolean enhancementRequested) {
        getJdbcTemplate().update("INSERT INTO ai_resource_search_index_task "
            + "(task_key, namespace_id, resource_type, resource_name, task_stage, status, "
            + "enhancement_requested, enhancement_fingerprint, attempt_count, revision, "
            + "next_retry_time, lease_until, last_error, gmt_create, gmt_modified) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 0, 1, CURRENT_TIMESTAMP, NULL, NULL, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", taskKey, namespaceId, resourceType,
            resourceName, AiResourceIndexTask.STAGE_BASE_INDEX, STATUS_PENDING,
            enhancementRequested ? 1 : 0);
    }
    
    private String taskKey(String namespaceId, String resourceType, String resourceName) {
        String identity = String.valueOf(namespaceId) + '\n' + resourceType + '\n' + resourceName;
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
            task.setResourceType(rs.getString("resource_type"));
            task.setResourceName(rs.getString("resource_name"));
            task.setTaskStage(rs.getString("task_stage"));
            task.setStatus(rs.getString("status"));
            task.setEnhancementRequested(rs.getBoolean("enhancement_requested"));
            task.setEnhancementFingerprint(rs.getString("enhancement_fingerprint"));
            task.setAttemptCount(rs.getInt("attempt_count"));
            task.setRevision(rs.getLong("revision"));
            return task;
        }
    }
}
