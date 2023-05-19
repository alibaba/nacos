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

import com.alibaba.nacos.core.storage.kv.FileKvStorage;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.core.storage.kv.MemoryKvStorage;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@link StorageFactory} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 17:55
 */
public class StorageFactoryTest {
    
    @Test
    public void testCreateKvStorage() {
        try {
            KvStorage kvStorage = StorageFactory.createKvStorage(KvStorage.KvType.Memory, "", "/");
            Assert.assertTrue(kvStorage instanceof MemoryKvStorage);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    
        try {
            KvStorage kvStorage = StorageFactory.createKvStorage(KvStorage.KvType.File, "", "/");
            Assert.assertTrue(kvStorage instanceof FileKvStorage);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    
        try {
            StorageFactory.createKvStorage(KvStorage.KvType.RocksDB, "", "/");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
