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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.common.utils.ReflectUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FailoverReactorTest {
    
    @Mock
    ServiceInfoHolder holder;
    
    @Mock
    FailoverDataSource failoverDataSource;
    
    FailoverReactor failoverReactor;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        failoverReactor = new FailoverReactor(holder, UUID.randomUUID().toString());
        Field failoverDataSourceField = FailoverReactor.class.getDeclaredField("failoverDataSource");
        failoverDataSourceField.setAccessible(true);
        failoverDataSourceField.set(failoverReactor, failoverDataSource);
    }
    
    @After
    public void tearDown() throws NacosException {
        failoverReactor.shutdown();
    }
    
    @Test
    public void testIsFailoverSwitch() throws NacosException {
        Assert.assertFalse(failoverReactor.isFailoverSwitch());
        
    }
    
    @Test
    public void testGetService() throws NacosException {
        ServiceInfo info = failoverReactor.getService("aa@@bb");
        assertEquals(new ServiceInfo("aa@@bb").toString(), info.toString());
    }
    
    @Test
    public void testRefreshFromDisabledToEnabled() throws InterruptedException {
        // make sure the first no delay refresh thread finished.
        TimeUnit.MILLISECONDS.sleep(500);
        FailoverSwitch mockFailoverSwitch = new FailoverSwitch(true);
        when(failoverDataSource.getSwitch()).thenReturn(mockFailoverSwitch);
        Map<String, FailoverData> map = new HashMap<>();
        ServiceInfo serviceInfo = new ServiceInfo("a@@b");
        map.put("a@@b", NamingFailoverData.newNamingFailoverData(serviceInfo));
        when(failoverDataSource.getFailoverData()).thenReturn(map);
        when(holder.isChangedServiceInfo(any(), any())).thenReturn(true);
        // waiting refresh thread work
        TimeUnit.MILLISECONDS.sleep(5500);
        ServiceInfo actual = failoverReactor.getService("a@@b");
        assertEquals(serviceInfo, actual);
    }
    
    @Test
    public void testRefreshFromDisabledToEnabledWithException() throws InterruptedException {
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
    public void testRefreshFromEnabledToDisabled()
            throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        // make sure the first no delay refresh thread finished.
        TimeUnit.MILLISECONDS.sleep(500);
        FailoverSwitch mockFailoverSwitch = new FailoverSwitch(false);
        when(failoverDataSource.getSwitch()).thenReturn(mockFailoverSwitch);
        Field failoverSwitchEnableField = FailoverReactor.class.getDeclaredField("failoverSwitchEnable");
        failoverSwitchEnableField.setAccessible(true);
        failoverSwitchEnableField.set(failoverReactor, true);
        Map<String, ServiceInfo> map = new HashMap<>();
        ServiceInfo serviceInfo = new ServiceInfo("a@@b");
        map.put("a@@b", serviceInfo);
        when(holder.getServiceInfoMap()).thenReturn(map);
        Field serviceMapField = FailoverReactor.class.getDeclaredField("serviceMap");
        serviceMapField.setAccessible(true);
        serviceMapField.set(failoverReactor, map);
        when(holder.isChangedServiceInfo(any(), any())).thenReturn(true);
        // waiting refresh thread work
        TimeUnit.MILLISECONDS.sleep(5500);
        ServiceInfo actual = failoverReactor.getService("a@@b");
        assertNotEquals(serviceInfo, actual);
    }
    
    @Test
    public void testFailoverServiceCntMetrics()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = FailoverReactor.class.getDeclaredMethod("failoverServiceCntMetrics");
        method.setAccessible(true);
        method.invoke(failoverReactor);
        // No exception
    }
    
    @Test
    public void testFailoverServiceCntMetricsClear()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        Field field = FailoverReactor.class.getDeclaredField("meterMap");
        field.setAccessible(true);
        field.set(failoverReactor, Collections.singletonMap("a", null));
        Method method = FailoverReactor.class.getDeclaredMethod("failoverServiceCntMetricsClear");
        method.setAccessible(true);
        method.invoke(failoverReactor);
        // No exception
    }
}
