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

package com.alibaba.nacos.persistence.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistenceExecutorTest {
    
    @Test
    void testExecuteEmbeddedDump() throws InterruptedException {
        
        AtomicInteger atomicInteger = new AtomicInteger();
        
        Runnable runnable = atomicInteger::incrementAndGet;
        
        PersistenceExecutor.executeEmbeddedDump(runnable);
        
        TimeUnit.MILLISECONDS.sleep(20);
        
        assertEquals(1, atomicInteger.get());
        
    }
    
}