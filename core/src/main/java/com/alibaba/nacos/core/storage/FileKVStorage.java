package com.alibaba.nacos.core.storage;

import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.KVStorageException;
import com.alibaba.nacos.core.utils.DiskUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * File based KV storage
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FileKVStorage implements KvStorage {
    
    private String baseDir;
    
    public void init(String path) {
        this.baseDir = path;
    }
    
    @Override
    public byte[] get(byte[] key) throws KVStorageException {
        final String fileName = new String(key);
        File file = Paths.get(baseDir, fileName).toFile();
        if (file.exists()) {
            return DiskUtils.readFileBytes(file);
        }
        return null;
    }
    
    @Override
    public Map<byte[], byte[]> batchGet(List<byte[]> keys) throws KVStorageException {
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
    public void put(byte[] key, byte[] value) throws KVStorageException {
        final String fileName = new String(key);
        File file = Paths.get(baseDir, fileName).toFile();
        try {
            DiskUtils.touch(file);
        } catch (IOException e) {
            throw new KVStorageException(ErrorCode.KVStorageWriteError.getCode(), "create file failed");
        }
        DiskUtils.writeFile(file, value, false);
    }
    
    @Override
    public void batchPut(List<byte[]> keys, List<byte[]> values) throws KVStorageException {
        if (keys.size() != values.size()) {
            throw new KVStorageException(ErrorCode.KVStorageBatchWriteError.getCode(),
                    "key's size must be equal to value's size");
        }
        int size = keys.size();
        for (int i = 0; i < size; i ++) {
            put(keys.get(i), values.get(i));
        }
    }
    
    @Override
    public void delete(byte[] key) throws KVStorageException {
        final String fileName = new String(key);
        File file = Paths.get(baseDir, fileName).toFile();
        if (file.exists()) {
            file.delete();
        }
    }
    
    @Override
    public void batchDelete(List<byte[]> keys) throws KVStorageException {
        for (byte[] key : keys) {
            delete(key);
        }
    }
    
    @Override
    public void shutdown() {
    
    }
}
