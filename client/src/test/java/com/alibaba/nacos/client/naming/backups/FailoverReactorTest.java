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

package com.alibaba.nacos.client.naming.backups;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.common.utils.ReflectUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailoverReactorTest {
    
    @Mock
    ServiceInfoHolder holder;
    
    @Mock
    FailoverDataSource failoverDataSource;
    
    FailoverReactor failoverReactor;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        failoverReactor = new FailoverReactor(holder, UUID.randomUUID().toString());
        Field failoverDataSourceField = FailoverReactor.class.getDeclaredField("failoverDataSource");
        failoverDataSourceField.setAccessible(true);
        failoverDataSourceField.set(failoverReactor, failoverDataSource);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        failoverReactor.shutdown();
    }
    
    @Test
    void testIsFailoverSwitch() throws NacosException {
        assertFalse(failoverReactor.isFailoverSwitch());
        
    }
    
    @Test
    void testGetService() throws NacosException {
        ServiceInfo info = failoverReactor.getService("aa@@bb");
        assertEquals(new ServiceInfo("aa@@bb").toString(), info.toString());
    }
    
    @Test
    void testRefreshFromDisabledToEnabled() throws InterruptedException {
        // make sure the first no delay refresh thread finished.
        TimeUnit.MILLISECONDS.sleep(500);
        FailoverSwitch mockFailoverSwitch = new FailoverSwitch(true);
        when(failoverDataSource.getSwitch()).thenReturn(mockFailoverSwitch);
        Map<String, FailoverData> map = new HashMap<>();
        ServiceInfo serviceInfo = new ServiceInfo("a@@b");
        serviceInfo.addHost(new Instance());
        map.put("a@@b", NamingFailoverData.newNamingFailoverData(serviceInfo));
        when(failoverDataSource.getFailoverData()).thenReturn(map);
        // waiting refresh thread work
        TimeUnit.MILLISECONDS.sleep(5500);
        ServiceInfo actual = failoverReactor.getService("a@@b");
        assertEquals(serviceInfo, actual);
    }
    
    @Test
    void testRefreshFromDisabledToEnabledWithException() throws InterruptedException {
        // make sure the first no delay refresh thread finished.
        TimeUnit.MILLISECONDS.sleep(500);
        FailoverSwitch mockFailoverSwitch = new FailoverSwitch(true);
        when(failoverDataSource.getSwitch()).thenReturn(mockFailoverSwitch);
        when(failoverDataSource.getFailoverData()).thenReturn(null);
        // waiting refresh thread work
        TimeUnit.MILLISECONDS.sleep(5500);
        assertTrue(((Map) ReflectUtils.getFieldValue(failoverDataSource, "serviceMap", new HashMap<>())).isEmpty());
    }
    
    @Test
    void testRefreshFromEnabledToDisabled() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        // make sure the first no delay refresh thread finished.
        TimeUnit.MILLISECONDS.sleep(500);
        FailoverSwitch mockFailoverSwitch = new FailoverSwitch(false);
        when(failoverDataSource.getSwitch()).thenReturn(mockFailoverSwitch);
        Field failoverSwitchEnableField = FailoverReactor.class.getDeclaredField("failoverSwitchEnable");
        failoverSwitchEnableField.setAccessible(true);
        failoverSwitchEnableField.set(failoverReactor, true);
        Map<String, ServiceInfo> map = new HashMap<>();
        ServiceInfo serviceInfo = new ServiceInfo("a@@b");
        serviceInfo.addHost(new Instance());
        map.put("a@@b", serviceInfo);
        when(holder.getServiceInfoMap()).thenReturn(map);
        Field serviceMapField = FailoverReactor.class.getDeclaredField("serviceMap");
        serviceMapField.setAccessible(true);
        serviceMapField.set(failoverReactor, map);
        // waiting refresh thread work
        TimeUnit.MILLISECONDS.sleep(5500);
        ServiceInfo actual = failoverReactor.getService("a@@b");
        assertNotEquals(serviceInfo, actual);
    }
    
    @Test
    void testFailoverServiceCntMetrics()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = FailoverReactor.class.getDeclaredMethod("failoverServiceCntMetrics");
        method.setAccessible(true);
        method.invoke(failoverReactor);
        // No exception
    }
    
    @Test
    void testFailoverServiceCntMetricsClear()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        Method method = FailoverReactor.class.getDeclaredMethod("failoverServiceCntMetricsClear");
        method.setAccessible(true);
        method.invoke(failoverReactor);
        // No exception
    }
}
