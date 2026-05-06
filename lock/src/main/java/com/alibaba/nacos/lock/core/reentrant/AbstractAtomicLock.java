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

package com.alibaba.nacos.lock.core.reentrant;

import java.io.Serializable;

/**
 * abstract atomic lock.
 *
 * @author 985492783@qq.com
 * @description AtomicLock
 * @date 2023/7/10 14:50
 */
public abstract class AbstractAtomicLock implements AtomicLockService, Serializable {
    
    private static final long serialVersionUID = -3460985546856855524L;
    
    private final String key;
    
    public AbstractAtomicLock(String key) {
        this.key = key;
    }
    
    @Override
    public String getKey() {
        return key;
    }
}
