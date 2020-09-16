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
    
    // kv error
    
    /**
     * KVStorage write error.
     */
    KVStorageWriteError(40100),
    
    /**
     * KVStorage read error.
     */
    KVStorageReadError(40101),
    
    /**
     * KVStorage delete error.
     */
    KVStorageDeleteError(40102),
    
    /**
     * KVStorage snapshot save error.
     */
    KVStorageSnapshotSaveError(40103),
    
    /**
     * KVStorage snapshot load error.
     */
    KVStorageSnapshotLoadError(40104),
    
    /**
     * KVStorage reset error.
     */
    KVStorageResetError(40105),
    
    /**
     * KVStorage create error.
     */
    KVStorageCreateError(40106),
    
    /**
     * KVStorage write error.
     */
    KVStorageBatchWriteError(40107),
    
    // disk error
    
    /**
     * mkdir error.
     */
    IOMakeDirError(40201),
    
    /**
     * copy directory has error.
     */
    IOCopyDirError(40202),
    
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
