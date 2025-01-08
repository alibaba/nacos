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
import java.util.Map;

/**
 * lock info DTO.
 *
 * @author 985492783@qq.com
 * @date 2023/9/17 14:20
 */
public class LockInfo implements Serializable {
    
    private static final long serialVersionUID = -3460985546826875524L;
    
    private LockKey key;
    
    private Long endTime;
    
    private Map<String, ? extends Serializable> params;
    
    public LockInfo() {
    }
    
    public LockKey getKey() {
        return key;
    }
    
    public void setKey(LockKey key) {
        this.key = key;
    }
    
    public Long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }
    
    public Map<String, ? extends Serializable> getParams() {
        return params;
    }
    
    public void setParams(Map<String, ? extends Serializable> params) {
        this.params = params;
    }
}
