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

package com.alibaba.nacos.ai.service.ard.vector;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PostgresqlAiResourceVectorIndex}.
 *
 * @author nacos
 */
class PostgresqlAiResourceVectorIndexTest {
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private MockEnvironment environment;
    
    private PostgresqlAiResourceVectorIndex vectorIndex;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        environment.setProperty(PostgresqlAiResourceVectorIndex.KEY_POSTGRESQL_URL,
            "jdbc:h2:mem:ard_vector_index;DB_CLOSE_DELAY=-1");
        environment.setProperty(PostgresqlAiResourceVectorIndex.KEY_POSTGRESQL_DRIVER_CLASS_NAME,
            "org.h2.Driver");
        EnvUtil.setEnvironment(environment);
        vectorIndex = new PostgresqlAiResourceVectorIndex();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (vectorIndex != null) {
            vectorIndex.shutdown();
        }
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void shouldReuseDedicatedJdbcTemplateWhenPostgresqlUrlConfigured() {
        JdbcTemplate first = vectorIndex.getDedicatedJdbcTemplate(
            environment.getProperty(PostgresqlAiResourceVectorIndex.KEY_POSTGRESQL_URL));
        JdbcTemplate second = vectorIndex.getDedicatedJdbcTemplate("jdbc:h2:mem:another");
        
        assertSame(first, second);
    }
    
    @Test
    void availableShouldProbeDedicatedDatasourceWhenPostgresqlUrlConfigured() {
        JdbcTemplate jdbcTemplate = vectorIndex.getDedicatedJdbcTemplate(
            environment.getProperty(PostgresqlAiResourceVectorIndex.KEY_POSTGRESQL_URL));
        jdbcTemplate.execute("CREATE TABLE ai_resource_ard_embedding_pg (id bigint)");
        
        assertTrue(vectorIndex.available());
    }
}
