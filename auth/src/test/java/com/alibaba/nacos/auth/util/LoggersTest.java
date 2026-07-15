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

package com.alibaba.nacos.auth.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class LoggersTest {
    
    private Level originalLevel;
    
    @BeforeEach
    void setUp() {
        originalLevel = ((Logger) Loggers.AUTH).getLevel();
    }
    
    @AfterEach
    void tearDown() {
        if (originalLevel != null) {
            ((Logger) Loggers.AUTH).setLevel(originalLevel);
        }
    }
    
    @Test
    void testSetLogLevelForAuth() {
        Loggers.setLogLevel("auth", "DEBUG");
        assertEquals(Level.DEBUG, ((Logger) Loggers.AUTH).getLevel());
    }
    
    @Test
    void testSetLogLevelForNonAuth() {
        Level levelBefore = ((Logger) Loggers.AUTH).getLevel();
        Loggers.setLogLevel("other", "DEBUG");
        assertEquals(levelBefore, ((Logger) Loggers.AUTH).getLevel());
    }
    
    @Test
    void testAuthLoggerNotNull() {
        assertNotNull(Loggers.AUTH);
    }
    
    @Test
    void testConstructor() {
        new Loggers();
    }
}
