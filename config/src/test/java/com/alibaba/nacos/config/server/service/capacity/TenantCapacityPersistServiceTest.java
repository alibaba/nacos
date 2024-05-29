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
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.impl.mysql.TenantCapacityMapperByMySql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
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
        doReturn(new TenantCapacityMapperByMySql()).when(mapperManager).findMapper(any(), eq(TableConstant.TENANT_CAPACITY));
    }
    
    @Test
    void testGetTenantCapacity() {
        
        List<TenantCapacity> list = new ArrayList<>();
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("test");
        list.add(tenantCapacity);
        
        String tenantId = "testId";
        when(jdbcTemplate.query(anyString(), eq(new Object[] {tenantId}), any(RowMapper.class))).thenReturn(list);
        TenantCapacity ret = service.getTenantCapacity(tenantId);
        
        assertEquals(tenantCapacity.getTenant(), ret.getTenant());
    }
    
    @Test
    void testInsertTenantCapacity() {
        
        when(jdbcTemplate.update(anyString(), eq("test"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq("test"))).thenReturn(1);
        
        TenantCapacity capacity = new TenantCapacity();
        capacity.setTenant("test");
        assertTrue(service.insertTenantCapacity(capacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq("test"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq("test"))).thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.insertTenantCapacity(capacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testIncrementUsageWithDefaultQuotaLimit() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test");
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
    void testIncrementUsageWithQuotaLimit() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test2");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2"))).thenReturn(1);
        
        assertTrue(service.incrementUsageWithQuotaLimit(tenantCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2"))).thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsageWithQuotaLimit(tenantCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testIncrementUsage() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test3");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3"))).thenReturn(1);
        
        assertTrue(service.incrementUsage(tenantCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3"))).thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsage(tenantCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testDecrementUsage() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test4");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4"))).thenReturn(1);
        
        assertTrue(service.decrementUsage(tenantCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4"))).thenThrow(new CannotGetJdbcConnectionException("conn fail"));
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
        
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenAnswer((Answer<Integer>) invocationOnMock -> {
            if (invocationOnMock.getArgument(1).equals(quota) && invocationOnMock.getArgument(2).equals(maxSize)
                    && invocationOnMock.getArgument(3).equals(maxAggrCount) && invocationOnMock.getArgument(4).equals(maxAggrSize)
                    && invocationOnMock.getArgument(5).equals(timestamp) && invocationOnMock.getArgument(6).equals(tenant)) {
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
        
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenAnswer((Answer<Integer>) invocationOnMock -> {
            if (invocationOnMock.getArgument(1).equals(quota) && invocationOnMock.getArgument(3).equals(tenant)) {
                return 1;
            }
            return 0;
        });
        assertTrue(service.updateQuota(tenant, quota));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.updateQuota(tenant, quota);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
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
    void testGetCapacityList4CorrectUsage() {
        
        List<TenantCapacity> list = new ArrayList<>();
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("test");
        list.add(tenantCapacity);
        long lastId = 1;
        int pageSize = 1;
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}), any(RowMapper.class))).thenReturn(list);
        List<TenantCapacity> ret = service.getCapacityList4CorrectUsage(lastId, pageSize);
        
        assertEquals(list.size(), ret.size());
        assertEquals(tenantCapacity.getTenant(), ret.get(0).getTenant());
        
        //mock get connection fail
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}), any(RowMapper.class))).thenThrow(
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
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.deleteTenantCapacity("test");
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testTenantCapacityRowMapper() throws SQLException {
        TenantCapacityPersistService.TenantCapacityRowMapper groupCapacityRowMapper = new TenantCapacityPersistService.TenantCapacityRowMapper();
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
        
        TenantCapacity groupCapacity = groupCapacityRowMapper.mapRow(rs, 1);
        assertEquals(quota, groupCapacity.getQuota().intValue());
        assertEquals(usage, groupCapacity.getUsage().intValue());
        assertEquals(maxSize, groupCapacity.getMaxSize().intValue());
        assertEquals(maxAggrCount, groupCapacity.getMaxAggrCount().intValue());
        assertEquals(maxAggrSize, groupCapacity.getMaxAggrSize().intValue());
        assertEquals(tenant, groupCapacity.getTenant());
    }
}
