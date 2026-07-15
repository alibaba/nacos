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

package com.alibaba.nacos.api.lock.remote.request;

import com.alibaba.nacos.api.lock.common.LockNotificationType;
import com.alibaba.nacos.api.remote.request.ServerRequest;

import static com.alibaba.nacos.api.common.Constants.Lock.LOCK_MODULE;

/**
 * Server push notification to client when a lock becomes available.
 *
 * <p>When a lock is released or expires, the server pushes this notification
 * to waiting clients so they can retry acquiring the lock.
 *
 * @author DHX
 * @date 2026/05/29
 */
public class LockNotificationRequest extends ServerRequest {
    
    private String lockKey;
    
    private String lockType;
    
    private String owner;
    
    private LockNotificationType notificationType;
    
    public LockNotificationRequest() {
    }
    
    public LockNotificationRequest(String lockKey, String lockType, String owner,
        LockNotificationType notificationType) {
        this.lockKey = lockKey;
        this.lockType = lockType;
        this.owner = owner;
        this.notificationType = notificationType;
    }
    
    public static LockNotificationRequest available(String lockKey, String lockType, String owner) {
        return new LockNotificationRequest(lockKey, lockType, owner,
            LockNotificationType.AVAILABLE);
    }
    
    public static LockNotificationRequest timeout(String lockKey, String lockType, String owner) {
        return new LockNotificationRequest(lockKey, lockType, owner, LockNotificationType.TIMEOUT);
    }
    
    @Override
    public String getModule() {
        return LOCK_MODULE;
    }
    
    public String getLockKey() {
        return lockKey;
    }
    
    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
    
    public String getLockType() {
        return lockType;
    }
    
    public void setLockType(String lockType) {
        this.lockType = lockType;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public LockNotificationType getNotificationType() {
        return notificationType;
    }
    
    public void setNotificationType(LockNotificationType notificationType) {
        this.notificationType = notificationType;
    }
}
