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

package com.alibaba.nacos.sys.utils;

import com.alibaba.nacos.common.utils.LoggerUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimerContextTest {
    
    @Mock
    Runnable runnable;
    
    @Mock
    Supplier<Object> supplier;
    
    @Mock
    Function<Object, Object> function;
    
    @Mock
    Consumer<Object> consumer;
    
    @Mock
    Logger logger;
    
    @Test
    void testEndForInfo() {
        when(logger.isInfoEnabled()).thenReturn(true);
        TimerContext.start("test");
        TimerContext.end("test", logger, LoggerUtils.INFO);
        verify(logger).info(anyString(), any(Object[].class));
    }
    
    @Test
    void testEndForTrace() {
        when(logger.isTraceEnabled()).thenReturn(true);
        TimerContext.start("test");
        TimerContext.end("test", logger, LoggerUtils.TRACE);
        verify(logger).trace(anyString(), any(Object[].class));
    }
    
    @Test
    void testEndForError() {
        when(logger.isErrorEnabled()).thenReturn(true);
        TimerContext.start("test");
        TimerContext.end("test", logger, LoggerUtils.ERROR);
        verify(logger).error(anyString(), any(Object[].class));
    }
    
    @Test
    void testEndForWarn() {
        when(logger.isWarnEnabled()).thenReturn(true);
        TimerContext.start("test");
        TimerContext.end("test", logger, LoggerUtils.WARN);
        verify(logger).warn(anyString(), any(Object[].class));
    }
    
    @Test
    void testEndForDefault() {
        when(logger.isErrorEnabled()).thenReturn(true);
        TimerContext.start("test");
        TimerContext.end("test", logger, "");
        verify(logger).error(anyString(), any(Object[].class));
    }
    
    @Test
    void testRunWithRunnable() {
        when(logger.isDebugEnabled()).thenReturn(true);
        TimerContext.run(runnable, "test", logger);
        verify(runnable).run();
        verify(logger).debug(anyString(), any(Object[].class));
    }
    
    @Test
    void testRunWithSupplier() {
        when(logger.isDebugEnabled()).thenReturn(true);
        Object o = new Object();
        when(supplier.get()).thenReturn(o);
        assertEquals(o, TimerContext.run(supplier, "test", logger));
        verify(logger).debug(anyString(), any(Object[].class));
    }
    
    @Test
    void testRunWithFunction() {
        when(logger.isDebugEnabled()).thenReturn(true);
        Object input = new Object();
        Object output = new Object();
        when(function.apply(input)).thenReturn(output);
        assertEquals(output, TimerContext.run(function, input, "test", logger));
        verify(logger).debug(anyString(), any(Object[].class));
    }
    
    @Test
    void testRunWithConsumer() {
        when(logger.isDebugEnabled()).thenReturn(true);
        Object o = new Object();
        TimerContext.run(consumer, o, "test", logger);
        verify(consumer).accept(o);
        verify(logger).debug(anyString(), any(Object[].class));
    }
}