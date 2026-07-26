/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.core.distributed.id;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowFlowerInstanceIdGeneratorTest {
    
    @Test
    void nextId() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        SnowFlowerIdGenerator generator = new SnowFlowerIdGenerator();
        generator.initialize(1);
        
        long count = IntStream.range(0, 10).mapToObj(i -> generator.nextId()).distinct().count();
        
        assertEquals(10, count);
    }
    
    @Test
    void currentIdAndWorkerId() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        SnowFlowerIdGenerator generator = new SnowFlowerIdGenerator();
        generator.initialize(1);
        assertEquals(0, generator.currentId());
        assertEquals(1, generator.workerId());
        long id = generator.nextId();
        assertEquals(id, generator.currentId());
    }
    
    @Test
    void info() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        SnowFlowerIdGenerator generator = new SnowFlowerIdGenerator();
        generator.initialize(2);
        generator.nextId();
        Map<Object, Object> info = generator.info();
        assertEquals(2L, info.get("workerId"));
        assertEquals(generator.currentId(), info.get("currentId"));
    }
    
    @Test
    void initializeRejectsInvalidWorkerId() {
        SnowFlowerIdGenerator generator = new SnowFlowerIdGenerator();
        assertThrows(IllegalArgumentException.class, () -> generator.initialize(1025));
        assertThrows(IllegalArgumentException.class, () -> generator.initialize(-1));
    }
    
    @Test
    void nextIdWithWorkerIdFromEnv() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        SnowFlowerIdGenerator generator = new SnowFlowerIdGenerator();
        generator.initialize(0);
        for (int i = 0; i < 10; i++) {
            generator.nextId();
        }
        assertTrue(generator.currentId() > 0);
    }
    
    @Test
    void nextIdSequenceOverflowTriggersWaitUntilNextTime() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        SnowFlowerIdGenerator generator = new SnowFlowerIdGenerator();
        generator.initialize(1);
        // Use reflection to force sequence=4095 and lastTime=currentMillis so the next nextId()
        // hits (++sequence & 4095)==0 and enters waitUntilNextTime()
        ReflectionTestUtils.setField(generator, "sequence", 4095L);
        Method currentTimeMillis =
            SnowFlowerIdGenerator.class.getDeclaredMethod("currentTimeMillis");
        currentTimeMillis.setAccessible(true);
        long now = (Long) currentTimeMillis.invoke(generator);
        ReflectionTestUtils.setField(generator, "lastTime", now);
        long id = generator.nextId();
        assertTrue(id > 0);
        assertTrue(generator.currentId() > 0);
    }
    
    @Test
    void workerIdFromIpWhenNoPropertySet() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        SnowFlowerIdGenerator generator = new SnowFlowerIdGenerator();
        generator.init();
        long w = generator.workerId();
        assertTrue(w >= 0 && w <= 1024);
        generator.nextId();
        assertTrue(generator.currentId() > 0);
    }
}
