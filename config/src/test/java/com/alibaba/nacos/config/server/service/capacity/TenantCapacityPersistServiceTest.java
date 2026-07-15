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

package com.alibaba.nacos.config.server.service.capacity;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.capacity.NamespaceCapacity;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.impl.mysql.TenantCapacityMapperByMySql;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantCapacityPersistServiceTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private MapperManager mapperManager;
    
    @InjectMocks
    private TenantCapacityPersistService service;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "dataSourceService", dataSourceService);
        ReflectionTestUtils.setField(service, "mapperManager", mapperManager);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        doReturn(new TenantCapacityMapperByMySql()).when(mapperManager).findMapper(any(),
            eq(TableConstant.TENANT_CAPACITY));
    }
    
    @Test
    void testGetTenantCapacity() {
        
        List<NamespaceCapacity> list = new ArrayList<>();
        NamespaceCapacity tenantCapacity = new NamespaceCapacity();
        tenantCapacity.setNamespaceId("test");
        list.add(tenantCapacity);
        
        String tenantId = "testId";
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(new Object[] {tenantId})))
            .thenReturn(list);
        NamespaceCapacity ret = service.getTenantCapacity(tenantId);
        
        assertEquals(tenantCapacity.getNamespaceId(), ret.getNamespaceId());
    }
    
    @Test
    void testGetTenantCapacityNotFound() {
        String tenantId = "notExist";
        when(jdbcTemplate.query(anyString(), any(RowMapper.class),
            eq(new Object[] {tenantId}))).thenReturn(new ArrayList<>());
        assertNull(service.getTenantCapacity(tenantId));
    }
    
    @Test
    void testInit() {
        DynamicDataSource dynamicDataSource = Mockito.mock(DynamicDataSource.class);
        try (MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic =
            Mockito.mockStatic(DynamicDataSource.class);
            MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<MapperManager> mapperManagerMockedStatic =
                Mockito.mockStatic(MapperManager.class)) {
            dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
                .thenReturn(dynamicDataSource);
            when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
            when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
            envUtilMockedStatic.when(() -> EnvUtil.getProperty(
                CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false))
                .thenReturn(true);
            mapperManagerMockedStatic.when(() -> MapperManager.instance(true))
                .thenReturn(mapperManager);
            
            service.init();
        }
        
        assertEquals(jdbcTemplate, ReflectionTestUtils.getField(service, "jdbcTemplate"));
        assertEquals(dataSourceService,
            ReflectionTestUtils.getField(service, "dataSourceService"));
        assertEquals(mapperManager, ReflectionTestUtils.getField(service, "mapperManager"));
    }
    
    @Test
    void testInsertTenantCapacity() {
        
        when(jdbcTemplate.update(anyString(), eq("test"), eq(null), eq(null), eq(null), eq(null),
            eq(null), eq(null),
            eq("test"))).thenReturn(1);
        
        NamespaceCapacity capacity = new NamespaceCapacity();
        capacity.setNamespaceId("test");
        assertTrue(service.insertTenantCapacity(capacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq("test"), eq(null), eq(null), eq(null), eq(null),
            eq(null), eq(null),
            eq("test"))).thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.insertTenantCapacity(capacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testInsertTenantCapacityReturnsFalseWhenNoRowUpdated() {
        when(jdbcTemplate.update(anyString(), eq("test"), eq(null), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq("test"))).thenReturn(0);
        
        NamespaceCapacity capacity = new NamespaceCapacity();
        capacity.setNamespaceId("test");
        assertFalse(service.insertTenantCapacity(capacity));
    }
    
    @Test
    void testIncrementUsageWithDefaultQuotaLimit() {
        
        NamespaceCapacity tenantCapacity = new NamespaceCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setNamespaceId("test");
        tenantCapacity.setQuota(1);
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenReturn(1);
        
        assertTrue(service.incrementUsageWithDefaultQuotaLimit(tenantCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenThrow(
            new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsageWithDefaultQuotaLimit(tenantCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testIncrementUsageMethodsReturnFalseWhenNoRowUpdated() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        NamespaceCapacity tenantCapacity = newTenantCapacity("test", timestamp);
        tenantCapacity.setQuota(1);
        
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenReturn(0);
        assertFalse(service.incrementUsageWithDefaultQuotaLimit(tenantCapacity));
        
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"))).thenReturn(0);
        assertFalse(service.incrementUsageWithQuotaLimit(tenantCapacity));
        assertFalse(service.incrementUsage(tenantCapacity));
        assertFalse(service.decrementUsage(tenantCapacity));
    }
    
    @Test
    void testIncrementUsageWithQuotaLimit() {
        
        NamespaceCapacity tenantCapacity = new NamespaceCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setNamespaceId("test2");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2"))).thenReturn(1);
        
        assertTrue(service.incrementUsageWithQuotaLimit(tenantCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2")))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsageWithQuotaLimit(tenantCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testIncrementUsage() {
        
        NamespaceCapacity tenantCapacity = new NamespaceCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setNamespaceId("test3");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3"))).thenReturn(1);
        
        assertTrue(service.incrementUsage(tenantCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3")))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsage(tenantCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testDecrementUsage() {
        
        NamespaceCapacity tenantCapacity = new NamespaceCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setNamespaceId("test4");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4"))).thenReturn(1);
        
        assertTrue(service.decrementUsage(tenantCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4")))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.decrementUsage(tenantCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testUpdateTenantCapacity() {
        final MockedStatic<TimeUtils> timeUtilsMockedStatic = Mockito.mockStatic(TimeUtils.class);
        
        List<Object> argList = CollectionUtils.list();
        
        Integer quota = 1;
        argList.add(quota);
        
        Integer maxSize = 2;
        argList.add(maxSize);
        
        Integer maxAggrCount = 3;
        argList.add(maxAggrCount);
        
        Integer maxAggrSize = 4;
        argList.add(maxAggrSize);
        
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        timeUtilsMockedStatic.when(TimeUtils::getCurrentTime).thenReturn(timestamp);
        argList.add(timestamp);
        
        String tenant = "test";
        argList.add(tenant);
        
        when(jdbcTemplate.update(anyString(), any(Object.class)))
            .thenAnswer((Answer<Integer>) invocationOnMock -> {
                if (invocationOnMock.getArgument(1).equals(quota)
                    && invocationOnMock.getArgument(2).equals(maxSize)
                    && invocationOnMock.getArgument(3).equals(maxAggrCount)
                    && invocationOnMock.getArgument(4).equals(maxAggrSize)
                    && invocationOnMock.getArgument(5).equals(timestamp)
                    && invocationOnMock.getArgument(6).equals(tenant)) {
                    return 1;
                }
                return 0;
            });
        assertTrue(service.updateTenantCapacity(tenant, quota, maxSize, maxAggrCount, maxAggrSize));
        
        timeUtilsMockedStatic.close();
    }
    
    @Test
    void testUpdateQuota() {
        List<Object> argList = CollectionUtils.list();
        
        Integer quota = 2;
        argList.add(quota);
        
        String tenant = "test2";
        argList.add(tenant);
        
        when(jdbcTemplate.update(anyString(), any(Object.class)))
            .thenAnswer((Answer<Integer>) invocationOnMock -> {
                if (invocationOnMock.getArgument(1).equals(quota)
                    && invocationOnMock.getArgument(3).equals(tenant)) {
                    return 1;
                }
                return 0;
            });
        assertTrue(service.updateQuota(tenant, quota));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), any(Object.class)))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.updateQuota(tenant, quota);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testUpdateTenantCapacityReturnsFalseWhenNoRowUpdated() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        try (MockedStatic<TimeUtils> timeUtilsMockedStatic =
            Mockito.mockStatic(TimeUtils.class)) {
            timeUtilsMockedStatic.when(TimeUtils::getCurrentTime).thenReturn(timestamp);
            when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
            
            assertFalse(service.updateTenantCapacity("test", 1, 2, 3, 4));
        }
    }
    
    @Test
    void testCorrectUsage() {
        
        String tenant = "test";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        when(jdbcTemplate.update(anyString(), eq(tenant), eq(timestamp), eq(tenant))).thenReturn(1);
        assertTrue(service.correctUsage(tenant, timestamp));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(tenant), eq(timestamp), eq(tenant))).thenThrow(
            new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.correctUsage(tenant, timestamp);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testCorrectUsageReturnsFalseWhenNoRowUpdated() {
        String tenant = "test";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        when(jdbcTemplate.update(anyString(), eq(tenant), eq(timestamp), eq(tenant)))
            .thenReturn(0);
        
        assertFalse(service.correctUsage(tenant, timestamp));
    }
    
    @Test
    void testCorrectUsageConnectionFailure() {
        String tenant = "test";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        when(jdbcTemplate.update(anyString(), eq(tenant), eq(timestamp), eq(tenant)))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        
        CannotGetJdbcConnectionException actual = assertThrows(
            CannotGetJdbcConnectionException.class,
            () -> service.correctUsage(tenant, timestamp));
        
        assertEquals("conn fail", actual.getMessage());
    }
    
    @Test
    void testGetCapacityList4CorrectUsage() {
        
        List<NamespaceCapacity> list = new ArrayList<>();
        NamespaceCapacity tenantCapacity = new NamespaceCapacity();
        tenantCapacity.setNamespaceId("test");
        list.add(tenantCapacity);
        long lastId = 1;
        int pageSize = 1;
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}),
            any(RowMapper.class))).thenReturn(list);
        List<NamespaceCapacity> ret = service.getCapacityList4CorrectUsage(lastId, pageSize);
        
        assertEquals(list.size(), ret.size());
        assertEquals(tenantCapacity.getNamespaceId(), ret.get(0).getNamespaceId());
        
        //mock get connection fail
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}),
            any(RowMapper.class))).thenThrow(
                new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.getCapacityList4CorrectUsage(lastId, pageSize);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testDeleteTenantCapacity() {
        
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenReturn(1);
        assertTrue(service.deleteTenantCapacity("test"));
        
        //mock get connection fail
        when(jdbcTemplate.update(any(PreparedStatementCreator.class)))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.deleteTenantCapacity("test");
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testDeleteTenantCapacityReturnsFalseWhenNoRowUpdated() {
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenReturn(0);
        
        assertFalse(service.deleteTenantCapacity("test"));
    }
    
    @Test
    void testTenantCapacityRowMapper() throws SQLException {
        TenantCapacityPersistService.TenantCapacityRowMapper groupCapacityRowMapper =
            new TenantCapacityPersistService.TenantCapacityRowMapper();
        ResultSet rs = Mockito.mock(ResultSet.class);
        int quota = 12345;
        Mockito.when(rs.getInt(eq("quota"))).thenReturn(quota);
        int usage = 1244;
        Mockito.when(rs.getInt(eq("usage"))).thenReturn(usage);
        int maxSize = 123;
        Mockito.when(rs.getInt(eq("max_size"))).thenReturn(maxSize);
        int maxAggrCount = 123;
        Mockito.when(rs.getInt(eq("max_aggr_count"))).thenReturn(maxAggrCount);
        int maxAggrSize = 123;
        Mockito.when(rs.getInt(eq("max_aggr_size"))).thenReturn(maxAggrSize);
        String tenant = "testTeat";
        Mockito.when(rs.getString(eq("tenant_id"))).thenReturn(tenant);
        
        NamespaceCapacity groupCapacity = groupCapacityRowMapper.mapRow(rs, 1);
        assertEquals(quota, groupCapacity.getQuota().intValue());
        assertEquals(usage, groupCapacity.getUsage().intValue());
        assertEquals(maxSize, groupCapacity.getMaxSize().intValue());
        assertEquals(maxAggrCount, groupCapacity.getMaxAggrCount().intValue());
        assertEquals(maxAggrSize, groupCapacity.getMaxAggrSize().intValue());
        assertEquals(tenant, groupCapacity.getNamespaceId());
    }
    
    @Test
    void testGetCapacityList4CorrectUsageRowMapper() {
        long lastId = 1;
        int pageSize = 1;
        
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenAnswer((Answer<List<NamespaceCapacity>>) invocation -> {
                RowMapper<NamespaceCapacity> rowMapper = invocation.getArgument(2);
                ResultSet rs = Mockito.mock(ResultSet.class);
                Mockito.when(rs.getLong("id")).thenReturn(200L);
                Mockito.when(rs.getString("tenant_id")).thenReturn("tenantX");
                List<NamespaceCapacity> result = new ArrayList<>();
                result.add(rowMapper.mapRow(rs, 1));
                return result;
            });
        
        List<NamespaceCapacity> ret = service.getCapacityList4CorrectUsage(lastId, pageSize);
        assertEquals(1, ret.size());
        assertEquals(200L, ret.get(0).getId().longValue());
        assertEquals("tenantX", ret.get(0).getNamespaceId());
    }
    
    @Test
    void testDeleteTenantCapacityPreparedStatementCreator() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        Mockito.when(connection.prepareStatement(anyString())).thenReturn(ps);
        
        when(jdbcTemplate.update(any(PreparedStatementCreator.class)))
            .thenAnswer((Answer<Integer>) invocation -> {
                PreparedStatementCreator creator = invocation.getArgument(0);
                creator.createPreparedStatement(connection);
                return 1;
            });
        
        assertTrue(service.deleteTenantCapacity("tenantX"));
        Mockito.verify(ps).setString(1, "tenantX");
    }
    
    private NamespaceCapacity newTenantCapacity(String namespaceId, Timestamp timestamp) {
        NamespaceCapacity tenantCapacity = new NamespaceCapacity();
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setNamespaceId(namespaceId);
        return tenantCapacity;
    }
}
