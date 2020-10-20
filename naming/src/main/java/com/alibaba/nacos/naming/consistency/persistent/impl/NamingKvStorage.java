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

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.core.storage.StorageFactory;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.core.storage.kv.MemoryKvStorage;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.misc.Loggers;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kv storage implementation for naming.
 *
 * @author xiweng.yy
 */
public class NamingKvStorage extends MemoryKvStorage {
    
    private final String baseDir;
    
    private final KvStorage baseDirStorage;
    
    private final Map<String, KvStorage> namespaceKvStorage;
    
    public NamingKvStorage(final String baseDir) throws Exception {
        this.baseDir = baseDir;
        baseDirStorage = StorageFactory.createKvStorage(KvStorage.KvType.File, "naming-persistent", baseDir);
        namespaceKvStorage = new ConcurrentHashMap<>();
    }
    
    @Override
    public byte[] get(byte[] key) throws KvStorageException {
        byte[] result = super.get(key);
        if (null == result) {
            try {
                KvStorage storage = createActualStorageIfAbsent(key);
                result = null == storage ? null : storage.get(key);
                if (null != result) {
                    super.put(key, result);
                }
            } catch (Exception e) {
                throw new KvStorageException(ErrorCode.KVStorageWriteError.getCode(),
                        "Get data failed, key: " + new String(key), e);
            }
        }
        return result;
    }
    
    @Override
    public Map<byte[], byte[]> batchGet(List<byte[]> keys) throws KvStorageException {
        Map<byte[], byte[]> result = new HashMap<>(keys.size());
        for (byte[] key : keys) {
            byte[] val = get(key);
            if (val != null) {
                result.put(key, val);
            }
        }
        return result;
    }
    
    @Override
    public void put(byte[] key, byte[] value) throws KvStorageException {
        try {
            KvStorage storage = createActualStorageIfAbsent(key);
            storage.put(key, value);
        } catch (Exception e) {
            throw new KvStorageException(ErrorCode.KVStorageWriteError.getCode(),
                    "Put data failed, key: " + new String(key), e);
        }
        // after actual storage put success, put it in memory, memory put should success all the time
        super.put(key, value);
    }
    
    @Override
    public void batchPut(List<byte[]> keys, List<byte[]> values) throws KvStorageException {
        if (keys.size() != values.size()) {
            throw new KvStorageException(ErrorCode.KVStorageBatchWriteError,
                    "key's size must be equal to value's size");
        }
        int size = keys.size();
        for (int i = 0; i < size; i++) {
            put(keys.get(i), values.get(i));
        }
    }
    
    @Override
    public void delete(byte[] key) throws KvStorageException {
        try {
            KvStorage storage = createActualStorageIfAbsent(key);
            if (null != storage) {
                storage.delete(key);
            }
        } catch (Exception e) {
            throw new KvStorageException(ErrorCode.KVStorageDeleteError.getCode(),
                    "Delete data failed, key: " + new String(key), e);
        }
        // after actual storage delete success, put it in memory, memory delete should success all the time
        super.delete(key);
    }
    
    @Override
    public void batchDelete(List<byte[]> key) throws KvStorageException {
        for (byte[] each : key) {
            delete(each);
        }
    }
    
    @Override
    public void doSnapshot(String backupPath) throws KvStorageException {
        baseDirStorage.doSnapshot(backupPath);
    }
    
    @Override
    public void snapshotLoad(String path) throws KvStorageException {
        final long startTime = System.currentTimeMillis();
        baseDirStorage.snapshotLoad(path);
        loadSnapshotFromActualStorage(baseDirStorage);
        loadNamespaceSnapshot();
        long costTime = System.currentTimeMillis() - startTime;
        Loggers.RAFT.info("load snapshot cost time {}ms", costTime);
    }
    
    private void loadSnapshotFromActualStorage(KvStorage actualStorage) throws KvStorageException {
        for (byte[] each : actualStorage.allKeys()) {
            byte[] datum = actualStorage.get(each);
            super.put(each, datum);
        }
    }
    
    private void loadNamespaceSnapshot() {
        for (String each : getAllNamespaceDirs()) {
            try {
                KvStorage kvStorage = createActualStorageIfAbsent(each);
                loadSnapshotFromActualStorage(kvStorage);
            } catch (Exception e) {
                Loggers.RAFT.error("load snapshot for namespace {} failed", each, e);
            }
        }
    }
    
    private List<String> getAllNamespaceDirs() {
        List<String> result = new LinkedList<>();
        File[] files = new File(baseDir).listFiles();
        if (null != files) {
            for (File each : files) {
                if (each.isDirectory()) {
                    result.add(each.getName());
                }
            }
        }
        return result;
    }
    
    @Override
    public List<byte[]> allKeys() throws KvStorageException {
        return super.allKeys();
    }
    
    @Override
    public void shutdown() {
        baseDirStorage.shutdown();
        for (KvStorage each : namespaceKvStorage.values()) {
            each.shutdown();
        }
        namespaceKvStorage.clear();
        super.shutdown();
    }
    
    private KvStorage createActualStorageIfAbsent(byte[] key) throws Exception {
        String keyString = new String(key);
        String namespace = KeyBuilder.getNamespace(keyString);
        return createActualStorageIfAbsent(namespace);
    }
    
    private KvStorage createActualStorageIfAbsent(String namespace) throws Exception {
        if (StringUtils.isBlank(namespace)) {
            return baseDirStorage;
        }
        if (!namespaceKvStorage.containsKey(namespace)) {
            String namespacePath = Paths.get(baseDir, namespace).toString();
            namespaceKvStorage.putIfAbsent(namespace,
                    StorageFactory.createKvStorage(KvStorage.KvType.File, "naming-persistent", namespacePath));
        }
        return namespaceKvStorage.get(namespace);
    }
}
