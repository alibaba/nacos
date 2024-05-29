/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

class LoggerUtilsTest {
    
    @Test
    void testPrintIfDebugEnabled() {
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);
        LoggerUtils.printIfDebugEnabled(logger, "test", "arg1", "arg2", "arg3");
        Mockito.verify(logger, Mockito.times(1)).debug("test", "arg1", "arg2", "arg3");
    }
    
    @Test
    void testPrintIfInfoEnabled() {
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isInfoEnabled()).thenReturn(true);
        LoggerUtils.printIfInfoEnabled(logger, "test", "arg1", "arg2", "arg3");
        Mockito.verify(logger, Mockito.times(1)).info("test", "arg1", "arg2", "arg3");
    }
    
    @Test
    void testPrintIfTraceEnabled() {
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);
        LoggerUtils.printIfTraceEnabled(logger, "test", "arg1", "arg2", "arg3");
        Mockito.verify(logger, Mockito.times(1)).trace("test", "arg1", "arg2", "arg3");
    }
    
    @Test
    void testPrintIfWarnEnabled() {
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isWarnEnabled()).thenReturn(true);
        LoggerUtils.printIfWarnEnabled(logger, "test", "arg1", "arg2", "arg3");
        Mockito.verify(logger, Mockito.times(1)).warn("test", "arg1", "arg2", "arg3");
    }
    
    @Test
    void testPrintIfErrorEnabled() {
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isErrorEnabled()).thenReturn(true);
        LoggerUtils.printIfErrorEnabled(logger, "test", "arg1", "arg2", "arg3");
        Mockito.verify(logger, Mockito.times(1)).error("test", "arg1", "arg2", "arg3");
    }
    
}
