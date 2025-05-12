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

package com.alibaba.nacos.client.lock.core;

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;

/**
 * Nacos client lock entity.
 *
 * @author 985492783@qq.com
 * @date 2023/8/24 19:52
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class NLock extends LockInstance {
    
    private static final long serialVersionUID = -346054842454875524L;
    
    public NLock(String key, Long expireTimestamp) {
        super(key, expireTimestamp, LockConstants.NACOS_LOCK_TYPE);
    }
}
