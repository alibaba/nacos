/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

public class ExternalDataSourcePropertiesTest {
    
    @SuppressWarnings("checkstyle:linelength")
    public static final String JDBC_URL = "jdbc:mysql://127.0.0.1:3306/nacos_devtest?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC";
    
    public static final String PASSWORD = "nacos";
    
    public static final String USERNAME = "nacos_devtest";
    
    @Test
    public void externalDatasourceNormally() {
        HikariDataSource expectedDataSource = new HikariDataSource();
        expectedDataSource.setJdbcUrl(JDBC_URL);
        expectedDataSource.setUsername(USERNAME);
        expectedDataSource.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "1");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources = new ExternalDataSourceProperties().build(environment, (dataSource -> {
            Assert.assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
            Assert.assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
            Assert.assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
            
        }));
        Assert.assertEquals(dataSources.size(), 1);
    }
    
    @Test
    public void externalDatasourceToAssertMultiJdbcUrl() {
        
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
        List<HikariDataSource> dataSources = new ExternalDataSourceProperties().build(environment, (dataSource -> {
            Assert.assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
            Assert.assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
            Assert.assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
            
        }));
        Assert.assertEquals(dataSources.size(), 2);
    }
    
    @Test
    public void externalDatasourceToAssertMultiPasswordAndUsername() {
        
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
        List<HikariDataSource> dataSources = new ExternalDataSourceProperties().build(environment, (dataSource -> {
            Assert.assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
            Assert.assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
            Assert.assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
            
        }));
        Assert.assertEquals(dataSources.size(), 2);
    }

    @Test
    public void externalDatasourceToAssertMinIdle() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "1");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources = new ExternalDataSourceProperties().build(environment, (dataSource -> {
            dataSource.validate();
            Assert.assertEquals(dataSource.getMinimumIdle(), DataSourcePoolProperties.DEFAULT_MINIMUM_IDLE);
        }));
        Assert.assertEquals(dataSources.size(), 1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void externalDatasourceFailureWithLarkInfo() {
        
        MockEnvironment environment = new MockEnvironment();
        new ExternalDataSourceProperties().build(environment, null);
        
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void externalDatasourceFailureWithErrorInfo() {
        
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
        List<HikariDataSource> dataSources = new ExternalDataSourceProperties().build(environment, (dataSource -> {
            Assert.assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
            Assert.assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
            Assert.assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
            
        }));
    }
    
}
