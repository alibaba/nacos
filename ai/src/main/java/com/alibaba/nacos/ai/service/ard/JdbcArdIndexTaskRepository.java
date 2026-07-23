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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.ai.model.ard.ArdIndexTask;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC repository for durable, coalesced ARD index tasks.
 *
 * @author nacos
 */
@Repository
@ConditionalOnArdEnabled
public class JdbcArdIndexTaskRepository implements ArdIndexTaskRepository {
    
    static final String STATUS_PENDING = "pending";
    
    static final String STATUS_PROCESSING = "processing";
    
    static final String STATUS_RETRY = "retry";
    
    private static final int MAX_ERROR_LENGTH = 2000;
    
    private static final RowMapper<ArdIndexTask> ROW_MAPPER = new ArdIndexTaskRowMapper();
    
    private final JdbcTemplate injectedJdbcTemplate;
    
    public JdbcArdIndexTaskRepository() {
        this.injectedJdbcTemplate = null;
    }
    
    public JdbcArdIndexTaskRepository(JdbcTemplate jdbcTemplate) {
        this.injectedJdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void schedule(String namespaceId, String resourceType, String resourceName) {
        String taskKey = taskKey(namespaceId, resourceType, resourceName);
        int updated = updateExistingTask(taskKey, namespaceId, resourceType, resourceName);
        if (updated > 0) {
            return;
        }
        try {
            getJdbcTemplate().update("INSERT INTO ai_resource_ard_index_task "
                + "(task_key, namespace_id, resource_type, resource_name, status, "
                + "attempt_count, revision, next_retry_time, lease_until, last_error, "
                + "gmt_create, gmt_modified) VALUES (?, ?, ?, ?, ?, 0, 1, "
                + "CURRENT_TIMESTAMP, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                taskKey, namespaceId, resourceType, resourceName, STATUS_PENDING);
        } catch (DuplicateKeyException ignored) {
            updateExistingTask(taskKey, namespaceId, resourceType, resourceName);
        }
    }
    
    @Override
    public List<ArdIndexTask> findDueTasks(int limit) {
        List<ArdIndexTask> tasks = getJdbcTemplate().query(
            "SELECT task_key, namespace_id, resource_type, resource_name, status, "
                + "attempt_count, revision FROM ai_resource_ard_index_task WHERE "
                + "((status IN (?, ?) AND next_retry_time<=CURRENT_TIMESTAMP) OR "
                + "(status=? AND lease_until<CURRENT_TIMESTAMP)) "
                + "ORDER BY next_retry_time, gmt_modified",
            ROW_MAPPER, STATUS_PENDING, STATUS_RETRY, STATUS_PROCESSING);
        if (tasks.size() <= limit) {
            return tasks;
        }
        return new ArrayList<>(tasks.subList(0, limit));
    }
    
    @Override
    public boolean claim(ArdIndexTask task, Timestamp leaseUntil) {
        int updated = getJdbcTemplate().update("UPDATE ai_resource_ard_index_task SET status=?, "
            + "lease_until=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=? "
            + "AND ((status IN (?, ?) AND next_retry_time<=CURRENT_TIMESTAMP) OR "
            + "(status=? AND lease_until<CURRENT_TIMESTAMP))",
            STATUS_PROCESSING, leaseUntil, task.getTaskKey(), task.getRevision(), STATUS_PENDING,
            STATUS_RETRY, STATUS_PROCESSING);
        return updated == 1;
    }
    
    @Override
    public void complete(ArdIndexTask task) {
        getJdbcTemplate().update(
            "DELETE FROM ai_resource_ard_index_task WHERE task_key=? AND revision=?",
            task.getTaskKey(), task.getRevision());
    }
    
    @Override
    public void retry(ArdIndexTask task, Timestamp nextRetryTime, String lastError) {
        getJdbcTemplate().update("UPDATE ai_resource_ard_index_task SET status=?, "
            + "attempt_count=attempt_count+1, next_retry_time=?, lease_until=NULL, "
            + "last_error=?, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=? AND revision=?",
            STATUS_RETRY, nextRetryTime, truncate(lastError), task.getTaskKey(),
            task.getRevision());
    }
    
    private int updateExistingTask(String taskKey, String namespaceId, String resourceType,
        String resourceName) {
        return getJdbcTemplate().update("UPDATE ai_resource_ard_index_task SET namespace_id=?, "
            + "resource_type=?, resource_name=?, status=?, attempt_count=0, "
            + "revision=revision+1, next_retry_time=CURRENT_TIMESTAMP, lease_until=NULL, "
            + "last_error=NULL, gmt_modified=CURRENT_TIMESTAMP WHERE task_key=?",
            namespaceId, resourceType, resourceName, STATUS_PENDING, taskKey);
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
    
    private static final class ArdIndexTaskRowMapper implements RowMapper<ArdIndexTask> {
        
        @Override
        public ArdIndexTask mapRow(ResultSet rs, int rowNum) throws SQLException {
            ArdIndexTask task = new ArdIndexTask();
            task.setTaskKey(rs.getString("task_key"));
            task.setNamespaceId(rs.getString("namespace_id"));
            task.setResourceType(rs.getString("resource_type"));
            task.setResourceName(rs.getString("resource_name"));
            task.setStatus(rs.getString("status"));
            task.setAttemptCount(rs.getInt("attempt_count"));
            task.setRevision(rs.getLong("revision"));
            return task;
        }
    }
}
