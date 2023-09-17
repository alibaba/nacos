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

package com.alibaba.nacos.api.lock.model;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.LockService;

import java.io.Serializable;
import java.util.Map;

/**
 * lock info entity.
 *
 * @author 985492783@qq.com
 * @date 2023/6/28 2:46
 */
public class LockInstance implements Serializable {
    
    private static final long serialVersionUID = -3460985546826875524L;
    
    private String key;
    
    private Long expiredTime;
    
    private Map<String, ? extends Serializable> params;
    
    private String lockType;
    
    public LockInstance(String key, Long expiredTime, String lockType) {
        this.key = key;
        this.expiredTime = expiredTime;
        this.lockType = lockType;
    }
    
    public LockInstance() {
    }
    
    public Long getExpiredTime() {
        return expiredTime;
    }
    
    public void setExpiredTime(Long expiredTime) {
        this.expiredTime = expiredTime;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public Map<String, ? extends Serializable> getParams() {
        return params;
    }
    
    public void setParams(Map<String, ? extends Serializable> params) {
        this.params = params;
    }
    
    /**
     * Will call {@link LockService#remoteTryLock(LockInstance)} request grpc to get lock and do something.<br/> can be
     * {@link Override} to do some client special logic.
     *
     * @param lockService {@link LockService}
     * @return Boolean {@link Boolean}
     * @throws NacosException NacosException
     */
    public Boolean lock(LockService lockService) throws NacosException {
        return lockService.remoteTryLock(this);
    }
    
    /**
     * Will call {@link LockService#remoteReleaseLock(LockInstance)} request grpc to release lock and do something.<br/>
     * can be {@link Override} to do some client special logic.
     *
     * @param lockService {@link LockService}
     * @return Boolean {@link Boolean}
     * @throws NacosException NacosException
     */
    public Boolean unLock(LockService lockService) throws NacosException {
        return lockService.remoteReleaseLock(this);
    }
    
    /**
     * spi get lock type.
     *
     * @return type
     */
    public String getLockType() {
        return lockType;
    }
    
    public void setLockType(String lockType) {
        this.lockType = lockType;
    }
}
