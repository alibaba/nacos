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

import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;

/**
 * Nacos naming push executor for v2.
 *
 * @author xiweng.yy
 */
public interface PushExecutor {
    
    /**
     * Do push.
     *
     * @param clientId   client id
     * @param subscriber subscriber
     * @param data       push data
     */
    void doPush(String clientId, Subscriber subscriber, PushDataWrapper data);
    
    /**
     * Do push with callback.
     *
     * @param clientId   client id
     * @param subscriber subscriber
     * @param data       push data
     * @param callBack   callback
     */
    void doPushWithCallback(String clientId, Subscriber subscriber, PushDataWrapper data, PushCallBack callBack);
}
