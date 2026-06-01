/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.trace;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigTraceServiceTest {
    
    @BeforeAll
    static void setUpEnvironment() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new ConfigTraceService());
    }
    
    @Test
    void testLogEventsReturnWhenTraceLogDisabled() {
        Logger traceLogger = (Logger) LogUtil.TRACE_LOG;
        Level originalLevel = traceLogger.getLevel();
        traceLogger.setLevel(Level.OFF);
        try {
            assertDoesNotThrow(() -> ConfigTraceService.logPersistenceEvent(
                "dataId", "group", "tenant", "app", System.currentTimeMillis(),
                "127.0.0.1", ConfigTraceService.PERSISTENCE_EVENT,
                ConfigTraceService.PERSISTENCE_TYPE_PUB, "content"));
            assertDoesNotThrow(() -> ConfigTraceService.logNotifyEvent(
                "dataId", "group", "tenant", "app", System.currentTimeMillis(),
                "127.0.0.1", ConfigTraceService.NOTIFY_EVENT,
                ConfigTraceService.NOTIFY_TYPE_OK, 100L, "10.0.0.1"));
            assertDoesNotThrow(() -> ConfigTraceService.logDumpEvent(
                "dataId", "group", "tenant", "app", System.currentTimeMillis(),
                "127.0.0.1", ConfigTraceService.DUMP_TYPE_OK, 50L, 1024L));
            assertDoesNotThrow(() -> ConfigTraceService.logDumpAllEvent(
                "dataId", "group", "tenant", "app", System.currentTimeMillis(),
                "127.0.0.1", ConfigTraceService.DUMP_TYPE_OK));
            assertDoesNotThrow(() -> ConfigTraceService.logPullEvent(
                "dataId", "group", "tenant", "app", System.currentTimeMillis(),
                ConfigTraceService.PULL_EVENT, ConfigTraceService.PULL_TYPE_OK,
                200L, "10.0.0.1", false, "http"));
        } finally {
            traceLogger.setLevel(originalLevel);
        }
    }
    
    @Test
    void testLogPersistenceEvent() {
        assertDoesNotThrow(() -> ConfigTraceService.logPersistenceEvent(
            "dataId", "group", "tenant", "app", System.currentTimeMillis(),
            "127.0.0.1", ConfigTraceService.PERSISTENCE_EVENT,
            ConfigTraceService.PERSISTENCE_TYPE_PUB, "content"));
    }
    
    @Test
    void testLogPersistenceEventBlankTenant() {
        assertDoesNotThrow(() -> ConfigTraceService.logPersistenceEvent(
            "dataId", "group", "", "app", System.currentTimeMillis(),
            "127.0.0.1", ConfigTraceService.PERSISTENCE_EVENT,
            ConfigTraceService.PERSISTENCE_TYPE_PUB, null));
    }
    
    @Test
    void testLogNotifyEvent() {
        assertDoesNotThrow(() -> ConfigTraceService.logNotifyEvent(
            "dataId", "group", "tenant", "app", System.currentTimeMillis(),
            "127.0.0.1", ConfigTraceService.NOTIFY_EVENT,
            ConfigTraceService.NOTIFY_TYPE_OK, 100L, "10.0.0.1"));
    }
    
    @Test
    void testLogNotifyEventNegativeDelay() {
        assertDoesNotThrow(() -> ConfigTraceService.logNotifyEvent(
            "dataId", "group", "", "app", System.currentTimeMillis(),
            "127.0.0.1", ConfigTraceService.NOTIFY_EVENT,
            ConfigTraceService.NOTIFY_TYPE_OK, -5L, "10.0.0.1"));
    }
    
    @Test
    void testLogDumpEvent() {
        assertDoesNotThrow(() -> ConfigTraceService.logDumpEvent(
            "dataId", "group", "tenant", "app", System.currentTimeMillis(),
            "127.0.0.1", ConfigTraceService.DUMP_TYPE_OK, 50L, 1024L));
    }
    
    @Test
    void testLogDumpGrayNameEvent() {
        assertDoesNotThrow(() -> ConfigTraceService.logDumpGrayNameEvent(
            "dataId", "group", "", "gray1", "app",
            System.currentTimeMillis(), "127.0.0.1",
            ConfigTraceService.DUMP_TYPE_OK, -1L, 512L));
    }
    
    @Test
    void testLogDumpAllEvent() {
        assertDoesNotThrow(() -> ConfigTraceService.logDumpAllEvent(
            "dataId", "group", "tenant", "app", System.currentTimeMillis(),
            "127.0.0.1", ConfigTraceService.DUMP_TYPE_OK));
    }
    
    @Test
    void testLogDumpAllEventBlankTenant() {
        assertDoesNotThrow(() -> ConfigTraceService.logDumpAllEvent(
            "dataId", "group", "", "app", System.currentTimeMillis(),
            "127.0.0.1", ConfigTraceService.DUMP_TYPE_OK));
    }
    
    @Test
    void testLogPullEvent() {
        assertDoesNotThrow(() -> ConfigTraceService.logPullEvent(
            "dataId", "group", "tenant", "app", System.currentTimeMillis(),
            ConfigTraceService.PULL_EVENT, ConfigTraceService.PULL_TYPE_OK,
            200L, "10.0.0.1", false, "http"));
    }
    
    @Test
    void testLogPullEventNotifyNegativeDelay() {
        assertDoesNotThrow(() -> ConfigTraceService.logPullEvent(
            "dataId", "group", "", "app", System.currentTimeMillis(),
            ConfigTraceService.PULL_EVENT, ConfigTraceService.PULL_TYPE_OK,
            -1L, "10.0.0.1", true, "grpc"));
    }
    
    @Test
    void testConstants() {
        assertDoesNotThrow(() -> {
            String e = ConfigTraceService.PERSISTENCE_EVENT_BETA;
            e = ConfigTraceService.PERSISTENCE_EVENT_TAG;
            e = ConfigTraceService.PERSISTENCE_EVENT_METADATA;
            e = ConfigTraceService.PERSISTENCE_TYPE_REMOVE;
            e = ConfigTraceService.PERSISTENCE_TYPE_MERGE;
            e = ConfigTraceService.NOTIFY_EVENT_BETA;
            e = ConfigTraceService.NOTIFY_EVENT_BATCH;
            e = ConfigTraceService.NOTIFY_EVENT_TAG;
            e = ConfigTraceService.NOTIFY_TYPE_ERROR;
            e = ConfigTraceService.NOTIFY_TYPE_UNHEALTH;
            e = ConfigTraceService.NOTIFY_TYPE_EXCEPTION;
            e = ConfigTraceService.DUMP_EVENT_BETA;
            e = ConfigTraceService.DUMP_EVENT_BATCH;
            e = ConfigTraceService.DUMP_EVENT_TAG;
            e = ConfigTraceService.DUMP_TYPE_REMOVE_OK;
            e = ConfigTraceService.DUMP_TYPE_ERROR;
            e = ConfigTraceService.PULL_TYPE_NOTFOUND;
            e = ConfigTraceService.PULL_TYPE_CONFLICT;
            e = ConfigTraceService.PULL_TYPE_ERROR;
        });
    }
}
