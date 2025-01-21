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

package com.alibaba.nacos.persistence.repository.embedded.operate;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.persistence.exception.NJdbcException;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandaloneDatabaseOperateImplTest {
    
    private StandaloneDatabaseOperateImpl operate;
    
    @Mock
    private RowMapper<MockConfigInfo> rowMapper;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private JdbcTemplate tempJdbcTemplate;
    
    @Mock
    private BiConsumer<Boolean, Throwable> biConsumer;
    
    @Mock
    private TransactionTemplate transactionTemplate;
    
    @BeforeAll
    static void beforeAll() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.persistence.sql.derby.limit.enabled", "false");
        EnvUtil.setEnvironment(environment);
    }
    
    @BeforeEach
    void setUp() {
        operate = new StandaloneDatabaseOperateImpl();
        operate.init();
        ReflectionTestUtils.setField(operate, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(operate, "transactionTemplate", transactionTemplate);
    }
    
    @AfterAll
    static void afterAll() {
        EnvUtil.setEnvironment(null);
        EmbeddedStorageContextHolder.cleanAllContext();
    }
    
    @Test
    void testQueryOne1() {
        String sql = "SELECT 1";
        Class<Long> clazz = Long.class;
        Long num = 1L;
        when(jdbcTemplate.queryForObject(sql, clazz)).thenReturn(num);
        assertEquals(operate.queryOne(sql, clazz), (Long) 1L);
        when(jdbcTemplate.queryForObject(sql, clazz)).thenThrow(new IncorrectResultSizeDataAccessException("test", 1));
        assertNull(operate.queryOne(sql, clazz));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForObject(sql, clazz)).thenThrow(new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> operate.queryOne(sql, clazz));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForObject(sql, clazz)).thenThrow(new NJdbcException("test", "OriginalExceptionName"));
        assertThrows(NJdbcException.class, () -> operate.queryOne(sql, clazz));
    }
    
    @Test
    void testQueryOne2() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        MockConfigInfo configInfo = new MockConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(jdbcTemplate.queryForObject(sql, args, MockConfigInfo.class)).thenReturn(configInfo);
        assertEquals(operate.queryOne(sql, args, MockConfigInfo.class), configInfo);
        when(jdbcTemplate.queryForObject(sql, args, MockConfigInfo.class)).thenThrow(
                new IncorrectResultSizeDataAccessException("test", 1));
        assertNull(operate.queryOne(sql, args, MockConfigInfo.class));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForObject(sql, args, MockConfigInfo.class)).thenThrow(
                new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> operate.queryOne(sql, args, MockConfigInfo.class));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForObject(sql, args, MockConfigInfo.class)).thenThrow(
                new NJdbcException("test", "OriginalExceptionName"));
        assertThrows(NJdbcException.class, () -> operate.queryOne(sql, args, MockConfigInfo.class));
    }
    
    @Test
    void testQueryOne3() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        MockConfigInfo configInfo = new MockConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(jdbcTemplate.queryForObject(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfo);
        assertEquals(operate.queryOne(sql, args, rowMapper), configInfo);
        when(jdbcTemplate.queryForObject(eq(sql), eq(args), any(RowMapper.class))).thenThrow(
                new IncorrectResultSizeDataAccessException("test", 1));
        assertNull(operate.queryOne(sql, args, rowMapper));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq(sql), eq(args), any(RowMapper.class))).thenThrow(
                new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> operate.queryOne(sql, args, rowMapper));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForObject(eq(sql), eq(args), any(RowMapper.class))).thenThrow(
                new NJdbcException("test", "OriginalExceptionName"));
        assertThrows(NJdbcException.class, () -> operate.queryOne(sql, args, rowMapper));
    }
    
    @Test
    void testQueryOne4() {
        String sql = "SELECT 1";
        Class<Long> clazz = Long.class;
        Long result = 1L;
        when(tempJdbcTemplate.queryForObject(sql, clazz)).thenReturn(result);
        assertEquals(operate.queryOne(tempJdbcTemplate, sql, clazz), result);
    }
    
    @Test
    void testQueryOne5() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        MockConfigInfo configInfo = new MockConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(tempJdbcTemplate.queryForObject(sql, args, MockConfigInfo.class)).thenReturn(configInfo);
        assertEquals(operate.queryOne(tempJdbcTemplate, sql, args, MockConfigInfo.class), configInfo);
    }
    
    @Test
    void testQueryOne6() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        MockConfigInfo configInfo = new MockConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(tempJdbcTemplate.queryForObject(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfo);
        assertEquals(operate.queryOne(tempJdbcTemplate, sql, args, rowMapper), configInfo);
    }
    
    @Test
    void testQueryMany1() {
        final String sql = "SELECT * FROM config_info WHERE id >= ? AND id <= ?";
        final Object[] args = new Object[] {1, 2};
        MockConfigInfo configInfo1 = new MockConfigInfo();
        configInfo1.setId(1);
        MockConfigInfo configInfo2 = new MockConfigInfo();
        configInfo2.setId(2);
        List<MockConfigInfo> configInfos = new ArrayList<>();
        configInfos.add(configInfo1);
        configInfos.add(configInfo2);
        when(jdbcTemplate.query(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfos);
        assertEquals(configInfos, operate.queryMany(sql, args, rowMapper));
        when(jdbcTemplate.query(eq(sql), eq(args), any(RowMapper.class))).thenThrow(
                new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> operate.queryMany(sql, args, rowMapper));
        reset(jdbcTemplate);
        when(jdbcTemplate.query(eq(sql), eq(args), any(RowMapper.class))).thenThrow(
                new NJdbcException("test", "OriginalExceptionName"));
        assertThrows(NJdbcException.class, () -> operate.queryMany(sql, args, rowMapper));
    }
    
    @Test
    void testQueryMany2() {
        final String sql = "SELECT id, data_id, group_id FROM config_info WHERE id >= ? AND id <= ?";
        final Object[] args = new Object[] {1, 2};
        
        final List<Map<String, Object>> resultList = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("id", 1);
        map1.put("data_id", "test");
        map1.put("group_id", "test");
        
        final Map<String, Object> map2 = new HashMap<>();
        map1.put("id", 2);
        map1.put("data_id", "test");
        map1.put("group_id", "test");
        
        resultList.add(map1);
        resultList.add(map2);
        
        when(jdbcTemplate.queryForList(sql, args)).thenReturn(resultList);
        assertEquals(operate.queryMany(sql, args), resultList);
        when(jdbcTemplate.queryForList(sql, args)).thenThrow(new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> operate.queryMany(sql, args));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForList(sql, args)).thenThrow(new NJdbcException("test", "OriginalExceptionName"));
        assertThrows(NJdbcException.class, () -> operate.queryMany(sql, args));
    }
    
    @Test
    void testQueryMany3() {
        String sql = "SELECT data_id FROM config_info WHERE id >= ? AND id <= ?";
        Object[] args = new Object[] {1, 2};
        String dataId1 = "test1";
        String dataId2 = "test2";
        List<String> resultList = new ArrayList<>();
        resultList.add(dataId1);
        resultList.add(dataId2);
        Class clazz = dataId1.getClass();
        when(jdbcTemplate.queryForList(sql, args, clazz)).thenReturn(resultList);
        assertEquals(operate.queryMany(sql, args, clazz), resultList);
        when(jdbcTemplate.queryForList(sql, args, clazz)).thenThrow(
                new IncorrectResultSizeDataAccessException("test", 1));
        assertNull(operate.queryMany(sql, args, clazz));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForList(sql, args, clazz)).thenThrow(new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> operate.queryMany(sql, args, clazz));
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForList(sql, args, clazz)).thenThrow(
                new NJdbcException("test", "OriginalExceptionName"));
        assertThrows(NJdbcException.class, () -> operate.queryMany(sql, args, clazz));
        
    }
    
    @Test
    void testQueryMany4() {
        final String sql = "SELECT data_id FROM config_info WHERE id >= ? AND id <= ?";
        final Object[] args = new Object[] {1, 2};
        final List<Map<String, Object>> resultList = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("id", 1);
        map1.put("data_id", "test");
        map1.put("group_id", "test");
        
        final Map<String, Object> map2 = new HashMap<>();
        map1.put("id", 2);
        map1.put("data_id", "test");
        map1.put("group_id", "test");
        
        resultList.add(map1);
        resultList.add(map2);
        
        when(tempJdbcTemplate.queryForList(sql, args)).thenReturn(resultList);
        assertEquals(operate.queryMany(tempJdbcTemplate, sql, args), resultList);
    }
    
    @Test
    void testQueryMany5() {
        String sql = "SELECT data_id FROM config_info WHERE id >= ? AND id <= ?";
        Object[] args = new Object[] {1, 2};
        String dataId1 = "test1";
        String dataId2 = "test2";
        List<String> resultList = new ArrayList<>();
        resultList.add(dataId1);
        resultList.add(dataId2);
        Class clazz = dataId1.getClass();
        when(operate.queryMany(jdbcTemplate, sql, args, clazz)).thenReturn(resultList);
        assertEquals(operate.queryMany(jdbcTemplate, sql, args, clazz), resultList);
    }
    
    @Test
    void testQueryMany6() {
        final String sql = "SELECT * FROM config_info WHERE id >= ? AND id <= ?";
        final Object[] args = new Object[] {1, 2};
        MockConfigInfo configInfo1 = new MockConfigInfo();
        configInfo1.setId(1);
        MockConfigInfo configInfo2 = new MockConfigInfo();
        configInfo2.setId(2);
        List<MockConfigInfo> configInfos = new ArrayList<>();
        configInfos.add(configInfo1);
        configInfos.add(configInfo2);
        when(tempJdbcTemplate.query(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfos);
        assertEquals(operate.queryMany(tempJdbcTemplate, sql, args, rowMapper), configInfos);
    }
    
    @Test
    void testDataImportSuccess() throws ExecutionException, InterruptedException, URISyntaxException {
        File file = new File(getClass().getClassLoader().getResource("META-INF/test-derby-import.sql").toURI());
        int[] executeResult = new int[21];
        for (int i = executeResult.length - 1; i > executeResult.length - 6; i--) {
            executeResult[i] = 1;
        }
        when(jdbcTemplate.batchUpdate(any(String[].class))).thenReturn(executeResult);
        CompletableFuture<RestResult<String>> result = operate.dataImport(file);
        TimeUnit.MILLISECONDS.sleep(1000L);
        assertTrue(result.get().ok());
        assertEquals(200, result.get().getCode());
    }
    
    @Test
    void testDataImportFailed() throws ExecutionException, InterruptedException, URISyntaxException {
        File file = new File(getClass().getClassLoader().getResource("META-INF/test-derby-import.sql").toURI());
        int[] executeResult = new int[5];
        when(jdbcTemplate.batchUpdate(any(String[].class))).thenReturn(executeResult);
        CompletableFuture<RestResult<String>> result = operate.dataImport(file);
        TimeUnit.MILLISECONDS.sleep(1000L);
        assertFalse(result.get().ok());
        assertEquals(500, result.get().getCode());
        assertNull(result.get().getMessage());
    }
    
    @Test
    void testDataImportException() throws ExecutionException, InterruptedException, URISyntaxException {
        File file = new File(getClass().getClassLoader().getResource("META-INF/test-derby-import.sql").toURI());
        when(jdbcTemplate.batchUpdate(any(String[].class))).thenThrow(new NJdbcException("test import failed"));
        CompletableFuture<RestResult<String>> result = operate.dataImport(file);
        TimeUnit.MILLISECONDS.sleep(1000L);
        assertFalse(result.get().ok());
        assertEquals(500, result.get().getCode());
        assertEquals("com.alibaba.nacos.persistence.exception.NJdbcException: test import failed",
                result.get().getMessage());
    }
    
    @Test
    void testUpdate1() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        assertTrue(operate.update(modifyRequests));
    }
    
    @Test
    void testUpdate2() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        assertTrue(operate.update(modifyRequests, biConsumer));
    }
    
    @Test
    void testUpdate3() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        assertTrue(operate.update(transactionTemplate, jdbcTemplate, modifyRequests));
    }
    
    @Test
    void testUpdate4() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        assertTrue(operate.update(transactionTemplate, jdbcTemplate, modifyRequests, biConsumer));
    }
    
    @Test
    void testBlockUpdate1() {
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = 1;";
        EmbeddedStorageContextHolder.addSqlContext(sql);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        assertTrue(operate.blockUpdate());
    }
    
    @Test
    void testBlockUpdate2() {
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = 1;";
        EmbeddedStorageContextHolder.addSqlContext(sql);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        assertTrue(operate.blockUpdate(biConsumer));
    }
    
    @Test
    void testBlockUpdateWithException() {
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = 1;";
        EmbeddedStorageContextHolder.addSqlContext(sql);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenThrow(new NJdbcException("test"));
        assertThrows(NJdbcException.class, () -> operate.blockUpdate(biConsumer));
        assertTrue(EmbeddedStorageContextHolder.getCurrentSqlContext().isEmpty());
    }
    
    @Test
    void testDoDataImport() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(tempJdbcTemplate.batchUpdate(sql)).thenReturn(new int[] {1});
        assertTrue(operate.doDataImport(tempJdbcTemplate, modifyRequests));
    }
}