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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StandaloneDatabaseOperateImplTest {
    
    @Spy
    @InjectMocks
    private StandaloneDatabaseOperateImpl operate;
    
    @Mock
    private RowMapper<ConfigInfo> rowMapper;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private JdbcTemplate tempJdbcTemplate;
    
    @Mock
    private BiConsumer<Boolean, Throwable> biConsumer;
    
    /*   @Mock
       private File file;
       */
    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Before
    public void setUp() {
        ReflectionTestUtils.setField(operate, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(operate, "transactionTemplate", transactionTemplate);
        
    }
    
    @Test
    public void testQueryOne1() {
        String sql = "SELECT 1";
        Class<Long> clazz = Long.class;
        Long num = 1L;
        when(jdbcTemplate.queryForObject(sql, clazz)).thenReturn(num);
        Assert.assertEquals(operate.queryOne(sql, clazz), (Long) 1L);
    }
    
    @Test
    public void testQueryOne2() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(jdbcTemplate.queryForObject(sql, args, ConfigInfo.class)).thenReturn(configInfo);
        Assert.assertEquals(operate.queryOne(sql, args, ConfigInfo.class), configInfo);
    }
    
    @Test
    public void testQueryOne3() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(jdbcTemplate.queryForObject(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfo);
        Assert.assertEquals(operate.queryOne(sql, args, rowMapper), configInfo);
    }
    
    @Test
    public void testQueryOne4() {
        String sql = "SELECT 1";
        Class<Long> clazz = Long.class;
        Long result = 1L;
        when(tempJdbcTemplate.queryForObject(sql, clazz)).thenReturn(result);
        Assert.assertEquals(operate.queryOne(tempJdbcTemplate, sql, clazz), result);
    }
    
    @Test
    public void testQueryOne5() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(tempJdbcTemplate.queryForObject(sql, args, ConfigInfo.class)).thenReturn(configInfo);
        Assert.assertEquals(operate.queryOne(tempJdbcTemplate, sql, args, ConfigInfo.class), configInfo);
    }
    
    @Test
    public void testQueryOne6() {
        final String sql = "SELECT * FROM config_info WHERE id = ? AND data_id = ? AND group_id = ?";
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(1L);
        configInfo.setDataId("test");
        configInfo.setGroup("test");
        Object[] args = new Object[] {configInfo.getId(), configInfo.getDataId(), configInfo.getGroup()};
        when(tempJdbcTemplate.queryForObject(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfo);
        Assert.assertEquals(operate.queryOne(tempJdbcTemplate, sql, args, rowMapper), configInfo);
    }
    
    @Test
    public void testQueryMany1() {
        final String sql = "SELECT * FROM config_info WHERE id >= ? AND id <= ?";
        final Object[] args = new Object[] {1, 2};
        ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(1);
        ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setId(2);
        List<ConfigInfo> configInfos = new ArrayList<>();
        configInfos.add(configInfo1);
        configInfos.add(configInfo2);
        when(jdbcTemplate.query(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfos);
        Assert.assertEquals(configInfos, operate.queryMany(sql, args, rowMapper));
    }
    
    @Test
    public void testQueryMany2() {
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
        Assert.assertEquals(operate.queryMany(sql, args), resultList);
    }
    
    @Test
    public void testQueryMany3() {
        String sql = "SELECT data_id FROM config_info WHERE id >= ? AND id <= ?";
        Object[] args = new Object[] {1, 2};
        String dataId1 = "test1";
        String dataId2 = "test2";
        List<String> resultList = new ArrayList<>();
        resultList.add(dataId1);
        resultList.add(dataId2);
        Class clazz = dataId1.getClass();
        when(jdbcTemplate.queryForList(sql, args, clazz)).thenReturn(resultList);
        Assert.assertEquals(operate.queryMany(sql, args, clazz), resultList);
    }
    
    @Test
    public void testQueryMany4() {
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
        Assert.assertEquals(operate.queryMany(tempJdbcTemplate, sql, args), resultList);
    }
    
    @Test
    public void testQueryMany5() {
        String sql = "SELECT data_id FROM config_info WHERE id >= ? AND id <= ?";
        Object[] args = new Object[] {1, 2};
        String dataId1 = "test1";
        String dataId2 = "test2";
        List<String> resultList = new ArrayList<>();
        resultList.add(dataId1);
        resultList.add(dataId2);
        Class clazz = dataId1.getClass();
        when(operate.queryMany(jdbcTemplate, sql, args, clazz)).thenReturn(resultList);
        Assert.assertEquals(operate.queryMany(jdbcTemplate, sql, args, clazz), resultList);
    }
    
    @Test
    public void testQueryMany6() {
        final String sql = "SELECT * FROM config_info WHERE id >= ? AND id <= ?";
        final Object[] args = new Object[] {1, 2};
        ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(1);
        ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setId(2);
        List<ConfigInfo> configInfos = new ArrayList<>();
        configInfos.add(configInfo1);
        configInfos.add(configInfo2);
        when(tempJdbcTemplate.query(eq(sql), eq(args), any(RowMapper.class))).thenReturn(configInfos);
        Assert.assertEquals(operate.queryMany(tempJdbcTemplate, sql, args, rowMapper), configInfos);
    }
    
    @Test
    public void testDataImport() throws ExecutionException, InterruptedException {
        RestResult<String> errorResult = RestResult.<String>builder().withCode(500).withMsg("null").withData(null)
                .build();
        CompletableFuture<RestResult<String>> errorFuture = new CompletableFuture<>();
        errorFuture.complete(errorResult);
        doReturn(errorFuture).when(operate).dataImport(null);
        Assert.assertEquals(operate.dataImport(null).get(), errorResult);
    }
    
    @Test
    public void testUpdate1() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        Assert.assertTrue(operate.update(modifyRequests));
    }
    
    @Test
    public void testUpdate2() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        Assert.assertTrue(operate.update(modifyRequests, biConsumer));
    }
    
    @Test
    public void testUpdate3() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        Assert.assertTrue(operate.update(transactionTemplate, jdbcTemplate, modifyRequests));
    }
    
    @Test
    public void testUpdate4() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        Assert.assertTrue(operate.update(transactionTemplate, jdbcTemplate, modifyRequests, biConsumer));
    }
    
    @Test
    public void testBlockUpdate1() {
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = 1;";
        EmbeddedStorageContextUtils.addSqlContext(sql);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        Assert.assertTrue(operate.blockUpdate());
    }
    
    @Test
    public void testBlockUpdate2() {
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = 1;";
        EmbeddedStorageContextUtils.addSqlContext(sql);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(true);
        Assert.assertTrue(operate.blockUpdate(biConsumer));
    }
    
    @Test
    public void testDoDataImport() {
        List<ModifyRequest> modifyRequests = new ArrayList<>();
        ModifyRequest modifyRequest1 = new ModifyRequest();
        String sql = "UPDATE config_info SET data_id = 'test' WHERE id = ?;";
        modifyRequest1.setSql(sql);
        Object[] args = new Object[] {1};
        modifyRequest1.setArgs(args);
        modifyRequests.add(modifyRequest1);
        when(tempJdbcTemplate.batchUpdate(sql)).thenReturn(new int[] {1});
        Assert.assertTrue(operate.doDataImport(tempJdbcTemplate, modifyRequests));
    }
    
    @Test
    public void testFutureUpdate() throws ExecutionException, InterruptedException {
        String sql = "SELECT 1";
        EmbeddedStorageContextUtils.addSqlContext(sql);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        future.complete(true);
        doAnswer((invocationOnMock) -> null).when(operate).futureUpdate();
        when(operate.futureUpdate()).thenReturn(future);
        Assert.assertTrue(operate.futureUpdate().get());
    }
}