/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
import java.util.Objects;

/**
 * lock key type and key name.
 *
 * @author 985492783@qq.com
 * @date 2023/9/7 21:31
 */
public class LockKey implements Serializable {
    
    private static final long serialVersionUID = -3460548121526875524L;
    
    public LockKey(String lockType, String key) {
        this.lockType = lockType;
        this.key = key;
    }
    
    private String lockType;
    
    private String key;
    
    public String getLockType() {
        return lockType;
    }
    
    public void setLockType(String lockType) {
        this.lockType = lockType;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LockKey lockKey = (LockKey) o;
        return Objects.equals(lockType, lockKey.lockType) && Objects.equals(key, lockKey.key);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(lockType, key);
    }
}
