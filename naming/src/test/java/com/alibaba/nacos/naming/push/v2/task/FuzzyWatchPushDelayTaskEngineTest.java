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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.v2.executor.PushExecutorDelegate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuzzyWatchPushDelayTaskEngineTest {
    
    private static final String SERVICE_KEY = "namespace@@group@@service";
    
    private static final String CLIENT_ID = "connection-1";
    
    @Mock
    private PushExecutorDelegate pushExecutor;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private NacosTaskProcessor processor;
    
    @Mock
    private NamingExecuteTaskDispatcher dispatcher;
    
    private TestFuzzyWatchPushDelayTaskEngine executeEngine;
    
    @BeforeEach
    void setUp() {
        executeEngine = new TestFuzzyWatchPushDelayTaskEngine(pushExecutor, switchDomain);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        executeEngine.shutdown();
    }
    
    @Test
    void testConstructAndGetPushExecutor() {
        assertSame(pushExecutor, executeEngine.getPushExecutor());
    }
    
    @Test
    void testProcessTasksSkippedWhenPushDisabled() {
        when(switchDomain.isPushEnabled()).thenReturn(false);
        TestDelayTask task = new TestDelayTask();
        executeEngine.addProcessor("key", processor);
        executeEngine.addTask("key", task);
        
        executeEngine.processTasksForTest();
        
        assertEquals(1, executeEngine.size());
        verify(processor, never()).process(any(NacosTask.class));
    }
    
    @Test
    void testProcessTasksWhenPushEnabled() {
        when(switchDomain.isPushEnabled()).thenReturn(true);
        when(processor.process(any(NacosTask.class))).thenReturn(true);
        TestDelayTask task = new TestDelayTask();
        executeEngine.addProcessor("key", processor);
        executeEngine.addTask("key", task);
        
        executeEngine.processTasksForTest();
        
        assertTrue(executeEngine.isEmpty());
        verify(processor).process(task);
    }
    
    @Test
    void testDefaultProcessorDispatchesChangeNotifyTask() {
        try (MockedStatic<NamingExecuteTaskDispatcher> mockedDispatcher =
            Mockito.mockStatic(NamingExecuteTaskDispatcher.class)) {
            mockedDispatcher.when(NamingExecuteTaskDispatcher::getInstance)
                .thenReturn(dispatcher);
            FuzzyWatchChangeNotifyTask task =
                new FuzzyWatchChangeNotifyTask(SERVICE_KEY, ADD_SERVICE, CLIENT_ID, 0L);
            
            assertTrue(executeEngine.getProcessor("unknown").process(task));
            
            verify(dispatcher).dispatchAndExecuteTask(eq(
                FuzzyWatchPushDelayTaskEngine.getTaskKey(task)),
                any(FuzzyWatchChangeNotifyExecuteTask.class));
        }
    }
    
    @Test
    void testDefaultProcessorDispatchesSyncNotifyTask() {
        try (MockedStatic<NamingExecuteTaskDispatcher> mockedDispatcher =
            Mockito.mockStatic(NamingExecuteTaskDispatcher.class)) {
            mockedDispatcher.when(NamingExecuteTaskDispatcher::getInstance)
                .thenReturn(dispatcher);
            FuzzyWatchSyncNotifyTask task =
                new FuzzyWatchSyncNotifyTask(CLIENT_ID, "group@@service*",
                    FUZZY_WATCH_INIT_NOTIFY, Collections.emptySet(), 0L);
            task.setCurrentBatch(2);
            
            assertTrue(executeEngine.getProcessor("unknown").process(task));
            
            verify(dispatcher).dispatchAndExecuteTask(eq(
                FuzzyWatchPushDelayTaskEngine.getTaskKey(task)),
                any(FuzzyWatchSyncNotifyExecuteTask.class));
        }
    }
    
    @Test
    void testDefaultProcessorIgnoresUnknownTask() {
        assertTrue(executeEngine.getProcessor("unknown").process(new UnknownTask()));
    }
    
    @Test
    void testGetTaskKey() {
        FuzzyWatchChangeNotifyTask changeTask =
            new FuzzyWatchChangeNotifyTask(SERVICE_KEY, ADD_SERVICE, CLIENT_ID, 0L);
        FuzzyWatchSyncNotifyTask syncTask =
            new FuzzyWatchSyncNotifyTask(CLIENT_ID, "group@@service*",
                FUZZY_WATCH_INIT_NOTIFY, Collections.emptySet(), 0L);
        syncTask.setCurrentBatch(2);
        
        assertEquals("fwcnT-connection-1namespace@@group@@service",
            FuzzyWatchPushDelayTaskEngine.getTaskKey(changeTask));
        assertEquals("fwsnT-" + FUZZY_WATCH_INIT_NOTIFY + "-connection-1group@@service*-2",
            FuzzyWatchPushDelayTaskEngine.getTaskKey(syncTask));
        assertThrows(NacosRuntimeException.class,
            () -> FuzzyWatchPushDelayTaskEngine.getTaskKey(new UnknownTask()));
    }
    
    private static class TestFuzzyWatchPushDelayTaskEngine
        extends FuzzyWatchPushDelayTaskEngine {
        
        TestFuzzyWatchPushDelayTaskEngine(PushExecutorDelegate pushExecutor,
            SwitchDomain switchDomain) {
            super(pushExecutor, switchDomain);
        }
        
        void processTasksForTest() {
            super.processTasks();
        }
    }
    
    private static class TestDelayTask extends AbstractDelayTask {
        
        TestDelayTask() {
            setTaskInterval(0L);
            setLastProcessTime(0L);
        }
        
        @Override
        public void merge(AbstractDelayTask task) {
        }
    }
    
    private static class UnknownTask implements NacosTask {
        
        @Override
        public boolean shouldProcess() {
            return true;
        }
    }
}
