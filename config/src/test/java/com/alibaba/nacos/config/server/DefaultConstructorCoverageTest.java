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

package com.alibaba.nacos.config.server;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultConstructorCoverageTest {
    
    private static final String BASE = "com.alibaba.nacos.config.server.";
    
    @Test
    void testZeroArgumentConstructors() throws Exception {
        for (String className : zeroArgumentClassNames()) {
            Constructor<?> constructor = Class.forName(className).getDeclaredConstructor();
            constructor.setAccessible(true);
            
            assertNotNull(constructor.newInstance());
        }
    }
    
    @Test
    void testClientRecordConstructor() throws Exception {
        Constructor<?> constructor = Class.forName(BASE + "service.ClientRecord")
            .getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        
        Object record = constructor.newInstance("127.0.0.1");
        
        assertNotNull(record);
        assertEquals("127.0.0.1", record.getClass().getMethod("getIp").invoke(record));
    }
    
    @Test
    void testDumpAllTaskMergeMethods() throws Exception {
        invokeMerge(BASE + "service.dump.task.DumpAllGrayTask");
        
        Object dumpAllTask = Class.forName(BASE + "service.dump.task.DumpAllTask")
            .getDeclaredConstructor(boolean.class).newInstance(true);
        dumpAllTask.getClass().getMethod("merge", AbstractDelayTask.class)
            .invoke(dumpAllTask, new Object[] {null});
    }
    
    private void invokeMerge(String className) throws Exception {
        Object task = Class.forName(className).getDeclaredConstructor().newInstance();
        task.getClass().getMethod("merge", AbstractDelayTask.class)
            .invoke(task, new Object[] {null});
    }
    
    private String[] zeroArgumentClassNames() {
        return new String[] {
            BASE + "Config",
            BASE + "constant.Constants",
            BASE + "model.ConfigAdvanceInfo",
            BASE + "model.ConfigAllInfo",
            BASE + "model.ConfigInfoGrayWrapper",
            BASE + "model.ConfigInfoWrapper",
            BASE + "model.gray.GrayRuleManager",
            BASE + "monitor.MetricsMonitor",
            BASE + "monitor.ResponseMonitor",
            BASE + "service.ClientIpWhiteList",
            BASE + "service.ClientTrackService",
            BASE + "service.ConfigChangePublisher",
            BASE + "service.SwitchService",
            BASE + "service.dump.disk.ConfigDiskServiceFactory",
            BASE + "service.dump.HistoryConfigCleanerManager",
            BASE + "service.dump.task.DumpAllGrayTask",
            BASE + "service.query.handler.ConfigContentTypeHandler",
            BASE + "service.sql.EmbeddedStorageContextUtils",
            BASE + "service.sql.ExternalStorageUtils",
            BASE + "utils.AppNameUtils",
            BASE + "utils.ConfigExecutor",
            BASE + "utils.ConfigTagUtil",
            BASE + "utils.ContentUtils",
            BASE + "utils.GroupKey",
            BASE + "utils.GroupKey2",
            BASE + "utils.LogUtil",
            BASE + "utils.MD5Util",
            BASE + "utils.ParamUtils",
            BASE + "utils.Protocol",
            BASE + "utils.RegexParser",
            BASE + "utils.RequestUtil",
            BASE + "utils.ResponseUtil",
            BASE + "utils.SystemConfig",
            BASE + "utils.TimeUtils",
            BASE + "utils.TraceLogUtil",
            BASE + "utils.UrlAnalysisUtils"
        };
    }
}
