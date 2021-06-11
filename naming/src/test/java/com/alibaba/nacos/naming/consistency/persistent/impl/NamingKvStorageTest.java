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
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NamingKvStorageTest extends TestCase {

    private NamingKvStorage namingKvStorage;

    @Mock
    private FileKvStorage baseDirStorageMock;

    private final byte[] key = "fileName_test".getBytes();

    private final String str = "str_test";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        namingKvStorage = new NamingKvStorage("baseDir_test");

        Field baseDirStorageField = NamingKvStorage.class.getDeclaredField("baseDirStorage");
        baseDirStorageField.setAccessible(true);
        baseDirStorageField.set(namingKvStorage, baseDirStorageMock);

        when(baseDirStorageMock.get(key)).thenReturn(null);
    }

    @Test
    public void testGet() throws KvStorageException {
        namingKvStorage.get(key);
        verify(baseDirStorageMock).get(key);
    }

    @Test
    public void testPut() throws KvStorageException {
        byte[] value = "value_test".getBytes();
        namingKvStorage.put(key, value);
        verify(baseDirStorageMock).put(key, value);
    }

    @Test
    public void testDelete() throws KvStorageException {
        namingKvStorage.delete(key);
        verify(baseDirStorageMock).delete(key);
    }

    @Test
    public void testDoSnapshot() throws KvStorageException {
        namingKvStorage.doSnapshot(str);
        verify(baseDirStorageMock).doSnapshot(str);
    }

    @Test
    public void testSnapshotLoad() throws KvStorageException {
        namingKvStorage.snapshotLoad(str);
        verify(baseDirStorageMock).snapshotLoad(str);
    }
}
