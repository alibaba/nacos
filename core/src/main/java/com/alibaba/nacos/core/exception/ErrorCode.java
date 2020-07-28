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

package com.alibaba.nacos.core.exception;

/**
 * Core module code starts with 40001.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public enum ErrorCode {
    
    /**
     * unknow error.
     */
    UnKnowError(40001),
    
    // rocksdb error
    
    /**
     * rocksdb write error.
     */
    RocksDBWriteError(40100),
    
    /**
     * rocksdb read error.
     */
    RocksDBReadError(40101),
    
    /**
     * rocksdb delete error.
     */
    RocksDBDeleteError(40102),
    
    /**
     * rocksdb snapshot save error.
     */
    RocksDBSnapshotSaveError(40103),
    
    /**
     * rocksdb snapshot load error.
     */
    RocksDBSnapshotLoadError(40104),
    
    /**
     * rocksdb reset error.
     */
    RocksDBResetError(40105),
    
    /**
     * rocksdb create error.
     */
    RocksDBCreateError(40106),
    
    // disk error
    
    /**
     * mkdir error.
     */
    IOMakeDirError(40201),
    
    // consistency protocol error
    
    /**
     * protocol write error.
     */
    ProtoSubmitError(40301),
    
    /**
     * protocol read error.
     */
    ProtoReadError(40302);
    
    private final int code;
    
    ErrorCode(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
}
