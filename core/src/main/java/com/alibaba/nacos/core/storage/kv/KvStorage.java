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

package com.alibaba.nacos.core.storage.kv;

import com.alibaba.nacos.core.exception.KVStorageException;

import java.util.List;
import java.util.Map;

/**
 * Universal KV storage interface
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface KvStorage {
    
    enum KVType {
        File,
        
        Memory,
        
        RocksDB,
    }
    
    
    /**
     * get data by key
     *
     * @param key byte[]
     * @return byte[]
     * @throws KVStorageException KVStorageException
     */
    byte[] get(byte[] key) throws KVStorageException;
    
    /**
     * batch get by List byte[].
     *
     * @param keys List byte[]
     * @return Map byte[], byte[]
     * @throws KVStorageException RocksStorageException
     */
    Map<byte[], byte[]> batchGet(List<byte[]> keys) throws KVStorageException;
    
    /**
     * write data.
     *
     * @param key byte[]
     * @param value byte[]
     * @throws KVStorageException RocksStorageException
     */
    void put(byte[] key, byte[] value) throws KVStorageException;
    
    /**
     * batch write.
     *
     * @param key List byte[]
     * @param values List byte[]
     * @throws KVStorageException RocksStorageException
     */
    void batchPut(List<byte[]> key, List<byte[]> values) throws KVStorageException;
    
    /**
     * delete with key.
     *
     * @param key byte[]
     * @throws KVStorageException RocksStorageException
     */
    void delete(byte[] key) throws KVStorageException;
    
    /**
     * batch delete with keys.
     *
     * @param key List byte[]
     * @throws KVStorageException RocksStorageException
     */
    void batchDelete(List<byte[]> key) throws KVStorageException;
    
    /**
     * do snapshot.
     *
     * @param backupPath snapshot file save path
     * @throws KVStorageException KVStorageException
     */
    void doSnapshot(final String backupPath) throws KVStorageException;
    
    /**
     * load snapshot.
     *
     * @param path The path to the snapshot file
     * @throws KVStorageException KVStorageException
     */
    void snapshotLoad(String path) throws KVStorageException;
    
    /**
     * shutdown.
     */
    void shutdown();
    
}
