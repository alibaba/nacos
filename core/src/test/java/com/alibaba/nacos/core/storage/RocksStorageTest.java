/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.storage;

import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.core.storage.kv.RocksStorage;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.DiskUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.rocksdb.RocksDB;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Paths;

public class RocksStorageTest {
 
    static {
        ApplicationUtils.injectEnvironment(new MockEnvironment());
        RocksDB.loadLibrary();
    }
    
    static final String DIR = Paths.get(ApplicationUtils.getNacosTmpDir(), "rocksdb").toString();
    
    @Before
    public void before() throws Throwable {
        DiskUtils.deleteDirectory(DIR);
    }
    
    @Test
    public void testCreateRocksStorage() throws Throwable {
        RocksStorage storage = RocksStorage.createDefault("test", DIR);
        storage.put("liaochuntao".getBytes(), "liaochuntao".getBytes());
    }
    
    @Test
    public void testRocksStorageSnapshotSave() throws Throwable {
        try {
            RocksStorage storage = RocksStorage.createDefault("test", DIR);
            storage.put("liaochuntao".getBytes(), "liaochuntao".getBytes());
            storage.doSnapshot(Paths.get(DIR, "snapshot").toString());
        } catch (Throwable ex) {
            Assert.fail(ex.getMessage());
        }
    }
    
    @Test
    public void testRocksStorageSnapshotLoad() throws Throwable {
        RocksStorage storage = RocksStorage.createDefault("test", DIR);
        storage.put("liaochuntao".getBytes(), "liaochuntao".getBytes());
        storage.snapshotLoad(Paths.get(DIR, "snapshot").toString());
        storage.shutdown();
        ThreadUtils.sleep(5_000L);
        storage = RocksStorage.createDefault("test", Paths.get(ApplicationUtils.getNacosTmpDir(), "snapshot_load").toString());
        storage.snapshotLoad(Paths.get(DIR, "snapshot").toString());
        byte[] b = storage.get("liaochuntao".getBytes());
        Assert.assertArrayEquals(b, "liaochuntao".getBytes());
    }
    
}