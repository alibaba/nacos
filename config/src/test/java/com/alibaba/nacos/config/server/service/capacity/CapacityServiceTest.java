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

import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class CapacityServiceTest {
    
    private CapacityService service;
    
    @Mock
    private GroupCapacityPersistService groupCapacityPersistService;
    
    @Mock
    private TenantCapacityPersistService tenantCapacityPersistService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @BeforeEach
    void setUp() {
        service = new CapacityService();
        ReflectionTestUtils.setField(service, "groupCapacityPersistService", groupCapacityPersistService);
        ReflectionTestUtils.setField(service, "tenantCapacityPersistService", tenantCapacityPersistService);
        ReflectionTestUtils.setField(service, "configInfoPersistService", configInfoPersistService);
    }
    
    @Test
    void testInit() {
        service.init();
    }
    
    @Test
    void testCorrectUsage() {
        List<GroupCapacity> groupCapacityList = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setId(1L);
        groupCapacity.setGroup("testGroup");
        groupCapacityList.add(groupCapacity);
        when(groupCapacityPersistService.getCapacityList4CorrectUsage(0L, 100)).thenReturn(groupCapacityList);
        when(groupCapacityPersistService.getCapacityList4CorrectUsage(1L, 100)).thenReturn(new ArrayList<>());
        when(groupCapacityPersistService.correctUsage(eq("testGroup"), any())).thenReturn(true);
        
        List<TenantCapacity> tenantCapacityList = new ArrayList<>();
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setId(1L);
        tenantCapacity.setTenant("testTenant");
        tenantCapacityList.add(tenantCapacity);
        when(tenantCapacityPersistService.getCapacityList4CorrectUsage(0L, 100)).thenReturn(tenantCapacityList);
        when(tenantCapacityPersistService.getCapacityList4CorrectUsage(1L, 100)).thenReturn(new ArrayList<>());
        when(tenantCapacityPersistService.correctUsage(eq("testTenant"), any())).thenReturn(true);
        
        service.correctUsage();
        
        Mockito.verify(groupCapacityPersistService, times(1)).getCapacityList4CorrectUsage(0L, 100);
        Mockito.verify(groupCapacityPersistService, times(1)).getCapacityList4CorrectUsage(1L, 100);
        Mockito.verify(groupCapacityPersistService, times(1)).correctUsage(eq("testGroup"), any());
        
        Mockito.verify(tenantCapacityPersistService, times(1)).getCapacityList4CorrectUsage(0L, 100);
        Mockito.verify(tenantCapacityPersistService, times(1)).getCapacityList4CorrectUsage(1L, 100);
        Mockito.verify(tenantCapacityPersistService, times(1)).correctUsage(eq("testTenant"), any());
    }
    
    @Test
    void testCorrectGroupUsage() {
        when(groupCapacityPersistService.correctUsage(eq("testGroup"), any())).thenReturn(true);
        service.correctGroupUsage("testGroup");
        Mockito.verify(groupCapacityPersistService, times(1)).correctUsage(eq("testGroup"), any());
    }
    
    @Test
    void testCorrectTenantUsage() {
        when(tenantCapacityPersistService.correctUsage(eq("testTenant"), any())).thenReturn(true);
        service.correctTenantUsage("testTenant");
        Mockito.verify(tenantCapacityPersistService, times(1)).correctUsage(eq("testTenant"), any());
    }
    
    @Test
    void testInitAllCapacity() {
        List<String> groupList = new ArrayList<>();
        groupList.add("testGroup");
        when(configInfoPersistService.getGroupIdList(eq(1), eq(500))).thenReturn(groupList);
        List<String> tenantList = new ArrayList<>();
        tenantList.add("testTenant");
        when(configInfoPersistService.getTenantIdList(eq(1), eq(500))).thenReturn(tenantList);
        
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroup("testGroup");
        groupCapacity.setUsage(300);
        when(groupCapacityPersistService.insertGroupCapacity(any())).thenReturn(true);
        when(groupCapacityPersistService.getGroupCapacity(eq("testGroup"))).thenReturn(groupCapacity);
        when(groupCapacityPersistService.updateQuota(eq("testGroup"), eq(500))).thenReturn(true);
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("testTenant");
        tenantCapacity.setUsage(300);
        when(tenantCapacityPersistService.insertTenantCapacity(any())).thenReturn(true);
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(tenantCapacity);
        when(tenantCapacityPersistService.updateQuota(eq("testTenant"), eq(500))).thenReturn(true);
        
        service.initAllCapacity();
        
        Mockito.verify(groupCapacityPersistService, times(1)).insertGroupCapacity(any());
        Mockito.verify(groupCapacityPersistService, times(1)).getGroupCapacity(eq("testGroup"));
        Mockito.verify(groupCapacityPersistService, times(1)).updateQuota(eq("testGroup"), eq(500));
        
        Mockito.verify(tenantCapacityPersistService, times(1)).insertTenantCapacity(any());
        Mockito.verify(tenantCapacityPersistService, times(1)).getTenantCapacity(eq("testTenant"));
        Mockito.verify(tenantCapacityPersistService, times(1)).updateQuota(eq("testTenant"), eq(500));
    }
    
    @Test
    void testInsertAndUpdateClusterUsage() {
        when(groupCapacityPersistService.insertGroupCapacity(any())).thenReturn(true);
        when(groupCapacityPersistService.incrementUsage(any())).thenReturn(true);
        when(groupCapacityPersistService.incrementUsageWithDefaultQuotaLimit(any())).thenReturn(true);
        when(groupCapacityPersistService.decrementUsage(any())).thenReturn(true);
        
        service.insertAndUpdateClusterUsage(CounterMode.INCREMENT, true);
        Mockito.verify(groupCapacityPersistService, times(1)).incrementUsage(any());
        
        service.insertAndUpdateClusterUsage(CounterMode.INCREMENT, false);
        Mockito.verify(groupCapacityPersistService, times(1)).incrementUsageWithDefaultQuotaLimit(any());
        
        service.insertAndUpdateClusterUsage(CounterMode.DECREMENT, true);
        Mockito.verify(groupCapacityPersistService, times(1)).decrementUsage(any());
    }
    
    @Test
    void testUpdateClusterUsage() {
        when(groupCapacityPersistService.incrementUsageWithDefaultQuotaLimit(any())).thenReturn(true);
        when(groupCapacityPersistService.decrementUsage(any())).thenReturn(true);
        
        service.updateClusterUsage(CounterMode.INCREMENT);
        Mockito.verify(groupCapacityPersistService, times(1)).incrementUsageWithDefaultQuotaLimit(any());
        
        service.updateClusterUsage(CounterMode.DECREMENT);
        Mockito.verify(groupCapacityPersistService, times(1)).decrementUsage(any());
    }
    
    @Test
    void testInsertAndUpdateGroupUsage() {
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroup("testGroup");
        groupCapacity.setUsage(300);
        when(groupCapacityPersistService.getGroupCapacity("testGroup")).thenReturn(groupCapacity);
        when(groupCapacityPersistService.incrementUsage(any())).thenReturn(true);
        when(groupCapacityPersistService.incrementUsageWithDefaultQuotaLimit(any())).thenReturn(true);
        when(groupCapacityPersistService.decrementUsage(any())).thenReturn(true);
        
        service.insertAndUpdateGroupUsage(CounterMode.INCREMENT, "testGroup", true);
        Mockito.verify(groupCapacityPersistService, times(1)).incrementUsage(any());
        
        service.insertAndUpdateClusterUsage(CounterMode.INCREMENT, false);
        Mockito.verify(groupCapacityPersistService, times(1)).incrementUsageWithDefaultQuotaLimit(any());
        
        service.insertAndUpdateClusterUsage(CounterMode.DECREMENT, true);
        Mockito.verify(groupCapacityPersistService, times(1)).decrementUsage(any());
    }
    
    @Test
    void testUpdateGroupUsage() {
        when(groupCapacityPersistService.incrementUsageWithDefaultQuotaLimit(any())).thenReturn(true);
        when(groupCapacityPersistService.decrementUsage(any())).thenReturn(true);
        
        service.updateGroupUsage(CounterMode.INCREMENT, "testGroup");
        Mockito.verify(groupCapacityPersistService, times(1)).incrementUsageWithDefaultQuotaLimit(any());
        
        service.updateGroupUsage(CounterMode.DECREMENT, "testGroup");
        Mockito.verify(groupCapacityPersistService, times(1)).decrementUsage(any());
    }
    
    @Test
    void testGetGroupCapacity() {
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setId(1L);
        groupCapacity.setGroup("testGroup");
        when(groupCapacityPersistService.getGroupCapacity(eq("testGroup"))).thenReturn(groupCapacity);
        
        GroupCapacity resGroupCapacity = service.getGroupCapacity("testGroup");
        assertEquals(groupCapacity.getId(), resGroupCapacity.getId());
        assertEquals(groupCapacity.getGroup(), resGroupCapacity.getGroup());
    }
    
    @Test
    void testInitGroupCapacity() {
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroup("testGroup");
        groupCapacity.setUsage(300);
        when(groupCapacityPersistService.insertGroupCapacity(any())).thenReturn(true);
        when(groupCapacityPersistService.getGroupCapacity(eq("testGroup"))).thenReturn(groupCapacity);
        when(groupCapacityPersistService.updateQuota(eq("testGroup"), eq(500))).thenReturn(true);
        
        service.initGroupCapacity("testGroup");
        Mockito.verify(groupCapacityPersistService, times(1)).insertGroupCapacity(any());
        Mockito.verify(groupCapacityPersistService, times(1)).getGroupCapacity(eq("testGroup"));
        Mockito.verify(groupCapacityPersistService, times(1)).updateQuota(eq("testGroup"), eq(500));
    }
    
    @Test
    void testGetCapacity() {
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setId(1L);
        when(groupCapacityPersistService.getGroupCapacity(eq("testGroup"))).thenReturn(groupCapacity);
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setId(2L);
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(tenantCapacity);
        
        Capacity resCapacity1 = service.getCapacity("testGroup", null);
        assertEquals(1L, resCapacity1.getId().longValue());
        
        Capacity resCapacity2 = service.getCapacity(null, "testTenant");
        assertEquals(2L, resCapacity2.getId().longValue());
    }
    
    @Test
    void testGetCapacityWithDefault() {
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setQuota(0);
        tenantCapacity.setMaxSize(0);
        tenantCapacity.setMaxAggrCount(0);
        tenantCapacity.setMaxAggrSize(0);
        when(tenantCapacityPersistService.getTenantCapacity(anyString())).thenReturn(tenantCapacity);
        
        GroupCapacity groupCapacity1 = new GroupCapacity();
        groupCapacity1.setQuota(0);
        groupCapacity1.setMaxSize(0);
        groupCapacity1.setMaxAggrCount(0);
        groupCapacity1.setMaxAggrSize(0);
        when(groupCapacityPersistService.getGroupCapacity(anyString())).thenReturn(groupCapacity1);
        
        //group is null
        Capacity resCapacity1 = service.getCapacityWithDefault(null, "testTenant");
        assertEquals(PropertyUtil.getDefaultGroupQuota(), resCapacity1.getQuota().intValue());
        assertEquals(PropertyUtil.getDefaultMaxSize(), resCapacity1.getMaxSize().intValue());
        assertEquals(PropertyUtil.getDefaultMaxAggrCount(), resCapacity1.getMaxAggrCount().intValue());
        assertEquals(PropertyUtil.getDefaultMaxAggrSize(), resCapacity1.getMaxAggrSize().intValue());
        
        //group is GroupCapacityPersistService.CLUSTER
        Capacity resCapacity2 = service.getCapacityWithDefault(GroupCapacityPersistService.CLUSTER, null);
        assertEquals(PropertyUtil.getDefaultClusterQuota(), resCapacity2.getQuota().intValue());
        assertEquals(PropertyUtil.getDefaultMaxSize(), resCapacity2.getMaxSize().intValue());
        assertEquals(PropertyUtil.getDefaultMaxAggrCount(), resCapacity2.getMaxAggrCount().intValue());
        assertEquals(PropertyUtil.getDefaultMaxAggrSize(), resCapacity2.getMaxAggrSize().intValue());
        
        GroupCapacity groupCapacity2 = new GroupCapacity();
        groupCapacity2.setQuota(0);
        groupCapacity2.setMaxSize(0);
        groupCapacity2.setMaxAggrCount(0);
        groupCapacity2.setMaxAggrSize(0);
        when(groupCapacityPersistService.getGroupCapacity(anyString())).thenReturn(groupCapacity2);
        
        //tenant is null
        Capacity resCapacity3 = service.getCapacityWithDefault("testGroup", null);
        assertEquals(PropertyUtil.getDefaultGroupQuota(), resCapacity3.getQuota().intValue());
        assertEquals(PropertyUtil.getDefaultMaxSize(), resCapacity3.getMaxSize().intValue());
        assertEquals(PropertyUtil.getDefaultMaxAggrCount(), resCapacity3.getMaxAggrCount().intValue());
        assertEquals(PropertyUtil.getDefaultMaxAggrSize(), resCapacity3.getMaxAggrSize().intValue());
    }
    
    @Test
    void testInitCapacityV1() {
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setUsage(300);
        when(groupCapacityPersistService.getGroupCapacity(eq("testGroup"))).thenReturn(groupCapacity);
        when(groupCapacityPersistService.insertGroupCapacity(any())).thenReturn(true);
        when(groupCapacityPersistService.updateQuota(eq("testGroup"), eq(500))).thenReturn(true);
        
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setUsage(300);
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(tenantCapacity);
        when(tenantCapacityPersistService.insertTenantCapacity(any())).thenReturn(true);
        when(tenantCapacityPersistService.updateQuota(eq("testTenant"), eq(500))).thenReturn(true);
        
        service.initCapacity("testGroup", null);
        Mockito.verify(groupCapacityPersistService, times(1)).getGroupCapacity(eq("testGroup"));
        Mockito.verify(groupCapacityPersistService, times(1)).insertGroupCapacity(any());
        Mockito.verify(groupCapacityPersistService, times(1)).updateQuota(eq("testGroup"), eq(500));
        
        service.initCapacity(null, "testTenant");
        Mockito.verify(tenantCapacityPersistService, times(1)).getTenantCapacity(eq("testTenant"));
        Mockito.verify(tenantCapacityPersistService, times(1)).insertTenantCapacity(any());
        Mockito.verify(tenantCapacityPersistService, times(1)).updateQuota(eq("testTenant"), eq(500));
    }
    
    @Test
    void testInitCapacityV2() {
        when(groupCapacityPersistService.insertGroupCapacity(any())).thenReturn(true);
        
        service.initCapacity(GroupCapacityPersistService.CLUSTER, null);
        Mockito.verify(groupCapacityPersistService, times(1)).insertGroupCapacity(any());
    }
    
    @Test
    void testInsertAndUpdateTenantUsage() {
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("testTenant");
        tenantCapacity.setUsage(300);
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(tenantCapacity);
        when(tenantCapacityPersistService.incrementUsage(any())).thenReturn(true);
        when(tenantCapacityPersistService.incrementUsageWithDefaultQuotaLimit(any())).thenReturn(true);
        when(tenantCapacityPersistService.decrementUsage(any())).thenReturn(true);
        
        service.insertAndUpdateTenantUsage(CounterMode.INCREMENT, "testTenant", true);
        Mockito.verify(tenantCapacityPersistService, times(1)).incrementUsage(any());
        
        service.insertAndUpdateTenantUsage(CounterMode.INCREMENT, "testTenant", false);
        Mockito.verify(tenantCapacityPersistService, times(1)).incrementUsageWithDefaultQuotaLimit(any());
        
        service.insertAndUpdateTenantUsage(CounterMode.DECREMENT, "testTenant", true);
        Mockito.verify(tenantCapacityPersistService, times(1)).decrementUsage(any());
    }
    
    @Test
    void testUpdateTenantUsage() {
        when(tenantCapacityPersistService.incrementUsageWithDefaultQuotaLimit(any())).thenReturn(true);
        when(tenantCapacityPersistService.decrementUsage(any())).thenReturn(true);
        
        service.updateTenantUsage(CounterMode.INCREMENT, "testTenant");
        Mockito.verify(tenantCapacityPersistService, times(1)).incrementUsageWithDefaultQuotaLimit(any());
        
        service.updateTenantUsage(CounterMode.DECREMENT, "testTenant");
        Mockito.verify(tenantCapacityPersistService, times(1)).decrementUsage(any());
    }
    
    @Test
    void testInitTenantCapacityV1() {
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("testTenant");
        tenantCapacity.setUsage(300);
        when(tenantCapacityPersistService.insertTenantCapacity(any())).thenReturn(true);
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(tenantCapacity);
        when(tenantCapacityPersistService.updateQuota(eq("testTenant"), eq(500))).thenReturn(true);
        
        service.initTenantCapacity("testTenant");
        Mockito.verify(tenantCapacityPersistService, times(1)).insertTenantCapacity(any());
        Mockito.verify(tenantCapacityPersistService, times(1)).getTenantCapacity(eq("testTenant"));
        Mockito.verify(tenantCapacityPersistService, times(1)).updateQuota(eq("testTenant"), eq(500));
    }
    
    @Test
    void testInitTenantCapacityV2() {
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("testTenant");
        tenantCapacity.setUsage(300);
        when(tenantCapacityPersistService.insertTenantCapacity(any())).thenReturn(true);
        
        service.initTenantCapacity("testTenant", 0, 0, 0, 0);
        Mockito.verify(tenantCapacityPersistService, times(1)).insertTenantCapacity(any());
    }
    
    @Test
    void testGetTenantCapacity() {
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setId(1L);
        tenantCapacity.setTenant("testTenant");
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(tenantCapacity);
        
        TenantCapacity resTenantCapacity = service.getTenantCapacity("testTenant");
        assertEquals(tenantCapacity.getId(), resTenantCapacity.getId());
        assertEquals(tenantCapacity.getTenant(), resTenantCapacity.getTenant());
    }
    
    @Test
    void testInsertOrUpdateCapacityV1() {
        //tenant is null
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setUsage(300);
        when(groupCapacityPersistService.getGroupCapacity(eq("testGroup"))).thenReturn(groupCapacity);
        when(groupCapacityPersistService.updateGroupCapacity(eq("testGroup"), eq(0), eq(0), eq(0), eq(0))).thenReturn(true);
        service.insertOrUpdateCapacity("testGroup", null, 0, 0, 0, 0);
        Mockito.verify(groupCapacityPersistService, times(1)).getGroupCapacity(eq("testGroup"));
        Mockito.verify(groupCapacityPersistService, times(1)).updateGroupCapacity(eq("testGroup"), eq(0), eq(0), eq(0), eq(0));
        
        //tenant is not null
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant("testTenant");
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(tenantCapacity);
        when(tenantCapacityPersistService.updateTenantCapacity(eq("testTenant"), eq(0), eq(0), eq(0), eq(0))).thenReturn(true);
        service.insertOrUpdateCapacity(null, "testTenant", 0, 0, 0, 0);
        Mockito.verify(tenantCapacityPersistService, times(1)).getTenantCapacity(eq("testTenant"));
        Mockito.verify(tenantCapacityPersistService, times(1)).updateTenantCapacity(eq("testTenant"), eq(0), eq(0), eq(0), eq(0));
    }
    
    @Test
    void testInsertOrUpdateCapacityV2() {
        when(groupCapacityPersistService.getGroupCapacity(eq("testGroup"))).thenReturn(null);
        when(groupCapacityPersistService.insertGroupCapacity(any())).thenReturn(true);
        service.insertOrUpdateCapacity("testGroup", null, 0, 0, 0, 0);
        Mockito.verify(groupCapacityPersistService, times(1)).getGroupCapacity(eq("testGroup"));
        Mockito.verify(groupCapacityPersistService, times(1)).insertGroupCapacity(any());
        
        when(tenantCapacityPersistService.getTenantCapacity(eq("testTenant"))).thenReturn(null);
        when(tenantCapacityPersistService.insertTenantCapacity(any())).thenReturn(true);
        service.insertOrUpdateCapacity(null, "testTenant", 0, 0, 0, 0);
        Mockito.verify(tenantCapacityPersistService, times(1)).getTenantCapacity(eq("testTenant"));
        Mockito.verify(tenantCapacityPersistService, times(1)).insertTenantCapacity(any());
    }
}
