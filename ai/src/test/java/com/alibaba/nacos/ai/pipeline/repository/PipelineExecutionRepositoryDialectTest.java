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

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineExecutionRepositoryDialectTest {
    
    @Test
    void appendPageClauseShouldUseMysqlStyleForMysqlLikeDatasources() {
        PipelineExecutionRepositoryImpl repository =
            new PipelineExecutionRepositoryImpl(newJdbcTemplate(),
                DataSourceConstant.MYSQL);
        
        String actual = repository
            .appendPageClause("SELECT * FROM pipeline_execution ORDER BY create_time DESC", 20, 10);
        
        assertEquals(
            "SELECT * FROM pipeline_execution ORDER BY create_time DESC LIMIT 10 OFFSET 20",
            actual);
    }
    
    @Test
    void appendPageClauseShouldUseOffsetFetchForDerby() {
        PipelineExecutionRepositoryImpl repository =
            new PipelineExecutionRepositoryImpl(newJdbcTemplate(),
                DataSourceConstant.DERBY);
        
        String actual = repository
            .appendPageClause("SELECT * FROM pipeline_execution ORDER BY create_time DESC", 20, 10);
        
        assertEquals(
            "SELECT * FROM pipeline_execution ORDER BY create_time DESC OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY",
            actual);
    }
    
    @Test
    void buildSingleLatestSqlShouldUseDerbyCompatibleFetchFirst() {
        PipelineExecutionRepositoryImpl repository =
            new PipelineExecutionRepositoryImpl(newJdbcTemplate(),
                DataSourceConstant.DERBY);
        
        String actual = repository.buildSingleLatestSql();
        
        assertEquals(
            "SELECT * FROM pipeline_execution WHERE resource_type=? AND resource_name=? AND namespace_id=? AND version=? "
                + "ORDER BY create_time DESC FETCH FIRST 1 ROW ONLY",
            actual);
    }
    
    private JdbcTemplate newJdbcTemplate() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pipeline_dialect_test;DB_CLOSE_DELAY=-1");
        return new JdbcTemplate(dataSource);
    }
}
