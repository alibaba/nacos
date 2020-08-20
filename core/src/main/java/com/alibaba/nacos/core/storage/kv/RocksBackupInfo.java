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

/**
 * RocksDB backup info.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RocksBackupInfo {
    
    private int backupId;
    
    private long timestamp;
    
    private long size;
    
    private int numberFiles;
    
    private String appMetadata;
    
    public int getBackupId() {
        return backupId;
    }
    
    public void setBackupId(int backupId) {
        this.backupId = backupId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    public int getNumberFiles() {
        return numberFiles;
    }
    
    public void setNumberFiles(int numberFiles) {
        this.numberFiles = numberFiles;
    }
    
    public String getAppMetadata() {
        return appMetadata;
    }
    
    public void setAppMetadata(String appMetadata) {
        this.appMetadata = appMetadata;
    }
    
    @Override
    public String toString() {
        return "RocksBackupInfo{" + "backupId=" + backupId + ", timestamp=" + timestamp + ", size=" + size
                + ", numberFiles=" + numberFiles + ", appMetadata='" + appMetadata + '\'' + '}';
    }
}
