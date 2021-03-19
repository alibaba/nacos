/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.executor;

import com.alibaba.nacos.naming.pojo.Subscriber;

/**
 * Nacos naming push executor for SPI.
 *
 * @author xiweng.yy
 */
public interface SpiPushExecutor extends PushExecutor {
    
    /**
     * Whether SPI push executor is interest this push.
     *
     * @param clientId   client id of push
     * @param subscriber subscribe info
     * @return {@code true} if this SPI push executor should execute, otherwise false.
     */
    boolean isInterest(String clientId, Subscriber subscriber);
}
