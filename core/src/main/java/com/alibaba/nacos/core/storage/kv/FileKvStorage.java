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

package com.alibaba.nacos.core.storage.kv;

import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.sys.utils.DiskUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Kv storage based on file system. // TODO 写文件的方式需要优化
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FileKvStorage implements KvStorage {
    
    private final String baseDir;
    
    /**
     * Ensure that a consistent view exists when implementing file copies.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    
    public FileKvStorage(String baseDir) throws IOException {
        this.baseDir = baseDir;
        DiskUtils.forceMkdir(baseDir);
    }
    
    @Override
    public byte[] get(byte[] key) throws KvStorageException {
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
    public Map<byte[], byte[]> batchGet(List<byte[]> keys) throws KvStorageException {
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
    public void put(byte[] key, byte[] value) throws KvStorageException {
        readLock.lock();
        try {
            final String fileName = new String(key);
            File file = Paths.get(baseDir, fileName).toFile();
            try {
                DiskUtils.touch(file);
                DiskUtils.writeFile(file, value, false);
            } catch (IOException e) {
                throw new KvStorageException(ErrorCode.KVStorageWriteError, e);
            }
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void batchPut(List<byte[]> keys, List<byte[]> values) throws KvStorageException {
        readLock.lock();
        try {
            if (keys.size() != values.size()) {
                throw new KvStorageException(ErrorCode.KVStorageBatchWriteError,
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
    public void delete(byte[] key) throws KvStorageException {
        readLock.lock();
        try {
            final String fileName = new String(key);
            DiskUtils.deleteFile(baseDir, fileName);
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public void batchDelete(List<byte[]> keys) throws KvStorageException {
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
    public void doSnapshot(String backupPath) throws KvStorageException {
        writeLock.lock();
        try {
            File srcDir = Paths.get(baseDir).toFile();
            File descDir = Paths.get(backupPath).toFile();
            DiskUtils.copyDirectory(srcDir, descDir);
        } catch (IOException e) {
            throw new KvStorageException(ErrorCode.IOCopyDirError, e);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void snapshotLoad(String path) throws KvStorageException {
        writeLock.lock();
        try {
            File srcDir = Paths.get(path).toFile();
            // If snapshot path is non-exist, means snapshot is empty
            if (srcDir.exists()) {
                // First clean up the local file information, before the file copy
                DiskUtils.deleteDirThenMkdir(baseDir);
                File descDir = Paths.get(baseDir).toFile();
                DiskUtils.copyDirectory(srcDir, descDir);
            }
        } catch (IOException e) {
            throw new KvStorageException(ErrorCode.IOCopyDirError, e);
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public List<byte[]> allKeys() throws KvStorageException {
        List<byte[]> result = new LinkedList<>();
        File[] files = new File(baseDir).listFiles();
        if (null != files) {
            for (File each : files) {
                if (each.isFile()) {
                    result.add(ByteUtils.toBytes(each.getName()));
                }
            }
        }
        return result;
    }
    
    @Override
    public void shutdown() {
    }
}