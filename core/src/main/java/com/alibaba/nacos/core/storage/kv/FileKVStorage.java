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

import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.KVStorageException;
import com.alibaba.nacos.core.utils.DiskUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Kv storage based on file system.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FileKVStorage implements KvStorage {
    
    private final String baseDir;
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    
    public FileKVStorage(String baseDir) {
        this.baseDir = baseDir;
    }
    
    @Override
    public byte[] get(byte[] key) throws KVStorageException {
        readLock.lock();
        try {
            final String fileName = new String(key);
            File file = Paths.get(baseDir, fileName).toFile();
            if (file.exists()) {
                return DiskUtils.readFileBytes(file);
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public Map<byte[], byte[]> batchGet(List<byte[]> keys) throws KVStorageException {
        readLock.lock();
        try {
            Map<byte[], byte[]> result = new HashMap<>(keys.size());
            for (byte[] key : keys) {
                byte[] val = get(key);
                if (val != null) {
                    result.put(key, val);
                }
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void put(byte[] key, byte[] value) throws KVStorageException {
        readLock.lock();
        try {
            final String fileName = new String(key);
            File file = Paths.get(baseDir, fileName).toFile();
            try {
                DiskUtils.touch(file);
                DiskUtils.writeFile(file, value, false);
            } catch (IOException e) {
                throw new KVStorageException(ErrorCode.KVStorageWriteError, e);
            }
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void batchPut(List<byte[]> keys, List<byte[]> values) throws KVStorageException {
        readLock.lock();
        try {
            if (keys.size() != values.size()) {
                throw new KVStorageException(ErrorCode.KVStorageBatchWriteError,
                        "key's size must be equal to value's size");
            }
            int size = keys.size();
            for (int i = 0; i < size; i++) {
                put(keys.get(i), values.get(i));
            }
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void delete(byte[] key) throws KVStorageException {
        readLock.lock();
        try {
            final String fileName = new String(key);
            File file = Paths.get(baseDir, fileName).toFile();
            if (file.exists()) {
                file.delete();
            }
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void batchDelete(List<byte[]> keys) throws KVStorageException {
        readLock.lock();
        try {
            for (byte[] key : keys) {
                delete(key);
            }
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void doSnapshot(String backupPath) throws KVStorageException {
        writeLock.lock();
        try {
            File srcDir = Paths.get(baseDir).toFile();
            File descDir = Paths.get(backupPath).toFile();
            DiskUtils.copyDirectory(srcDir, descDir);
        } catch (IOException e) {
            throw new KVStorageException(ErrorCode.IOCopyDirError, e);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void snapshotLoad(String path) throws KVStorageException {
        writeLock.lock();
        try {
            // First clean up the local file information, before the file copy
            DiskUtils.deleteDirThenMkdir(baseDir);
            File srcDir = Paths.get(path).toFile();
            File descDir = Paths.get(baseDir).toFile();
            DiskUtils.copyDirectory(srcDir, descDir);
        } catch (IOException e) {
            throw new KVStorageException(ErrorCode.IOCopyDirError, e);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void shutdown() {
    
    }
}
