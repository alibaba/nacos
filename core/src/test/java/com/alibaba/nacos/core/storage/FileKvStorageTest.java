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

import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.core.storage.kv.FileKvStorage;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link FileKvStorage} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 18:27
 */
public class FileKvStorageTest {
    
    private KvStorage kvStorage;
    
    private String baseDir;
    
    @Before
    public void init() {
        try {
            baseDir = System.getProperty("user.home");
            String dir = baseDir + File.separator + "nacos_file_kv_storage_test_brotherluxcq";
            kvStorage = StorageFactory.createKvStorage(KvStorage.KvType.File, null, dir);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
    
    @AfterClass
    public static void clean() {
        String dir = System.getProperty("user.home") + File.separator + "nacos_file_kv_storage_test_brotherluxcq";
        String backupDir = System.getProperty("user.home") + File.separator + "nacos_file_kv_storage_test_backup_brotherluxcq";
    
        try {
            FileUtils.deleteDirectory(new File(dir));
            FileUtils.deleteDirectory(new File(backupDir));
        } catch (IOException e) {
            e.printStackTrace();
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
            Assert.assertEquals(kvStorage.allKeys().size(), kvStorage.allKeys().size());
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
        String backupDir = baseDir + File.separator + "nacos_file_kv_storage_test_backup_brotherluxcq";
        try {
            File file = new File(backupDir);
            if (!file.exists()) {
                boolean dirResult = file.mkdirs();
                if (!dirResult) {
                    return;
                }
            }
            kvStorage.doSnapshot(backupDir);
        } catch (KvStorageException e) {
            e.printStackTrace();
            Assert.fail();
        }
    
        try {
            kvStorage.snapshotLoad(backupDir);
            byte[] key = "key".getBytes();
            byte[] value = kvStorage.get(key);
            Assert.assertArrayEquals("value".getBytes(), value);
        } catch (KvStorageException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
