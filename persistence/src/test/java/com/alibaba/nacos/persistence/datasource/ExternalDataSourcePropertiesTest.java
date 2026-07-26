/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExternalDataSourcePropertiesTest {
    
    @SuppressWarnings("checkstyle:linelength")
    public static final String JDBC_URL =
        "jdbc:mysql://127.0.0.1:3306/nacos_devtest?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC";
    
    public static final String PASSWORD = "nacos";
    
    public static final String USERNAME = "nacos_devtest";
    
    @Test
    void externalDatasourceNormally() {
        HikariDataSource expectedDataSource = new HikariDataSource();
        expectedDataSource.setJdbcUrl(JDBC_URL);
        expectedDataSource.setUsername(USERNAME);
        expectedDataSource.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "1");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                
            }));
        assertEquals(1, dataSources.size());
    }
    
    @Test
    void externalDatasourceSupportsCanonicalConfig() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.plugin.datasource.db.num", "1");
        environment.setProperty("nacos.plugin.datasource.db.user", USERNAME);
        environment.setProperty("nacos.plugin.datasource.db.password", PASSWORD);
        environment.setProperty("nacos.plugin.datasource.db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
                assertEquals(JDBC_URL, dataSource.getJdbcUrl());
                assertEquals(USERNAME, dataSource.getUsername());
                assertEquals(PASSWORD, dataSource.getPassword());
            });
        assertEquals(1, dataSources.size());
    }
    
    @Test
    void externalDatasourceCanonicalConfigOverridesLegacyPerItem() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "2");
        environment.setProperty("db.url.0", "legacy-url-0");
        environment.setProperty("db.url.1", "legacy-url-1");
        environment.setProperty("db.user.0", "legacy-user-0");
        environment.setProperty("db.user.1", "legacy-user-1");
        environment.setProperty("db.password", "legacy-password");
        environment.setProperty("nacos.plugin.datasource.db.url.0", "canonical-url-0");
        environment.setProperty("nacos.plugin.datasource.db.user", "canonical-user");
        environment.setProperty("nacos.plugin.datasource.db.password.1",
            "canonical-password-1");
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        assertEquals(2, dataSources.size());
        assertEquals("canonical-url-0", dataSources.get(0).getJdbcUrl());
        assertEquals("legacy-url-1", dataSources.get(1).getJdbcUrl());
        assertEquals("canonical-user", dataSources.get(0).getUsername());
        assertEquals("canonical-user", dataSources.get(1).getUsername());
        assertEquals("legacy-password", dataSources.get(0).getPassword());
        assertEquals("canonical-password-1", dataSources.get(1).getPassword());
    }
    
    @Test
    void externalDatasourceToAssertMultiJdbcUrl() {
        
        HikariDataSource expectedDataSource = new HikariDataSource();
        expectedDataSource.setJdbcUrl(JDBC_URL);
        expectedDataSource.setUsername(USERNAME);
        expectedDataSource.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "2");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        environment.setProperty("db.url.1", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                
            }));
        assertEquals(2, dataSources.size());
    }
    
    @Test
    void externalDatasourceToAssertMultiPasswordAndUsername() {
        
        HikariDataSource expectedDataSource = new HikariDataSource();
        expectedDataSource.setJdbcUrl(JDBC_URL);
        expectedDataSource.setUsername(USERNAME);
        expectedDataSource.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "2");
        environment.setProperty("db.user.0", USERNAME);
        environment.setProperty("db.user.1", USERNAME);
        environment.setProperty("db.password.0", PASSWORD);
        environment.setProperty("db.password.1", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        environment.setProperty("db.url.1", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                
            }));
        assertEquals(2, dataSources.size());
    }
    
    @Test
    void externalDatasourceToAssertMinIdle() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "1");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                dataSource.validate();
                assertEquals(DataSourcePoolProperties.DEFAULT_MINIMUM_IDLE,
                    dataSource.getMinimumIdle());
            }));
        assertEquals(1, dataSources.size());
    }
    
    @Test
    void externalDatasourceFailureWithLarkInfo() {
        assertThrows(IllegalArgumentException.class, () -> {
            
            MockEnvironment environment = new MockEnvironment();
            new ExternalDataSourceProperties().build(environment, null);
            
        });
        
    }
    
    @Test
    void externalDatasourceFailureWithErrorInfo() {
        assertThrows(IllegalArgumentException.class, () -> {
            
            HikariDataSource expectedDataSource = new HikariDataSource();
            expectedDataSource.setJdbcUrl(JDBC_URL);
            expectedDataSource.setUsername(USERNAME);
            expectedDataSource.setPassword(PASSWORD);
            MockEnvironment environment = new MockEnvironment();
            // error num of db
            environment.setProperty("db.num", "2");
            environment.setProperty("db.user", USERNAME);
            environment.setProperty("db.password", PASSWORD);
            environment.setProperty("db.url.0", JDBC_URL);
            List<HikariDataSource> dataSources =
                new ExternalDataSourceProperties().build(environment, (dataSource -> {
                    assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                    assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                    assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                    
                }));
        });
    }
    
}
