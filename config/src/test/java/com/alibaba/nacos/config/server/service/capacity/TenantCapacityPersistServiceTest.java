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
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.impl.mysql.TenantCapacityMapperByMySql;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
public class TenantCapacityPersistServiceTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private MapperManager mapperManager;
    
    @InjectMocks
    private TenantCapacityPersistService service;
    
    @Before
    public void setUp() {
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "dataSourceService", dataSourceService);
        ReflectionTestUtils.setField(service, "mapperManager", mapperManager);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        doReturn(new TenantCapacityMapperByMySql()).when(mapperManager)
                .findMapper(any(), eq(TableConstant.TENANT_CAPACITY));
    }
    
    @Test
    public void testGetTenantCapacity() {
        
        List<TenantCapacity> list = new ArrayList<>();
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("test");
        list.add(tenantCapacity);
        
        String tenantId = "testId";
        when(jdbcTemplate.query(anyString(), eq(new Object[] {tenantId}), any(RowMapper.class))).thenReturn(list);
        TenantCapacity ret = service.getTenantCapacity(tenantId);
        
        Assert.assertEquals(tenantCapacity.getTenant(), ret.getTenant());
    }
    
    @Test
    public void testInsertTenantCapacity() {
        
        when(jdbcTemplate.update(any(PreparedStatementCreator.class),
                argThat((ArgumentMatcher<GeneratedKeyHolder>) keyHolder -> {
                    List<Map<String, Object>> keyList = new ArrayList<>();
                    Map<String, Object> keyMap = new HashMap<>();
                    Number number = 1;
                    keyMap.put("test", number);
                    keyList.add(keyMap);
                    List<Map<String, Object>> expect = keyHolder.getKeyList();
                    expect.addAll(keyList);
                    return false;
                }))).thenReturn(1);
        
        TenantCapacity capacity = new TenantCapacity();
        capacity.setTenant("test");
        Assert.assertTrue(service.insertTenantCapacity(capacity));
    }
    
    @Test
    public void testIncrementUsageWithDefaultQuotaLimit() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test");
        tenantCapacity.setQuota(1);
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenReturn(1);
        
        Assert.assertTrue(service.incrementUsageWithDefaultQuotaLimit(tenantCapacity));
    }
    
    @Test
    public void testIncrementUsageWithQuotaLimit() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test2");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2"))).thenReturn(1);
        
        Assert.assertTrue(service.incrementUsageWithQuotaLimit(tenantCapacity));
    }
    
    @Test
    public void testIncrementUsage() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test3");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3"))).thenReturn(1);
        
        Assert.assertTrue(service.incrementUsage(tenantCapacity));
    }
    
    @Test
    public void testDecrementUsage() {
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        tenantCapacity.setGmtModified(timestamp);
        tenantCapacity.setTenant("test4");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4"))).thenReturn(1);
        
        Assert.assertTrue(service.decrementUsage(tenantCapacity));
    }
    
    @Test
    public void testUpdateTenantCapacity() {
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
                    && invocationOnMock.getArgument(3).equals(maxAggrCount) && invocationOnMock.getArgument(4)
                    .equals(maxAggrSize) && invocationOnMock.getArgument(5).equals(timestamp) && invocationOnMock
                    .getArgument(6).equals(tenant)) {
                return 1;
            }
            return 0;
        });
        Assert.assertTrue(service.updateTenantCapacity(tenant, quota, maxSize, maxAggrCount, maxAggrSize));
        
        timeUtilsMockedStatic.close();
    }
    
    @Test
    public void testUpdateQuota() {
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
        Assert.assertTrue(service.updateQuota(tenant, quota));
    }
    
    @Test
    public void testCorrectUsage() {
        
        String tenant = "test";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        when(jdbcTemplate.update(anyString(), eq(tenant), eq(timestamp), eq(tenant))).thenReturn(1);
        Assert.assertTrue(service.correctUsage(tenant, timestamp));
    }
    
    @Test
    public void testGetCapacityList4CorrectUsage() {
        
        List<TenantCapacity> list = new ArrayList<>();
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("test");
        list.add(tenantCapacity);
        long lastId = 1;
        int pageSize = 1;
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}), any(RowMapper.class)))
                .thenReturn(list);
        List<TenantCapacity> ret = service.getCapacityList4CorrectUsage(lastId, pageSize);
        
        Assert.assertEquals(list.size(), ret.size());
        Assert.assertEquals(tenantCapacity.getTenant(), ret.get(0).getTenant());
    }
    
    @Test
    public void testDeleteTenantCapacity() {
        
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenReturn(1);
        Assert.assertTrue(service.deleteTenantCapacity("test"));
    }
}
