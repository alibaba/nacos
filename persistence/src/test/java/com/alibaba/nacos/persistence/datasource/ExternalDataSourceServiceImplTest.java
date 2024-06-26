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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void testCheckMasterWritable() {
        
        when(testMasterWritableJT.queryForObject(eq(" SELECT @@read_only "), eq(Integer.class))).thenReturn(0);
        assertTrue(service.checkMasterWritable());
    }
    
    @Test
    void testGetCurrentDbUrl() {
        
        HikariDataSource bds = new HikariDataSource();
        bds.setJdbcUrl("test.jdbc.url");
        when(jt.getDataSource()).thenReturn(bds);
        
        assertEquals("test.jdbc.url", service.getCurrentDbUrl());
    }
    
    @Test
    void testGetHealth() {
        
        List<Boolean> isHealthList = new ArrayList<>();
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        assertEquals("UP", service.getHealth());
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
        
        when(jt.queryForMap(anyString())).thenThrow(new UncategorizedSQLException("Expected exception", "", new SQLException()));
        service.new CheckDbHealthTask().run();
        assertEquals(1, isHealthList.size());
        assertFalse(isHealthList.get(0));
    }
    
}
