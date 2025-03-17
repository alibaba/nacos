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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CapacityManagementAspectTest {
    
    final Boolean mockProceedingJoinPointResult = true;
    
    final String mockDataId = "mockDataId";
    
    final String mockGroup = "mockGroup";
    
    final String mockTenant = "mockTenant";
    
    @Mock
    private ConfigForm configForm;
    
    @Mock
    private ConfigRequestInfo configRequestInfo;
    
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
        // Mock static methods
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        when(PropertyUtil.getCorrectUsageDelay()).thenReturn(10 * 60);
        when(PropertyUtil.getDefaultMaxAggrSize()).thenReturn(1024);
        when(PropertyUtil.getDefaultMaxSize()).thenReturn(10 * 1024);
        
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false)).thenReturn(true);
        
        // Initialize the aspect with mocked dependencies
        capacityManagementAspect = new CapacityManagementAspect(configInfoPersistService, capacityService);
        
        // Mock the behavior of the ProceedingJoinPoint
        mockException = new RuntimeException("mock exception");
        when(localMockProceedingJoinPoint.proceed()).thenThrow(mockException);
    }
    
    @AfterEach
    void after() {
        // Close static mocks
        propertyUtilMockedStatic.close();
        envUtilMockedStatic.close();
    }
    
    @Test
    void testAroundPublishConfigForInsertAspect() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: false
        when(PropertyUtil.isManageCapacity()).thenReturn(false);
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult); 
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        Mockito.verify(configInfoPersistService, Mockito.times(0)).findConfigInfo(any(), any(), any());
        assert localMockResult.equals(mockProceedingJoinPointResult);
    }
    
    @Test
    void testAroundPublishConfigForInsertAspect1() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: true
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn(mockDataId);
        when(configForm.getGroup()).thenReturn(mockGroup);
        when(configForm.getNamespaceId()).thenReturn(mockTenant);
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(false);
        
        Exception exception = assertThrows(NacosException.class, () -> {
            capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        });
        
        assertEquals("Configuration limit exceeded [group=mockGroup, namespaceId=mockTenant].",
                exception.getMessage());
        Mockito.verify(proceedingJoinPoint, Mockito.times(0)).proceed();
    }
    
    @Test
    void testAroundPublishConfigForInsertAspect2Tenant() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: null
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult); 
        when(configForm.getDataId()).thenReturn(mockDataId);
        when(configForm.getGroup()).thenReturn(mockGroup);
        when(configForm.getNamespaceId()).thenReturn(mockTenant);
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(null);
        when(capacityService.updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant))).thenReturn(true);
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(capacityService, Mockito.times(1)).initTenantCapacity(eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundPublishConfigForInsertAspect2Group() throws Throwable {
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
        
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn(mockDataId);
        when(configForm.getGroup()).thenReturn(mockGroup);
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult); 
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(capacityService, Mockito.times(1)).initGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(1)).updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundPublishConfigForInsertAspect3Tenant() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant max size: true/false (if tenant max size is 0, will use default max size)
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult); 
        when(configForm.getDataId()).thenReturn(mockDataId);
        when(configForm.getGroup()).thenReturn(mockGroup);
        when(configForm.getNamespaceId()).thenReturn(mockTenant);
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant))).thenReturn(true);
        
        TenantCapacity localTenantCapacity = new TenantCapacity();
        localTenantCapacity.setTenant(mockTenant);
        localTenantCapacity.setMaxSize(0);
        localTenantCapacity.setMaxAggrCount(0);
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(localTenantCapacity);
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(capacityService, Mockito.times(0)).initTenantCapacity(eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        //  5. over tenant max size: true
        localTenantCapacity.setMaxSize(1);
        localTenantCapacity.setMaxAggrCount(1);
        
        Exception exception = assertThrows(NacosException.class, () -> {
            capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        });
        assertEquals("Configuration limit exceeded [group=mockGroup, namespaceId=mockTenant].",
                exception.getMessage());
        
        //  5. over tenant max size: true
        localTenantCapacity.setMaxSize(10 * 1024);
        localTenantCapacity.setMaxAggrCount(1024);
        localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
    }
    
    @Test
    void testAroundPublishConfigForInsertAspect3Group() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant max size: true/false (if tenant max size is 0, will use default max size)
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult);
        when(configForm.getDataId()).thenReturn(mockDataId);
        when(configForm.getGroup()).thenReturn(mockGroup);
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup))).thenReturn(true);
        
        GroupCapacity localGroupCapacity = new GroupCapacity();
        localGroupCapacity.setGroup(mockGroup);
        localGroupCapacity.setMaxSize(0);
        localGroupCapacity.setMaxAggrCount(0);
        when(capacityService.getGroupCapacity(eq(mockGroup))).thenReturn(localGroupCapacity);
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(true, localMockResult);
        Mockito.verify(capacityService, Mockito.times(0)).initGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(1)).updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        //  5. over tenant max size: true
        localGroupCapacity.setMaxSize(1);
        localGroupCapacity.setMaxAggrCount(1);
        Exception exception = assertThrows(NacosException.class, () -> {
            capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        });
        assertEquals("Configuration limit exceeded [group=mockGroup, namespaceId=null].", exception.getMessage());
        
        // 5. over tenant max size: true
        localGroupCapacity.setMaxSize(10 * 1024);
        localGroupCapacity.setMaxAggrCount(1024);
        localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
    }
    
    @Test
    void testAroundPublishAspectTenant() throws Throwable {
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant quota: false
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult); 
        when(configForm.getDataId()).thenReturn("dataId");
        when(configForm.getGroup()).thenReturn("group");
        when(configForm.getNamespaceId()).thenReturn("mockTenant");
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant))).thenReturn(true);
        
        TenantCapacity localTenantCapacity = new TenantCapacity();
        localTenantCapacity.setTenant(mockTenant);
        localTenantCapacity.setMaxSize(10 * 1024);
        localTenantCapacity.setMaxAggrCount(1024);
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(localTenantCapacity);
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(capacityService, Mockito.times(0)).initTenantCapacity(eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(0)).updateTenantUsage(eq(CounterMode.INCREMENT), eq(mockTenant));
        Mockito.verify(capacityService, Mockito.times(1)).getTenantCapacity(eq(mockTenant));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundPublishAspectGroup() throws Throwable {
        //condition:
        //  1. has tenant: false
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over group quota: false
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult); 
        when(configForm.getDataId()).thenReturn("dataId");
        when(configForm.getContent()).thenReturn("content");
        when(configForm.getGroup()).thenReturn(mockGroup);
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup))).thenReturn(true);
        
        GroupCapacity localGroupCapacity = new GroupCapacity();
        localGroupCapacity.setGroup(mockGroup);
        localGroupCapacity.setMaxSize(10 * 1024);
        localGroupCapacity.setMaxAggrCount(1024);
        when(capacityService.getGroupCapacity(eq(mockGroup))).thenReturn(localGroupCapacity);
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(capacityService, Mockito.times(0)).initGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(1)).getGroupCapacity(eq(mockGroup));
        Mockito.verify(capacityService, Mockito.times(0)).updateGroupUsage(eq(CounterMode.INCREMENT), eq(mockGroup));
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
    }
    
    @Test
    void testAroundPublishConfigForInsertRollbackAspect() throws Throwable {
        //test with insert
        //condition:
        //  1. has tenant: true
        //  2. capacity limit check: true
        //  3. over cluster quota: false
        //  4. tenant capacity: not null
        //  5. over tenant max size: true/false (if tenant max size is 0, will use default max size)
        when(PropertyUtil.isManageCapacity()).thenReturn(true);
        when(PropertyUtil.isCapacityLimitCheck()).thenReturn(true);
        when(localMockProceedingJoinPoint.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configForm.getGroup()).thenReturn("group");
        when(configForm.getNamespaceId()).thenReturn("mockTenant");
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.updateClusterUsage(any())).thenReturn(true);
        when(capacityService.updateTenantUsage(any(), eq(mockTenant))).thenReturn(true);
        
        TenantCapacity localTenantCapacity = new TenantCapacity();
        localTenantCapacity.setTenant(mockTenant);
        localTenantCapacity.setMaxSize(10 * 1024);
        localTenantCapacity.setMaxAggrCount(1024);
        when(capacityService.getTenantCapacity(eq(mockTenant))).thenReturn(localTenantCapacity);
        
        Boolean localMockResult = null;
        try {
            localMockResult = (Boolean) capacityManagementAspect.aroundPublishConfig(localMockProceedingJoinPoint);
        } catch (Throwable e) {
            assertEquals(mockException.getMessage(), e.getMessage());
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
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{mockDataId, mockGroup, mockTenant, null});
        when(localMockProceedingJoinPoint.getArgs()).thenReturn(new Object[]{mockDataId, mockGroup, mockTenant, null});
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult); 
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.insertAndUpdateTenantUsage(any(), eq(mockTenant), anyBoolean())).thenReturn(true);
        when(capacityService.updateClusterUsage(any())).thenReturn(true);
        when(capacityService.updateTenantUsage(any(), eq(mockTenant))).thenReturn(true);
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        localMockResult = (Boolean) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateClusterUsage(eq(CounterMode.DECREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateTenantUsage(eq(CounterMode.DECREMENT), eq(mockTenant), anyBoolean());
        Mockito.verify(proceedingJoinPoint, Mockito.times(2)).proceed();
        
        localMockResult = null;
        try {
            localMockResult = (Boolean) capacityManagementAspect.aroundDeleteConfig(localMockProceedingJoinPoint);
        } catch (Throwable e) {
            assertEquals(mockException.getMessage(), e.getMessage());
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
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{mockDataId, mockGroup, mockTenant, null});
        when(localMockProceedingJoinPoint.getArgs()).thenReturn(new Object[]{mockDataId, mockGroup, mockTenant, null});
        when(proceedingJoinPoint.proceed()).thenReturn(mockProceedingJoinPointResult);
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(null);
        when(capacityService.insertAndUpdateClusterUsage(any(), anyBoolean())).thenReturn(true);
        when(capacityService.insertAndUpdateGroupUsage(any(), eq(mockGroup), anyBoolean())).thenReturn(true);
        when(capacityService.updateClusterUsage(any())).thenReturn(true);
        when(capacityService.updateGroupUsage(any(), eq(mockGroup))).thenReturn(true);
        
        Boolean localMockResult = (Boolean) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(proceedingJoinPoint, Mockito.times(1)).proceed();
        
        when(configInfoPersistService.findConfigInfo(any(), any(), any())).thenReturn(new ConfigInfoWrapper());
        localMockResult = (Boolean) capacityManagementAspect.aroundDeleteConfig(proceedingJoinPoint);
        assertEquals(mockProceedingJoinPointResult, localMockResult);
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateClusterUsage(eq(CounterMode.DECREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1))
                .insertAndUpdateTenantUsage(eq(CounterMode.DECREMENT), eq(mockTenant), anyBoolean());
        Mockito.verify(proceedingJoinPoint, Mockito.times(2)).proceed();
        
        localMockResult = null;
        try {
            localMockResult = (Boolean) capacityManagementAspect.aroundDeleteConfig(localMockProceedingJoinPoint);
        } catch (Throwable e) {
            assertEquals(mockException.getMessage(), e.getMessage());
        }
        assertNull(localMockResult);
        Mockito.verify(capacityService, Mockito.times(2))
                .insertAndUpdateClusterUsage(eq(CounterMode.DECREMENT), anyBoolean());
        Mockito.verify(capacityService, Mockito.times(1)).updateClusterUsage(eq(CounterMode.INCREMENT));
        Mockito.verify(capacityService, Mockito.times(2))
                .insertAndUpdateTenantUsage(eq(CounterMode.DECREMENT), eq(mockTenant), anyBoolean());
        Mockito.verify(localMockProceedingJoinPoint, Mockito.times(1)).proceed();
    }
}