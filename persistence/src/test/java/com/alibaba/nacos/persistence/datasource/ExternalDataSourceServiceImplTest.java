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

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.exception.NJdbcException;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalDataSourceServiceImplTest {
    
    @InjectMocks
    private ExternalDataSourceServiceImpl service;
    
    @Mock
    private JdbcTemplate jt;
    
    @Mock
    private DataSourceTransactionManager tm;
    
    @Mock
    private TransactionTemplate tjt;
    
    @Mock
    private JdbcTemplate testMasterJT;
    
    @Mock
    private JdbcTemplate testMasterWritableJT;
    
    @BeforeEach
    void setUp() {
        service = new ExternalDataSourceServiceImpl();
        ReflectionTestUtils.setField(service, "jt", jt);
        ReflectionTestUtils.setField(service, "tm", tm);
        ReflectionTestUtils.setField(service, "tjt", tjt);
        ReflectionTestUtils.setField(service, "testMasterJT", testMasterJT);
        ReflectionTestUtils.setField(service, "testMasterWritableJT", testMasterWritableJT);
        List<HikariDataSource> dataSourceList = new ArrayList<>();
        dataSourceList.add(new HikariDataSource());
        ReflectionTestUtils.setField(service, "dataSourceList", dataSourceList);
    }
    
    @Test
    void testInit() {
        try {
            MockEnvironment environment = new MockEnvironment();
            EnvUtil.setEnvironment(environment);
            environment.setProperty("db.num", "2");
            environment.setProperty("db.user", "user");
            environment.setProperty("db.password", "password");
            environment.setProperty("db.url.0", "1.1.1.1");
            environment.setProperty("db.url.1", "2.2.2.2");
            environment.setProperty("db.pool.config.driverClassName",
                    "com.alibaba.nacos.persistence.datasource.mock.MockDriver");
            DatasourceConfiguration.setUseExternalDB(true);
            ExternalDataSourceServiceImpl service1 = new ExternalDataSourceServiceImpl();
            assertDoesNotThrow(service1::init);
            assertEquals("", service1.getDataSourceType());
            assertEquals("1.1.1.1", service1.getCurrentDbUrl());
            assertNotNull(service1.getJdbcTemplate());
            assertNotNull(service1.getTransactionTemplate());
        } finally {
            DatasourceConfiguration.setUseExternalDB(false);
            EnvUtil.setEnvironment(null);
        }
    }
    
    @Test
    void testInitInvalidConfig() {
        try {
            MockEnvironment environment = new MockEnvironment();
            EnvUtil.setEnvironment(environment);
            DatasourceConfiguration.setUseExternalDB(true);
            ExternalDataSourceServiceImpl service1 = new ExternalDataSourceServiceImpl();
            assertThrows(RuntimeException.class, service1::init);
        } finally {
            DatasourceConfiguration.setUseExternalDB(false);
            EnvUtil.setEnvironment(null);
        }
    }
    
    @Test
    void testReload() {
        try {
            MockEnvironment environment = new MockEnvironment();
            EnvUtil.setEnvironment(environment);
            environment.setProperty("db.num", "1");
            environment.setProperty("db.user", "user");
            environment.setProperty("db.password", "password");
            environment.setProperty("db.url.0", "1.1.1.1");
            environment.setProperty("db.pool.config.driverClassName",
                    "com.alibaba.nacos.persistence.datasource.mock.MockDriver");
            DatasourceConfiguration.setUseExternalDB(true);
            HikariDataSource dataSource = mock(HikariDataSource.class);
            JdbcTemplate oldJt = mock(JdbcTemplate.class);
            ReflectionTestUtils.setField(service, "testJtList", Collections.singletonList(oldJt));
            ReflectionTestUtils.setField(service, "dataSourceList", Collections.singletonList(dataSource));
            assertDoesNotThrow(service::reload);
            verify(jt).setDataSource(any(DataSource.class));
            verify(oldJt).setDataSource(null);
            verify(dataSource).close();
        } finally {
            DatasourceConfiguration.setUseExternalDB(false);
            EnvUtil.setEnvironment(null);
        }
    }
    
    @Test
    void testCheckMasterWritable() {
        when(testMasterWritableJT.queryForObject(eq(" SELECT @@read_only "), eq(Integer.class))).thenReturn(0);
        assertTrue(service.checkMasterWritable());
    }
    
    @Test
    void testCheckMasterWritableWithoutResult() {
        when(testMasterWritableJT.queryForObject(eq(" SELECT @@read_only "), eq(Integer.class))).thenReturn(null);
        assertFalse(service.checkMasterWritable());
    }
    
    @Test
    void testCheckMasterWritableWithException() {
        when(testMasterWritableJT.queryForObject(eq(" SELECT @@read_only "), eq(Integer.class))).thenThrow(
                new CannotGetJdbcConnectionException("test"));
        assertFalse(service.checkMasterWritable());
    }
    
    @Test
    void testGetCurrentDbUrl() {
        HikariDataSource bds = new HikariDataSource();
        bds.setJdbcUrl("test.jdbc.url");
        when(jt.getDataSource()).thenReturn(bds);
        assertEquals("test.jdbc.url", service.getCurrentDbUrl());
    }
    
    @Test
    void testGetCurrentDbUrlWithoutDatasource() {
        assertEquals("", service.getCurrentDbUrl());
    }
    
    @Test
    void testGetHealth() {
        List<Boolean> isHealthList = new ArrayList<>();
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        assertEquals("UP", service.getHealth());
    }
    
    @Test
    void testGetHealthWithMasterDown() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getJdbcUrl()).thenReturn("1.1.1.1");
        ReflectionTestUtils.setField(service, "dataSourceList", Collections.singletonList(dataSource));
        List<Boolean> isHealthList = new ArrayList<>();
        isHealthList.add(Boolean.FALSE);
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        assertEquals("DOWN:1.1.1.1", service.getHealth());
    }
    
    @Test
    void testGetHealthWithSlaveDown() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getJdbcUrl()).thenReturn("2.2.2.2");
        List<HikariDataSource> dataSourceList = new ArrayList<>();
        dataSourceList.add(null);
        dataSourceList.add(dataSource);
        ReflectionTestUtils.setField(service, "dataSourceList", dataSourceList);
        List<Boolean> isHealthList = new ArrayList<>();
        isHealthList.add(Boolean.TRUE);
        isHealthList.add(Boolean.FALSE);
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        assertEquals("WARN:2.2.2.2", service.getHealth());
    }
    
    @Test
    void testCheckDbHealthTaskRun() {
        List<JdbcTemplate> testJtList = new ArrayList<>();
        testJtList.add(jt);
        ReflectionTestUtils.setField(service, "testJtList", testJtList);
        
        List<Boolean> isHealthList = new ArrayList<>();
        isHealthList.add(Boolean.FALSE);
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        
        service.new CheckDbHealthTask().run();
        assertEquals(1, isHealthList.size());
        assertTrue(isHealthList.get(0));
    }
    
    @Test
    void testCheckDbHealthTaskRunWhenEmptyResult() {
        List<JdbcTemplate> testJtList = new ArrayList<>();
        testJtList.add(jt);
        ReflectionTestUtils.setField(service, "testJtList", testJtList);
        
        List<Boolean> isHealthList = new ArrayList<>();
        isHealthList.add(Boolean.FALSE);
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        
        when(jt.queryForMap(anyString())).thenThrow(new EmptyResultDataAccessException("Expected exception", 1));
        service.new CheckDbHealthTask().run();
        assertEquals(1, isHealthList.size());
        assertTrue(isHealthList.get(0));
    }
    
    @Test
    void testCheckDbHealthTaskRunWhenSqlException() {
        List<JdbcTemplate> testJtList = new ArrayList<>();
        testJtList.add(jt);
        ReflectionTestUtils.setField(service, "testJtList", testJtList);
        
        List<Boolean> isHealthList = new ArrayList<>();
        isHealthList.add(Boolean.FALSE);
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        
        when(jt.queryForMap(anyString())).thenThrow(
                new UncategorizedSQLException("Expected exception", "", new SQLException()));
        service.new CheckDbHealthTask().run();
        assertEquals(1, isHealthList.size());
        assertFalse(isHealthList.get(0));
    }
    
    @Test
    void testCheckDbHealthTaskRunWhenSqlExceptionForSlave() {
        List<JdbcTemplate> testJtList = new ArrayList<>();
        testJtList.add(jt);
        ReflectionTestUtils.setField(service, "testJtList", testJtList);
        
        List<Boolean> isHealthList = new ArrayList<>();
        isHealthList.add(Boolean.FALSE);
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        ReflectionTestUtils.setField(service, "masterIndex", 1);
        
        when(jt.queryForMap(anyString())).thenThrow(
                new UncategorizedSQLException("Expected exception", "", new SQLException()));
        service.new CheckDbHealthTask().run();
        assertEquals(1, isHealthList.size());
        assertFalse(isHealthList.get(0));
    }
    
    @Test
    void testMasterSelectWithException() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        ReflectionTestUtils.setField(service, "dataSourceList", Collections.singletonList(dataSource));
        when(testMasterJT.update("DELETE FROM config_info WHERE data_id='com.alibaba.nacos.testMasterDB'")).thenThrow(
                new NJdbcException("test"));
        assertDoesNotThrow(() -> service.new SelectMasterTask().run());
    }
}
