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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalDataSourceServiceImplTest {
    
    @InjectMocks
    private LocalDataSourceServiceImpl service;
    
    @Mock
    private JdbcTemplate jt;
    
    @Mock
    private TransactionTemplate tjt;
    
    @BeforeEach
    void setUp() {
        service = new LocalDataSourceServiceImpl();
        ReflectionTestUtils.setField(service, "jt", jt);
        ReflectionTestUtils.setField(service, "tjt", tjt);
    }
    
    @Test
    void testGetDataSource() {
        
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("test.jdbc.url");
        when(jt.getDataSource()).thenReturn(dataSource);
        assertEquals(dataSource.getJdbcUrl(), ((HikariDataSource) service.getDatasource()).getJdbcUrl());
    }
    
    @Test
    void testCheckMasterWritable() {
        
        assertTrue(service.checkMasterWritable());
    }
    
    @Test
    void testSetAndGetHealth() {
        
        service.setHealthStatus("DOWN");
        assertEquals("DOWN", service.getHealth());
        
        service.setHealthStatus("UP");
        assertEquals("UP", service.getHealth());
    }
}
