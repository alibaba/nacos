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

package com.alibaba.nacos.core.exception;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * RocksDB Exception.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class KvStorageException extends NacosException {
    
    public KvStorageException() {
        super();
    }
    
    public KvStorageException(ErrorCode code, String errMsg) {
        super(code.getCode(), errMsg);
    }
    
    public KvStorageException(ErrorCode errCode, Throwable throwable) {
        super(errCode.getCode(), throwable);
    }
    
    public KvStorageException(ErrorCode errCode, String errMsg, Throwable throwable) {
        super(errCode.getCode(), errMsg, throwable);
    }
    
    public KvStorageException(int errCode, String errMsg) {
        super(errCode, errMsg);
    }
    
    public KvStorageException(int errCode, Throwable throwable) {
        super(errCode, throwable);
    }
    
    public KvStorageException(int errCode, String errMsg, Throwable throwable) {
        super(errCode, errMsg, throwable);
    }
    
    @Override
    public int getErrCode() {
        return super.getErrCode();
    }
    
    @Override
    public String getErrMsg() {
        return super.getErrMsg();
    }
    
    @Override
    public void setErrCode(int errCode) {
        super.setErrCode(errCode);
    }
    
    @Override
    public void setErrMsg(String errMsg) {
        super.setErrMsg(errMsg);
    }
    
    @Override
    public void setCauseThrowable(Throwable throwable) {
        super.setCauseThrowable(throwable);
    }
    
    @Override
    public String toString() {
        return super.toString();
    }
}
