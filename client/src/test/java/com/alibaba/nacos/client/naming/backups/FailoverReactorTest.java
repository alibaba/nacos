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
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

public class FailoverReactorTest {

    @Test
    public void testInit() throws NacosException, NoSuchFieldException, IllegalAccessException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        FailoverReactor failoverReactor = new FailoverReactor(holder, "/tmp", UUID.randomUUID().toString());
        Field executorService = FailoverReactor.class.getDeclaredField("executorService");
        executorService.setAccessible(true);
        ScheduledExecutorService o = (ScheduledExecutorService) executorService.get(failoverReactor);
        Assert.assertFalse(o.isShutdown());
        failoverReactor.shutdown();
        Assert.assertTrue(o.isShutdown());
    }

    @Test
    public void testAddDay() throws NacosException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        FailoverReactor failoverReactor = new FailoverReactor(holder, "/tmp", UUID.randomUUID().toString());
        Date date = new Date();
        Date actual = failoverReactor.addDay(date, 1);
        Assert.assertEquals(date.getTime() + 24 * 60 * 60 * 1000, actual.getTime());
        failoverReactor.shutdown();
    }

    @Test
    public void testIsFailoverSwitch() throws NacosException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        FailoverReactor failoverReactor = new FailoverReactor(holder, "/tmp", UUID.randomUUID().toString());
        Assert.assertFalse(failoverReactor.isFailoverSwitch());
        failoverReactor.shutdown();

    }

    @Test
    public void testGetService() throws NacosException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        FailoverReactor failoverReactor = new FailoverReactor(holder, "/tmp",UUID.randomUUID().toString());
        ServiceInfo info = failoverReactor.getService("aa@@bb");
        Assert.assertEquals(new ServiceInfo("aa@@bb").toString(), info.toString());
        failoverReactor.shutdown();
    }
}