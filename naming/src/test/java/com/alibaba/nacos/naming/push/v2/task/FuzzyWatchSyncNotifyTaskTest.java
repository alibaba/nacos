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

import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchSyncRequest;
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.BatchTaskCounter;
import com.alibaba.nacos.naming.push.v2.PushConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyWatchSyncNotifyTaskTest {
    
    @Test
    void testConstructWithNullSyncServiceKeys() {
        FuzzyWatchSyncNotifyTask task =
            new FuzzyWatchSyncNotifyTask("client", "group@@service*",
                FUZZY_WATCH_INIT_NOTIFY, null, 100L);
        BatchTaskCounter batchTaskCounter = new BatchTaskCounter(1);
        task.setBatchTaskCounter(batchTaskCounter);
        task.setTotalBatch(3);
        task.setCurrentBatch(2);
        
        assertEquals("client", task.getClientId());
        assertEquals("group@@service*", task.getPattern());
        assertEquals(FUZZY_WATCH_INIT_NOTIFY, task.getSyncType());
        assertTrue(task.getSyncServiceKeys().isEmpty());
        assertEquals(100L, task.getTaskInterval());
        assertSame(batchTaskCounter, task.getBatchTaskCounter());
        assertEquals(3, task.getTotalBatch());
        assertEquals(2, task.getCurrentBatch());
        assertTrue(task.getExecuteStartTime() > 0L);
    }
    
    @Test
    void testMergeIgnoresOtherTaskType() {
        NamingFuzzyWatchSyncRequest.Context context = context("serviceA");
        Set<NamingFuzzyWatchSyncRequest.Context> contexts =
            new HashSet<>(Collections.singleton(context));
        FuzzyWatchSyncNotifyTask task =
            new FuzzyWatchSyncNotifyTask("client", "group@@service*",
                FUZZY_WATCH_INIT_NOTIFY, contexts, 100L);
        
        task.merge(new UnknownDelayTask());
        
        assertEquals(1, task.getSyncServiceKeys().size());
        assertTrue(task.getSyncServiceKeys().contains(context));
    }
    
    @Test
    void testMergeAddsServiceKeysAndKeepsEarlierProcessTime() {
        NamingFuzzyWatchSyncRequest.Context contextA = context("serviceA");
        NamingFuzzyWatchSyncRequest.Context contextB = context("serviceB");
        FuzzyWatchSyncNotifyTask task =
            new FuzzyWatchSyncNotifyTask("client", "group@@service*",
                FUZZY_WATCH_INIT_NOTIFY,
                new HashSet<>(Collections.singleton(contextA)), 100L);
        FuzzyWatchSyncNotifyTask oldTask =
            new FuzzyWatchSyncNotifyTask("client", "group@@service*",
                FUZZY_WATCH_INIT_NOTIFY,
                new HashSet<>(Collections.singleton(contextB)), 100L);
        task.setLastProcessTime(200L);
        oldTask.setLastProcessTime(50L);
        
        task.merge(oldTask);
        
        assertEquals(2, task.getSyncServiceKeys().size());
        assertTrue(task.getSyncServiceKeys().contains(contextA));
        assertTrue(task.getSyncServiceKeys().contains(contextB));
        assertEquals(50L, task.getLastProcessTime());
    }
    
    @Test
    void testCallbackTimeout() {
        FuzzyWatchSyncNotifyTask task =
            new FuzzyWatchSyncNotifyTask("client", "group@@service*",
                FUZZY_WATCH_INIT_NOTIFY, null, 100L);
        FuzzyWatchSyncNotifyCallback callback =
            new FuzzyWatchSyncNotifyCallback(task, new BatchTaskCounter(1), null);
        
        assertEquals(PushConfig.getInstance().getPushTaskTimeout(), callback.getTimeout());
    }
    
    private NamingFuzzyWatchSyncRequest.Context context(String serviceKey) {
        return NamingFuzzyWatchSyncRequest.Context.build(serviceKey, ADD_SERVICE);
    }
    
    private static class UnknownDelayTask extends AbstractDelayTask {
        
        @Override
        public void merge(AbstractDelayTask task) {
        }
    }
}
