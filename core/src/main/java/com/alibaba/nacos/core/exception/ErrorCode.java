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
    
    UnKnowError(40001),
    
    RocksDBWriteError(40100),
    
    RocksDBReadError(40101),
    
    RocksDBDeleteError(40102),
    
    RocksDBSnapshotSaveError(40103),
    
    RocksDBSnapshotLoadError(40104),
    
    RocksDBResetError(40105),
    
    RocksDBCreateError(40106),
    
    // disk error
    
    IOMakeDirError(40201),
    
    // consistency protocol
    
    ProtoSubmitError(40301),
    
    ProtoReadError(40302),
    ;
    
    private final int code;
    
    ErrorCode(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
}
