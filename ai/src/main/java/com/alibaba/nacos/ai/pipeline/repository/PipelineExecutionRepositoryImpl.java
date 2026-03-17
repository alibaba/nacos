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

package com.alibaba.nacos.ai.pipeline.repository;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC-based implementation of {@link PipelineExecutionRepository}.
 *
 * <p>Uses {@link DynamicDataSource} to obtain a {@link JdbcTemplate} and persists pipeline execution
 * records to the {@code pipeline_execution} table. The pipeline field (List of PipelineNodeResult)
 * is serialized/deserialized as JSON using {@link JacksonUtils}.</p>
 *
 * @author kiro
 * @since 3.2.0
 */
public class PipelineExecutionRepositoryImpl implements PipelineExecutionRepository {
    
    private static final Logger LOG = LoggerFactory.getLogger(PipelineExecutionRepositoryImpl.class);
    
    private static final String SQL_INSERT = "INSERT INTO pipeline_execution "
            + "(execution_id, resource_type, resource_name, namespace_id, version, status, pipeline, create_time, update_time) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    
    private static final String SQL_UPDATE = "UPDATE pipeline_execution SET status=?, pipeline=?, update_time=? "
            + "WHERE execution_id=?";
    
    private static final String SQL_FIND_BY_ID = "SELECT * FROM pipeline_execution WHERE execution_id=?";
    
    private static final String SQL_FIND_BY_RESOURCE = "SELECT * FROM pipeline_execution "
            + "WHERE resource_type=? AND resource_name=? AND namespace_id=? AND version=? "
            + "ORDER BY create_time DESC LIMIT 1";
    
    private static final PipelineExecutionRowMapper ROW_MAPPER = new PipelineExecutionRowMapper();
    
    private final JdbcTemplate injectedJdbcTemplate;
    
    /**
     * Default constructor. Uses {@link DynamicDataSource} to obtain the JdbcTemplate.
     */
    public PipelineExecutionRepositoryImpl() {
        this.injectedJdbcTemplate = null;
    }
    
    /**
     * Constructor for testing. Accepts a JdbcTemplate directly.
     *
     * @param jdbcTemplate the JdbcTemplate to use
     */
    public PipelineExecutionRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.injectedJdbcTemplate = jdbcTemplate;
    }
    
    private JdbcTemplate getJdbcTemplate() {
        if (injectedJdbcTemplate != null) {
            return injectedJdbcTemplate;
        }
        return DynamicDataSource.getInstance().getDataSource().getJdbcTemplate();
    }
    
    @Override
    public void save(PipelineExecution execution) {
        String pipelineJson = JacksonUtils.toJson(execution.getPipeline());
        getJdbcTemplate().update(SQL_INSERT, execution.getExecutionId(), execution.getResourceType(),
                execution.getResourceName(), execution.getNamespaceId(), execution.getVersion(),
                execution.getStatus().name(), pipelineJson, execution.getCreateTime(), execution.getUpdateTime());
    }
    
    @Override
    public void update(PipelineExecution execution) {
        String pipelineJson = JacksonUtils.toJson(execution.getPipeline());
        getJdbcTemplate().update(SQL_UPDATE, execution.getStatus().name(), pipelineJson,
                execution.getUpdateTime(), execution.getExecutionId());
    }
    
    @Override
    public PipelineExecution findById(String executionId) {
        try {
            return getJdbcTemplate().queryForObject(SQL_FIND_BY_ID, ROW_MAPPER, executionId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    @Override
    public PipelineExecution findByResource(String resourceType, String resourceName, String namespaceId,
            String version) {
        try {
            return getJdbcTemplate().queryForObject(SQL_FIND_BY_RESOURCE, ROW_MAPPER, resourceType, resourceName,
                    namespaceId, version);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    /**
     * RowMapper for mapping ResultSet rows to PipelineExecution objects.
     */
    private static class PipelineExecutionRowMapper implements RowMapper<PipelineExecution> {
        
        @Override
        public PipelineExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
            PipelineExecution execution = new PipelineExecution();
            execution.setExecutionId(rs.getString("execution_id"));
            execution.setResourceType(rs.getString("resource_type"));
            execution.setResourceName(rs.getString("resource_name"));
            execution.setNamespaceId(rs.getString("namespace_id"));
            execution.setVersion(rs.getString("version"));
            execution.setStatus(PipelineExecutionStatus.valueOf(rs.getString("status")));
            
            String pipelineJson = rs.getString("pipeline");
            List<PipelineNodeResult> pipeline = JacksonUtils.toObj(pipelineJson,
                    new TypeReference<List<PipelineNodeResult>>() { });
            execution.setPipeline(pipeline);
            
            execution.setCreateTime(rs.getLong("create_time"));
            execution.setUpdateTime(rs.getLong("update_time"));
            return execution;
        }
    }
}
