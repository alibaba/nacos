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

package com.alibaba.nacos.lock.raft.request;

import com.alibaba.nacos.lock.model.LockInfo;

import java.io.Serializable;

/**
 * mutex lock request.
 *
 * @author 985492783@qq.com
 * @date 2023/8/24 18:40
 */
public class MutexLockRequest implements Serializable {
    
    private static final long serialVersionUID = -925543547156890549L;
    
    private LockInfo lockInfo;
    
    public LockInfo getLockInfo() {
        return lockInfo;
    }
    
    public void setLockInfo(LockInfo lockInfo) {
        this.lockInfo = lockInfo;
    }
}
