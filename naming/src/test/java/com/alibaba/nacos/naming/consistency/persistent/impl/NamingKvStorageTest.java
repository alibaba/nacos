/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.core.storage.kv.FileKvStorage;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class NamingKvStorageTest {
    
    private final byte[] key = "fileName_test".getBytes();
    
    private final String str = "str_test";
    
    private NamingKvStorage namingKvStorage;
    
    @Mock
    private FileKvStorage baseDirStorageMock;
    
    @BeforeEach
    void setUp() throws Exception {
        namingKvStorage = new NamingKvStorage("baseDir_test");
        
        Field baseDirStorageField = NamingKvStorage.class.getDeclaredField("baseDirStorage");
        baseDirStorageField.setAccessible(true);
        baseDirStorageField.set(namingKvStorage, baseDirStorageMock);
        
        when(baseDirStorageMock.get(key)).thenReturn(null);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        DiskUtils.deleteDirectory("baseDir_test");
    }
    
    @Test
    void testGet() throws KvStorageException {
        namingKvStorage.get(key);
        verify(baseDirStorageMock).get(key);
    }
    
    @Test
    void testPut() throws KvStorageException {
        byte[] value = "value_test".getBytes();
        namingKvStorage.put(key, value);
        verify(baseDirStorageMock).put(key, value);
    }
    
    @Test
    void testDelete() throws KvStorageException {
        namingKvStorage.delete(key);
        verify(baseDirStorageMock).delete(key);
    }
    
    @Test
    void testDoSnapshot() throws KvStorageException {
        namingKvStorage.doSnapshot(str);
        verify(baseDirStorageMock).doSnapshot(str);
    }
    
    @Test
    void testSnapshotLoad() throws KvStorageException {
        namingKvStorage.snapshotLoad(str);
        verify(baseDirStorageMock).snapshotLoad(str);
    }
}
