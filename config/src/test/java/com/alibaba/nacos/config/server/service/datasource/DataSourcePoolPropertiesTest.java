/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.assertEquals;

public class DataSourcePoolPropertiesTest {
    
    private static final String JDBC_URL = "jdbc:derby://127.0.0.1:3306/nacos_devtest?characterEncoding=utf8&serverTimezone=UTC";
    
    private static final String JDBC_DRIVER_CLASS_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
    
    private static final String PASSWORD = "nacos";
    
    private static final String USERNAME = "nacos_devtest";
    
    private static final Long CONNECTION_TIMEOUT = 10000L;
    
    private static final Integer MAX_POOL_SIZE = 50;
    
    private MockEnvironment environment;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.pool.config.connectionTimeout", CONNECTION_TIMEOUT.toString());
        environment.setProperty("db.pool.config.maximumPoolSize", MAX_POOL_SIZE.toString());
    }
    
    @Test
    public void testBuild() {
        DataSourcePoolProperties poolProperties = DataSourcePoolProperties.build(environment);
        poolProperties.setJdbcUrl(JDBC_URL);
        poolProperties.setDriverClassName(JDBC_DRIVER_CLASS_NAME);
        poolProperties.setUsername(USERNAME);
        poolProperties.setPassword(PASSWORD);
        HikariDataSource actual = poolProperties.getDataSource();
        assertEquals(JDBC_URL, actual.getJdbcUrl());
        assertEquals(JDBC_DRIVER_CLASS_NAME, actual.getDriverClassName());
        assertEquals(USERNAME, actual.getUsername());
        assertEquals(PASSWORD, actual.getPassword());
        assertEquals(CONNECTION_TIMEOUT.longValue(), actual.getConnectionTimeout());
        assertEquals(DataSourcePoolProperties.DEFAULT_VALIDATION_TIMEOUT, actual.getValidationTimeout());
        assertEquals(MAX_POOL_SIZE.intValue(), actual.getMaximumPoolSize());
        assertEquals(DataSourcePoolProperties.DEFAULT_MINIMUM_IDLE, actual.getMinimumIdle());
    }
}
