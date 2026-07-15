/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.naming.healthcheck.heartbeat.BeatCheckTask;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthCheckReactorTest {
    
    private Map<String, ScheduledFuture> futureMap;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new MockEnvironment());
        Field futureMapField = HealthCheckReactor.class.getDeclaredField("futureMap");
        futureMapField.setAccessible(true);
        futureMap = (Map<String, ScheduledFuture>) futureMapField.get(null);
        futureMap.clear();
    }
    
    @AfterEach
    void tearDown() {
        futureMap.clear();
    }
    
    @Test
    void testConstruct() {
        new HealthCheckReactor();
    }
    
    @Test
    void testScheduleCheckWithRawBeatTask() {
        BeatCheckTask task = mock(BeatCheckTask.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(task.taskKey()).thenReturn("rawBeat");
        try (MockedStatic<GlobalExecutor> globalExecutor =
            Mockito.mockStatic(GlobalExecutor.class)) {
            globalExecutor.when(() -> GlobalExecutor.scheduleNamingHealth(same(task), eq(5000L),
                eq(5000L),
                eq(TimeUnit.MILLISECONDS)))
                .thenReturn(future);
            
            HealthCheckReactor.scheduleCheck(task);
            
            globalExecutor.verify(() -> GlobalExecutor.scheduleNamingHealth(same(task), eq(5000L),
                eq(5000L), eq(TimeUnit.MILLISECONDS)));
        }
    }
    
    @Test
    void testCancelCheckCatchesException() {
        BeatCheckTask task = mock(BeatCheckTask.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(task.taskKey()).thenReturn("cancel");
        when(future.cancel(true)).thenThrow(new RuntimeException("failed"));
        futureMap.put("cancel", future);
        
        HealthCheckReactor.cancelCheck(task);
        
        verify(future).cancel(true);
        assertTrue(futureMap.containsKey("cancel"));
    }
}
