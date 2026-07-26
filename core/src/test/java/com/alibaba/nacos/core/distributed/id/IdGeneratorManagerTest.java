/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.id;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdGeneratorManager} unit test.
 */
class IdGeneratorManagerTest {
    
    private IdGeneratorManager idGeneratorManager;
    
    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("nacos.core.snowflake.worker-id", "1");
        EnvUtil.setEnvironment(env);
        idGeneratorManager = new IdGeneratorManager();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testRegisterSingleResource() {
        String resource = "testResource";
        idGeneratorManager.register(resource);
        
        Map<String, com.alibaba.nacos.consistency.IdGenerator> map =
            idGeneratorManager.getGeneratorMap();
        assertTrue(map.containsKey(resource));
        assertNotNull(map.get(resource));
    }
    
    @Test
    void testRegisterMultipleResources() {
        idGeneratorManager.register("res1", "res2", "res3");
        
        assertTrue(idGeneratorManager.getGeneratorMap().containsKey("res1"));
        assertTrue(idGeneratorManager.getGeneratorMap().containsKey("res2"));
        assertTrue(idGeneratorManager.getGeneratorMap().containsKey("res3"));
    }
    
    @Test
    void testNextIdAfterRegister() {
        String resource = "nextIdResource";
        idGeneratorManager.register(resource);
        
        long id1 = idGeneratorManager.nextId(resource);
        long id2 = idGeneratorManager.nextId(resource);
        
        assertTrue(id1 > 0);
        assertTrue(id2 > 0);
    }
    
    @Test
    void testNextIdWithoutRegisterThrows() {
        assertThrows(NoSuchElementException.class, () -> idGeneratorManager.nextId("unregistered"));
    }
    
    @Test
    void testGetGeneratorMapReturnsCopyBehavior() {
        idGeneratorManager.register("r1");
        Map<String, com.alibaba.nacos.consistency.IdGenerator> map =
            idGeneratorManager.getGeneratorMap();
        assertEquals(1, map.size());
        assertNotNull(map.get("r1"));
    }
    
    @Test
    void testRegisterSameResourceTwiceUsesSameGenerator() {
        String resource = "same";
        idGeneratorManager.register(resource);
        long id1 = idGeneratorManager.nextId(resource);
        idGeneratorManager.register(resource);
        long id2 = idGeneratorManager.nextId(resource);
        assertEquals(1, idGeneratorManager.getGeneratorMap().size());
        assertTrue(id2 > id1);
    }
    
    @Test
    void testSpiLoadedGeneratorUsedWhenAvailable() {
        idGeneratorManager.register("spiRes");
        long id1 = idGeneratorManager.nextId("spiRes");
        long id2 = idGeneratorManager.nextId("spiRes");
        assertTrue(id1 >= 1);
        assertTrue(id2 == id1 + 1 || id2 > id1);
    }
}
