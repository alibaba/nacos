/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.model;

import java.io.Serializable;

/**
 * Represents a client waiting to acquire a lock.
 *
 * <p>Stored in the lock's wait queue. When the lock becomes available,
 * the server pushes a notification to the waiting client via gRPC.
 *
 * @author DHX
 * @date 2026/05/29 23:00
 */
public class WaitEntry implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String owner;
    
    private String connectionId;
    
    private long enqueueTime;
    
    private long waitDeadline;
    
    public WaitEntry() {
    }
    
    public WaitEntry(String owner, String connectionId, long enqueueTime, long waitDeadline) {
        this.owner = owner;
        this.connectionId = connectionId;
        this.enqueueTime = enqueueTime;
        this.waitDeadline = waitDeadline;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getConnectionId() {
        return connectionId;
    }
    
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
    public long getEnqueueTime() {
        return enqueueTime;
    }
    
    public void setEnqueueTime(long enqueueTime) {
        this.enqueueTime = enqueueTime;
    }
    
    public long getWaitDeadline() {
        return waitDeadline;
    }
    
    public void setWaitDeadline(long waitDeadline) {
        this.waitDeadline = waitDeadline;
    }
    
    public boolean isExpired() {
        return waitDeadline > 0 && System.currentTimeMillis() >= waitDeadline;
    }
}
