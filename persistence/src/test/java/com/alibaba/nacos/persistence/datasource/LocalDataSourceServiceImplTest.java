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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        DatasourceConfiguration.setUseExternalDB(false);
        service = new LocalDataSourceServiceImpl();
        ReflectionTestUtils.setField(service, "jt", jt);
        ReflectionTestUtils.setField(service, "tjt", tjt);
    }
    
    @Test
    void testInitWhenUseExternalDB() throws Exception {
        try {
            DatasourceConfiguration.setUseExternalDB(true);
            EnvUtil.setEnvironment(null);
            LocalDataSourceServiceImpl service1 = new LocalDataSourceServiceImpl();
            assertDoesNotThrow(service1::init);
        } finally {
            DatasourceConfiguration.setUseExternalDB(false);
        }
    }
    
    @Test
    void testInit() throws Exception {
        try {
            EnvUtil.setEnvironment(new MockEnvironment());
            LocalDataSourceServiceImpl service1 = new LocalDataSourceServiceImpl();
            assertDoesNotThrow(service1::init);
            assertNotNull(service1.getJdbcTemplate());
            assertNotNull(service1.getTransactionTemplate());
            assertEquals("derby", service1.getDataSourceType());
        } finally {
            EnvUtil.setEnvironment(null);
        }
    }
    
    @Test
    void testReloadWithNullDatasource() {
        assertThrowsExactly(RuntimeException.class, service::reload, "datasource is null");
    }
    
    @Test
    void testReloadWithException() throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(jt.getDataSource()).thenReturn(ds);
        when(ds.getConnection()).thenThrow(new SQLException());
        assertThrows(NacosRuntimeException.class, service::reload);
    }
    
    @Test
    void testCleanAndReopen() throws Exception {
        try {
            EnvUtil.setEnvironment(new MockEnvironment());
            LocalDataSourceServiceImpl service1 = new LocalDataSourceServiceImpl();
            assertDoesNotThrow(service1::init);
            assertDoesNotThrow(service1::cleanAndReopenDerby);
        } finally {
            EnvUtil.setEnvironment(null);
        }
    }
    
    @Test
    void testRestoreDerby() throws Exception {
        try {
            EnvUtil.setEnvironment(new MockEnvironment());
            LocalDataSourceServiceImpl service1 = new LocalDataSourceServiceImpl();
            assertDoesNotThrow(service1::init);
            Callable callback = mock(Callable.class);
            assertDoesNotThrow(() -> service1.restoreDerby(service1.getCurrentDbUrl(), callback));
            verify(callback).call();
        } finally {
            EnvUtil.setEnvironment(null);
        }
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
