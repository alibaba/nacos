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

/**
 * NLock factory.
 *
 * @author 985492783@qq.com
 * @date 2023/8/27 15:23
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class NLockFactory {
    
    /**
     * create NLock without expireTime.
     *
     * @param key key
     * @return NLock
     */
    public static NLock getLock(String key) {
        return new NLock(key, -1L);
    }
    
    /**
     * create NLock with expireTime.
     *
     * @param key key
     * @return NLock
     */
    public static NLock getLock(String key, Long expireTimestamp) {
        return new NLock(key, expireTimestamp);
    }
}
