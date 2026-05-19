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

package com.alibaba.nacos.config.server.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.nacos.common.utils.IoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class SwitchServiceTest {
    
    private ListAppender<ILoggingEvent> fatalLogAppender;
    
    @BeforeEach
    void attachFatalLogAppender() {
        Logger fatalLogger = (Logger) LoggerFactory.getLogger("com.alibaba.nacos.config.fatal");
        fatalLogAppender = new ListAppender<>();
        fatalLogAppender.start();
        fatalLogger.addAppender(fatalLogAppender);
    }
    
    @AfterEach
    void detachFatalLogAppender() {
        Logger fatalLogger = (Logger) LoggerFactory.getLogger("com.alibaba.nacos.config.fatal");
        fatalLogger.detachAppender(fatalLogAppender);
        fatalLogAppender.stop();
    }
    
    @Test
    void testLoadAppliesAllEntriesAfterFullParse() {
        String config = "fixedDelayTime=10\n"
            + "isHealthCheck=true\n"
            + "defaultPushCacheMillis=500\n"
            + "switchA=valueA\n"
            + "switchB=valueB";
        
        SwitchService.load(config);
        
        assertEquals(10, SwitchService.getSwitchInteger("fixedDelayTime", -1));
        assertEquals(500, SwitchService.getSwitchInteger("defaultPushCacheMillis", -1));
        String dump = SwitchService.getSwitches();
        assertTrue(dump.contains("isHealthCheck=true"));
        assertTrue(dump.contains("switchA=valueA"));
        assertTrue(dump.contains("switchB=valueB"));
    }
    
    @Test
    void testLoadSkipsBlankAndCommentLines() {
        String config = "# leading comment\n"
            + "\n"
            + "fixedDelayTime=20\n"
            + "# inline comment\n"
            + "switchX=ok";
        
        SwitchService.load(config);
        
        assertEquals(20, SwitchService.getSwitchInteger("fixedDelayTime", -1));
        String dump = SwitchService.getSwitches();
        assertTrue(dump.contains("switchX=ok"));
        assertFalse(dump.contains("# leading comment"));
        assertFalse(dump.contains("# inline comment"));
    }
    
    @Test
    void testLoadIsAtomicWithRespectToReplacement() {
        SwitchService.load("first=1\nsecond=2\nthird=3");
        assertEquals(1, SwitchService.getSwitchInteger("first", -1));
        assertEquals(3, SwitchService.getSwitchInteger("third", -1));
        
        SwitchService.load("alpha=10\nbeta=20");
        
        assertEquals(-1, SwitchService.getSwitchInteger("first", -1));
        assertEquals(-1, SwitchService.getSwitchInteger("third", -1));
        assertEquals(10, SwitchService.getSwitchInteger("alpha", -1));
        assertEquals(20, SwitchService.getSwitchInteger("beta", -1));
    }
    
    @Test
    void testLoadIgnoresCorruptRecord() {
        String config = "good=1\n"
            + "corruptRecordWithoutEquals\n"
            + "alsoGood=2";
        
        SwitchService.load(config);
        
        assertEquals(1, SwitchService.getSwitchInteger("good", -1));
        assertEquals(2, SwitchService.getSwitchInteger("alsoGood", -1));
    }
    
    @Test
    void testLoadBlankConfigKeepsExistingSwitches() {
        SwitchService.load("retained=42");
        
        SwitchService.load("");
        
        assertEquals(42, SwitchService.getSwitchInteger("retained", -1));
    }
    
    @Test
    void testLoadAllCorruptInputAtomicallyReplacesSwitches() {
        // Any non-blank input is treated as an authoritative replacement, even when
        // every record is corrupt. Operators wishing to preserve prior switches must
        // send a blank payload (covered by testLoadBlankConfigKeepsExistingSwitches).
        SwitchService.load("retained=42");
        
        SwitchService.load("noEqualsAtAll\nstillCorrupt");
        
        assertEquals(-1, SwitchService.getSwitchInteger("retained", -1));
    }
    
    @Test
    void testLoadAllCommentInputAtomicallyReplacesSwitches() {
        SwitchService.load("retained=42");
        
        SwitchService.load("# only comments\n# nothing useful");
        
        assertEquals(-1, SwitchService.getSwitchInteger("retained", -1));
    }
    
    @Test
    void testGetSwitchIntegerReturnsDefaultForMissingKey() {
        SwitchService.load("present=7");
        
        assertEquals(99, SwitchService.getSwitchInteger("missing", 99));
    }
    
    @Test
    void testGetSwitchIntegerReturnsDefaultForCorruptValue() {
        SwitchService.load("notAnInt=abc");
        
        assertEquals(123, SwitchService.getSwitchInteger("notAnInt", 123));
    }
    
    @Test
    void testLoadLogsIoExceptionWhenReadLinesFails() {
        try (MockedStatic<IoUtils> ioUtilsMockedStatic = Mockito.mockStatic(IoUtils.class)) {
            ioUtilsMockedStatic.when(() -> IoUtils.readLines(any(StringReader.class)))
                .thenThrow(new IOException("mock error"));
            
            SwitchService.load("key=value");
        }
        
        long errorLogs = fatalLogAppender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .filter(event -> event.getFormattedMessage().contains("[reload-switches] error"))
            .count();
        assertEquals(1, errorLogs);
    }
    
    @Test
    void testLoadEmitsReloadSwitchesLogExactlyOnce() {
        String config = "first=1\n"
            + "second=2\n"
            + "third=3\n"
            + "fourth=4\n"
            + "fifth=5";
        
        SwitchService.load(config);
        
        long reloadLogs = fatalLogAppender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .filter(event -> event.getFormattedMessage().contains("[reload-switches]"))
            .count();
        assertEquals(1, reloadLogs);
    }
}
