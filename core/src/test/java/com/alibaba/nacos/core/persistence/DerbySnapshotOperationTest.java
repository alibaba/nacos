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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.core.persistence;

import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.persistence.utils.PersistenceExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerbySnapshotOperationTest {
    
    @Mock
    private LocalDataSourceServiceImpl dataSourceService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private CallableStatement callableStatement;
    
    private MockedStatic<DynamicDataSource> dynamicDataSourceMock;
    private MockedStatic<EnvUtil> envUtilMock;
    private MockedStatic<DiskUtils> diskUtilsMock;
    private MockedStatic<PersistenceExecutor> persistenceExecutorMock;
    
    @BeforeEach
    void setUp() {
        envUtilMock = Mockito.mockStatic(EnvUtil.class);
        envUtilMock.when(EnvUtil::getNacosHome).thenReturn(System.getProperty("java.io.tmpdir"));
    }
    
    @AfterEach
    void tearDown() {
        if (dynamicDataSourceMock != null) {
            dynamicDataSourceMock.close();
        }
        if (envUtilMock != null) {
            envUtilMock.close();
        }
        if (diskUtilsMock != null) {
            diskUtilsMock.close();
        }
        if (persistenceExecutorMock != null) {
            persistenceExecutorMock.close();
        }
    }
    
    @Test
    void testConstructor() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        DerbySnapshotOperation operation = new DerbySnapshotOperation(lock.writeLock());
        assertNotNull(operation);
    }
    
    @Test
    void testOnSnapshotLoadWhenSnapshotFileMissing() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        DerbySnapshotOperation operation = new DerbySnapshotOperation(lock.writeLock());
        String tempDir = System.getProperty("java.io.tmpdir");
        Reader reader = new Reader(tempDir, Collections.emptyMap());
        boolean result = operation.onSnapshotLoad(reader);
        assertFalse(result);
    }
    
    @Test
    void testOnSnapshotSaveWithMocks() throws Exception {
        persistenceExecutorMock = Mockito.mockStatic(PersistenceExecutor.class);
        persistenceExecutorMock.when(() -> PersistenceExecutor.executeSnapshot(any(Runnable.class)))
            .thenAnswer(inv -> {
                inv.getArgument(0, Runnable.class).run();
                return null;
            });
        
        dynamicDataSourceMock = Mockito.mockStatic(DynamicDataSource.class);
        DynamicDataSource dsHolder = Mockito.mock(DynamicDataSource.class);
        when(dsHolder.getDataSource()).thenReturn(dataSourceService);
        dynamicDataSourceMock.when(DynamicDataSource::getInstance).thenReturn(dsHolder);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareCall(anyString())).thenReturn(callableStatement);
        
        diskUtilsMock = Mockito.mockStatic(DiskUtils.class);
        diskUtilsMock.when(() -> DiskUtils.deleteDirectory(anyString())).thenAnswer(inv -> null);
        diskUtilsMock.when(() -> DiskUtils.forceMkdir(anyString())).thenAnswer(inv -> null);
        diskUtilsMock.when(() -> DiskUtils.compress(anyString(), anyString(), anyString(), any()))
            .thenAnswer(inv -> null);
        
        Writer writer = new Writer(System.getProperty("java.io.tmpdir"));
        AtomicReference<Boolean> successRef = new AtomicReference<>();
        AtomicReference<Throwable> exRef = new AtomicReference<>();
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        DerbySnapshotOperation operation = new DerbySnapshotOperation(lock.writeLock());
        
        operation.onSnapshotSave(writer, (success, ex) -> {
            successRef.set(success);
            exRef.set(ex);
        });
        
        assertTrue(successRef.get());
        assertNull(exRef.get());
        verify(callableStatement).close();
    }
    
    @Test
    void testOnSnapshotSaveCatchPath() throws Exception {
        persistenceExecutorMock = Mockito.mockStatic(PersistenceExecutor.class);
        persistenceExecutorMock.when(() -> PersistenceExecutor.executeSnapshot(any(Runnable.class)))
            .thenAnswer(inv -> {
                inv.getArgument(0, Runnable.class).run();
                return null;
            });
        
        dynamicDataSourceMock = Mockito.mockStatic(DynamicDataSource.class);
        DynamicDataSource dsHolder = Mockito.mock(DynamicDataSource.class);
        when(dsHolder.getDataSource()).thenReturn(dataSourceService);
        dynamicDataSourceMock.when(DynamicDataSource::getInstance).thenReturn(dsHolder);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.getDataSource()).thenThrow(new RuntimeException("no ds"));
        
        diskUtilsMock = Mockito.mockStatic(DiskUtils.class);
        diskUtilsMock.when(() -> DiskUtils.deleteDirectory(anyString())).thenAnswer(inv -> null);
        diskUtilsMock.when(() -> DiskUtils.forceMkdir(anyString())).thenAnswer(inv -> null);
        
        Writer writer = new Writer(System.getProperty("java.io.tmpdir"));
        AtomicReference<Boolean> successRef = new AtomicReference<>();
        AtomicReference<Throwable> exRef = new AtomicReference<>();
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        DerbySnapshotOperation operation = new DerbySnapshotOperation(lock.writeLock());
        
        operation.onSnapshotSave(writer, (success, ex) -> {
            successRef.set(success);
            exRef.set(ex);
        });
        
        assertFalse(successRef.get());
        assertNotNull(exRef.get());
    }
}
