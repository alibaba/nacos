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

package com.alibaba.nacos.lock.service;

import com.alibaba.nacos.api.lock.model.LockInstance;

/**
 * lock operator service.
 *
 * @author 985492783@qq.com
 * @date 2023/6/28 2:38
 */
public interface LockOperationService {
    
    /**
     * get lock operator.
     *
     * @param lockInstance lockInstance
     * @return boolean
     */
    Boolean lock(LockInstance lockInstance);
    
    /**
     * unLock.
     *
     * @param lockInstance lockInstance
     * @return Boolean
     */
    Boolean unLock(LockInstance lockInstance);
    
}
