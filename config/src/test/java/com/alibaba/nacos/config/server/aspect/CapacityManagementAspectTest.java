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

package com.alibaba.nacos.config.server.aspect;

import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static com.alibaba.nacos.config.server.aspect.CapacityManagementAspect.LimitType.OVER_CLUSTER_QUOTA;
import static com.alibaba.nacos.config.server.aspect.CapacityManagementAspect.LimitType.OVER_MAX_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CapacityManagementAspectTest {
    
    final String mockProceedingJoinPointResult = "mock success return";
    
    final String mockDataId = "mockDataId";
    
    final String mockGroup = "mockGroup";
    
    final String mockTenant = "mockTenant";
    
    final String mockContent = "mockContent";
    
    @Mock
    ProceedingJoinPoint proceedingJoinPoint;
    
    @Mock
    ProceedingJoinPoint localMockProceedingJoinPoint;
    
    RuntimeException mockException = new RuntimeException("mock exception");
    
    CapacityManagementAspect capacityManagementAspect;
    
    @Mock
    CapacityService capacityService;
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeEach
    void before() throws Throwable {
        //PropertyUtil.isCapacityLimitCheck()
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        when(PropertyUtil.getCorrectUsageDelay()).thenReturn(10 * 60);
        when(PropertyUtil.getDefaultMaxAggrSize()).thenReturn(1024);
        when(PropertyUtil.getDefaultMaxSize()).thenReturn(10 * 1024);
        
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false)).thenReturn(true);
        
        capacityManagementAspect = new CapacityManagementAspect(configInfoPersistService, capacityService);
        
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult);
        
        when(localMockProceedingJoinPoint.proceed()).thenThrow(mockException);
    }
    
    @AfterEach
    void after() {
        propertyUtilMockedStatic.close();
        envUtilMockedStatic.close();
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForInsertAspect() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: false
        when(PropertyUtil.isManageCapacity()).thenReturn(false);
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                mockTenant, null);
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        Mockito.verify(configInfoPersistService, Mockito.times(0)).findConfigInfo(any(), any(), any());
        assert localMockResult.equals(mockProceedingJoinPointResult);
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForInsertAspect1() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: true
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(false);
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                mockTenant, null);
        assertEquals(localMockResult, String.valueOf(OVER_CLUSTER_QUOTA.status));
        Mockito.verify(proceedingJoinPoint, Mockito.times(0)).proceed();
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForInsertAspect2Tenant() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: null
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(null);
        when(capacityService.updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant))).thenReturn(true);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                mockTenant, null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(1)).initTenantCapacity(eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForInsertAspect2Group() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: false
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. group capacity: null
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        
        when(capacityService.getGroupCapacity(eq(mockGroup))).thenReturn(null);
        when(capacityService.updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup))).thenReturn(true);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null, null,
                null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(1)).initGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(1)).updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForInsertAspect3Tenant() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant max size: true/false (if tenant max size is 0, will use default max size)
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant))).thenReturn(true);
        TenantCapacity localTenantCapacity = new TenantCapacity();
        localTenantCapacity.setTenant(mockTenant);
        localTenantCapacity.setMaxSize(0);
        localTenantCapacity.setMaxAggrCount(0);
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(localTenantCapacity);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                mockTenant, null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(0)).initTenantCapacity(eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        //  5. over tenant max size: true
        localTenantCapacity.setMaxSize(1);
        localTenantCapacity.setMaxAggrCount(1);
        localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                mockTenant, null);
        assertEquals(localMockResult, String.valueOf(OVER_MAX_SIZE.status));
        
        //  5. over tenant max size: true
        localTenantCapacity.setMaxSize(10 * 1024);
        localTenantCapacity.setMaxAggrCount(1024);
        localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                mockTenant, null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForInsertAspect3Group() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant max size: true/false (if tenant max size is 0, will use default max size)
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup))).thenReturn(true);
        GroupCapacity localGroupCapacity = new GroupCapacity();
        localGroupCapacity.setGroup(mockGroup);
        localGroupCapacity.setMaxSize(0);
        localGroupCapacity.setMaxAggrCount(0);
        when(capacityService.getGroupCapacity(eq(mockGroup))).thenReturn(localGroupCapacity);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null, null,
                null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(0)).initGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(1)).updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        //  5. over tenant max size: true
        localGroupCapacity.setMaxSize(1);
        localGroupCapacity.setMaxAggrCount(1);
        localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null, null,
                null);
        assertEquals(localMockResult, String.valueOf(OVER_MAX_SIZE.status));
        
        //  5. over tenant max size: true
        localGroupCapacity.setMaxSize(10 * 1024);
        localGroupCapacity.setMaxAggrCount(1024);
        localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null, null,
                null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForUpdateAspectTenant() throws Throwable {
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant quota: false
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant))).thenReturn(true);
        TenantCapacity localTenantCapacity = new TenantCapacity();
        localTenantCapacity.setTenant(mockTenant);
        localTenantCapacity.setMaxSize(10 * 1024);
        localTenantCapacity.setMaxAggrCount(1024);
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(localTenantCapacity);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                mockTenant, null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(0)).initTenantCapacity(eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(0)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).getTenantCapacity(eq(mockTenant));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForUpdateAspectGroup() throws Throwable {
        //condition:
        //  1. has tenant: false
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over group quota: false
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup))).thenReturn(true);
        GroupCapacity localGroupCapacity = new GroupCapacity();
        localGroupCapacity.setGroup(mockGroup);
        localGroupCapacity.setMaxSize(10 * 1024);
        localGroupCapacity.setMaxAggrCount(1024);
        when(capacityService.getGroupCapacity(eq(mockGroup))).thenReturn(localGroupCapacity);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null, null,
                null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(0)).initGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(1)).getGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(0)).updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundSyncUpdateConfigAllForInsertRollbackAspect() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant max size: true/false (if tenant max size is 0, will use default max size)
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateClusterUsage(any())).thenReturn(true);
        when(capacityService.updateTenantUsage(any(), eq(mockTenant))).thenReturn(true);
        TenantCapacity localTenantCapacity = new TenantCapacity();
        localTenantCapacity.setTenant(mockTenant);
        localTenantCapacity.setMaxSize(10 * 1024);
        localTenantCapacity.setMaxAggrCount(1024);
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(localTenantCapacity);
        
        String localMockResult = null;
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        try {
            localMockResult = (String) capacityManagementAspect.aroundSyncUpdateConfigAll(localMockProceedingJoinPoint,
                    mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockContent, null, null,
                    mockTenant, null);
        } catch (Throwable e) {
            assertEquals(e.getMessage(), mockException.getMessage());
        }
        assertNull(localMockResult);
        Mockito.verify(capacityService, Mockito.times(0)).initTenantCapacity(eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).updateTenantUsage(eq(CounterMode.DECREMENT), eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateClusterUsage(eq(CounterMode.INCREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1)).updateClusterUsage(eq(CounterMode.DECREMENT));
        Mockito.verify(localMockProceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundDeleteConfigForTenant() throws Throwable {
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.insertAndUpdateTenantUsage(any(), eq(mockTenant), anyBoolean())).thenReturn(true);
        when(capacityService.updateClusterUsage(any())).thenReturn(true);
        when(capacityService.updateTenantUsage(any(), eq(mockTenant))).thenReturn(true);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockTenant);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        localMockResult = (String) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockTenant);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateClusterUsage(eq(CounterMode.DECREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateTenantUsage(eq(CounterMode.DECREMENT), eq(mockTenant), anyBoolean());
        Mockito.verify(proceedingJoinPoint, Mockito.times(2)).proceed();
        
        localMockResult = null;
        try {
            localMockResult = (String) capacityManagementAspect.aroundDeleteConfig(localMockProceedingJoinPoint,
                    mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, mockTenant);
        } catch (Throwable e) {
            assertEquals(e.getMessage(), mockException.getMessage());
        }
        assertNull(localMockResult);
        Mockito.verify(capacityService, Mockito.times(2))
                .insertAndUpdateClusterUsage(eq(CounterMode.DECREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1)).updateClusterUsage(eq(CounterMode.INCREMENT));
        Mockito.verify(capacityService, Mockito.times(2))
                .insertAndUpdateTenantUsage(eq(CounterMode.DECREMENT), eq(mockTenant), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(localMockProceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundDeleteConfigForGroup() throws Throwable {
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.insertAndUpdateGroupUsage(any(), eq(mockGroup), anyBoolean())).thenReturn(true);
        when(capacityService.updateClusterUsage(any())).thenReturn(true);
        when(capacityService.updateGroupUsage(any(), eq(mockGroup))).thenReturn(true);
        
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        String localMockResult = (String) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        localMockResult = (String) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint,
                mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, null);
        assertEquals(localMockResult, mockProceedingJoinPointResult);
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateClusterUsage(eq(CounterMode.DECREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateGroupUsage(eq(CounterMode.DECREMENT), eq(mockGroup), anyBoolean());
        Mockito.verify(proceedingJoinPoint, Mockito.times(2)).proceed();
        
        localMockResult = null;
        try {
            localMockResult = (String) capacityManagementAspect.aroundDeleteConfig(localMockProceedingJoinPoint,
                    mockHttpServletRequest, mockHttpServletResponse, mockDataId, mockGroup, null);
        } catch (Throwable e) {
            assertEquals(e.getMessage(), mockException.getMessage());
        }
        assertNull(localMockResult);
        Mockito.verify(capacityService, Mockito.times(2))
                .insertAndUpdateClusterUsage(eq(CounterMode.DECREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1)).updateClusterUsage(eq(CounterMode.INCREMENT));
        Mockito.verify(capacityService, Mockito.times(2))
                .insertAndUpdateGroupUsage(eq(CounterMode.DECREMENT), eq(mockGroup), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1)).updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup));
        Mockito.verify(localMockProceedingJoinPoint, Mockito.times(1)).proceed();
    }
}
