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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link MemoryKvStorage} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 18:02
 */
public class MemoryKvStorageTest {
    private KvStorage kvStorage;
    
    @Before
    public void init() {
        try {
            kvStorage = StorageFactory.createKvStorage(KvStorage.KvType.Memory, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
    
    @Test
    public void testPutAndGetAndDelete() {
        try {
            byte[] key = "key".getBytes();
            byte[] value = "value".getBytes();
            kvStorage.put(key, value);
            byte[] value1 = kvStorage.get(key);
            Assert.assertArrayEquals(value, value1);
            
            Assert.assertNotNull(kvStorage.allKeys());
            
            kvStorage.delete(key);
            Assert.assertNull(kvStorage.get(key));
            
            kvStorage.put(key, value);
            kvStorage.shutdown();
            Assert.assertEquals(0, kvStorage.allKeys().size());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
    
    @Test
    public void testBatchPutAndGet() {
        try {
            List<byte[]> keys = Arrays.asList("key1".getBytes(), "key2".getBytes());
            List<byte[]> values = Arrays.asList("value1".getBytes(), "value2".getBytes());
            kvStorage.batchPut(keys, values);
            
            Map<byte[], byte[]> res = kvStorage.batchGet(keys);
            Assert.assertNotNull(res);
            
            res.forEach((key, value) -> {
                if (Arrays.equals(key, "key1".getBytes())) {
                    Assert.assertArrayEquals("value1".getBytes(), value);
                } else if (Arrays.equals(key, "key2".getBytes())) {
                    Assert.assertArrayEquals("value2".getBytes(), value);
                } else {
                    Assert.fail();
                }
            });
            
            kvStorage.batchDelete(keys);
            Assert.assertEquals(0, kvStorage.batchGet(values).size());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
    
    @Test
    public void testSnapshot() {
        try {
            kvStorage.doSnapshot("/");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof UnsupportedOperationException);
        }
    
        try {
            kvStorage.snapshotLoad("/");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof UnsupportedOperationException);
        }
    }
}
