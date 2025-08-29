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

package com.alibaba.nacos.client.redo.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RedoDataTest {
    
    @Test
    void testGetRedoType() {
        RedoData<String> redoData = new RedoData<String>() {
        };
        // registered=true, unregistering=false, expectedRegistered=true, means data has been registered and match expected.
        redoData.setRegistered(true);
        redoData.setExpectedRegistered(true);
        redoData.setUnregistering(false);
        assertEquals(RedoData.RedoType.NONE, redoData.getRedoType());
        
        // registered=true, unregistering=false, expectedRegistered=true, means data has been registered but not match expected.
        redoData.setRegistered(true);
        redoData.setExpectedRegistered(false);
        redoData.setUnregistering(false);
        assertEquals(RedoData.RedoType.UNREGISTER, redoData.getRedoType());
        
        // registered=true, unregistering=true, expectedRegistered=false, means data has been registered and doing unregister.
        redoData.setRegistered(true);
        redoData.setExpectedRegistered(false);
        redoData.setUnregistering(true);
        assertEquals(RedoData.RedoType.UNREGISTER, redoData.getRedoType());
        
        // registered=false, unregistering=false, expectedRegistered=true, means data has been not registered and doing register.
        redoData.setRegistered(false);
        redoData.setExpectedRegistered(true);
        redoData.setUnregistering(false);
        assertEquals(RedoData.RedoType.REGISTER, redoData.getRedoType());
        
        // registered=false, unregistering=true, expectedRegistered=false, means data has been unregistered and match expected.
        redoData.setRegistered(false);
        redoData.setExpectedRegistered(false);
        redoData.setUnregistering(true);
        assertEquals(RedoData.RedoType.REMOVE, redoData.getRedoType());
        
        // registered=false, unregistering=true, expectedRegistered=true, means data has been unregistered but not match expected.
        redoData.setRegistered(false);
        redoData.setExpectedRegistered(true);
        redoData.setUnregistering(true);
        assertEquals(RedoData.RedoType.REGISTER, redoData.getRedoType());
    }
    
    @Test
    void testEqualsAndHashCode() {
        MockRedoData redoData = new MockRedoData();
        redoData.set("String");
        assertEquals(redoData, redoData);
        assertNotEquals(redoData, null);
        assertNotEquals(redoData, new RedoData<String>() {
        });
        MockRedoData redoData2 = new MockRedoData();
        redoData2.set("aaa");
        assertNotEquals(redoData, redoData2);
        redoData2.set("String");
        assertEquals(redoData, redoData2);
        assertEquals(redoData.hashCode(), redoData2.hashCode());
        redoData2.setRegistered(true);
        assertNotEquals(redoData, redoData2);
        redoData2.setRegistered(redoData.isRegistered());
        redoData2.setUnregistering(!redoData.isUnregistering());
        assertNotEquals(redoData, redoData2);
        assertEquals("String", redoData.get());
    }
    
    private static class MockRedoData extends RedoData<String> {
    }
}