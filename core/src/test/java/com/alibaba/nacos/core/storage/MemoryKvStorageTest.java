/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.storage;

import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.core.storage.kv.MemoryKvStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link MemoryKvStorage} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 18:02
 */
class MemoryKvStorageTest {
    
    private KvStorage kvStorage;
    
    @BeforeEach
    void init() {
        try {
            kvStorage = StorageFactory.createKvStorage(KvStorage.KvType.Memory, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
    
    @Test
    void testPutAndGetAndDelete() {
        try {
            byte[] key = "key".getBytes();
            byte[] value = "value".getBytes();
            kvStorage.put(key, value);
            byte[] value1 = kvStorage.get(key);
            assertArrayEquals(value, value1);
            
            assertNotNull(kvStorage.allKeys());
            
            kvStorage.delete(key);
            assertNull(kvStorage.get(key));
            
            kvStorage.put(key, value);
            kvStorage.shutdown();
            assertEquals(0, kvStorage.allKeys().size());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
    
    @Test
    void testBatchPutAndGet() {
        try {
            List<byte[]> keys = Arrays.asList("key1".getBytes(), "key2".getBytes());
            List<byte[]> values = Arrays.asList("value1".getBytes(), "value2".getBytes());
            kvStorage.batchPut(keys, values);
            
            Map<byte[], byte[]> res = kvStorage.batchGet(keys);
            assertNotNull(res);
            
            res.forEach((key, value) -> {
                if (Arrays.equals(key, "key1".getBytes())) {
                    assertArrayEquals("value1".getBytes(), value);
                } else if (Arrays.equals(key, "key2".getBytes())) {
                    assertArrayEquals("value2".getBytes(), value);
                } else {
                    fail();
                }
            });
            
            kvStorage.batchDelete(keys);
            assertEquals(0, kvStorage.batchGet(values).size());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
    
    @Test
    void testSnapshot() {
        try {
            kvStorage.doSnapshot("/");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
        }
        
        try {
            kvStorage.snapshotLoad("/");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
        }
    }
}
