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

package com.alibaba.nacos.naming.misc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoggersTest {
    
    private final Map<Logger, Level> originalLevels = new LinkedHashMap<>();
    
    @BeforeEach
    void setUp() {
        remember(Loggers.PUSH);
        remember(Loggers.CHECK_RT);
        remember(Loggers.SRV_LOG);
        remember(Loggers.EVT_LOG);
        remember(Loggers.RAFT);
        remember(Loggers.DISTRO);
        remember(Loggers.PERFORMANCE_LOG);
    }
    
    @AfterEach
    void tearDown() {
        originalLevels.forEach(Logger::setLevel);
    }
    
    @Test
    void testSetLogLevelForKnownNames() {
        assertSetLevel("naming-push", Loggers.PUSH, Level.DEBUG);
        assertSetLevel("naming-rt", Loggers.CHECK_RT, Level.INFO);
        assertSetLevel("naming-server", Loggers.SRV_LOG, Level.WARN);
        assertSetLevel("naming-event", Loggers.EVT_LOG, Level.ERROR);
        assertSetLevel("naming-raft", Loggers.RAFT, Level.TRACE);
        assertSetLevel("naming-distro", Loggers.DISTRO, Level.OFF);
        assertSetLevel("naming-performance", Loggers.PERFORMANCE_LOG, Level.toLevel("ALL"));
    }
    
    @Test
    void testSetLogLevelForUnknownNameDoesNothing() {
        Logger pushLogger = (Logger) Loggers.PUSH;
        pushLogger.setLevel(null);
        
        Loggers.setLogLevel("unknown", Level.ERROR.toString());
        
        assertNull(pushLogger.getLevel());
    }
    
    @Test
    void testConstruct() {
        assertNotNull(new Loggers());
    }
    
    private void remember(org.slf4j.Logger logger) {
        Logger logbackLogger = (Logger) logger;
        originalLevels.put(logbackLogger, logbackLogger.getLevel());
    }
    
    private void assertSetLevel(String logName, org.slf4j.Logger logger, Level level) {
        Loggers.setLogLevel(logName, level.toString());
        assertEquals(level, ((Logger) logger).getLevel());
    }
}
