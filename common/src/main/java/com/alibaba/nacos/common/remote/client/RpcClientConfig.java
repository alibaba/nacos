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

package com.alibaba.nacos.common.remote.client;

import java.util.Map;

/**
 * RpcClientConfig.
 *
 * @author karsonto
 */
public interface RpcClientConfig {
    
    /**
     * get name.
     *
     * @return name.
     */
    String name();
    
    /**
     * get request retry times.
     *
     * @return retryTimes.
     */
    int retryTimes();
    
    /**
     * get time out mills.
     *
     * @return timeOutMills.
     */
    long timeOutMills();
    
    /**
     * get connection keep alive time.
     *
     * @return connectionKeepAlive.
     */
    long connectionKeepAlive();
    
    /**
     * get health check retry times.
     *
     * @return healthCheckRetryTimes.
     */
    int healthCheckRetryTimes();
    
    /**
     * get health check time out.
     *
     * @return healthCheckTimeOut.
     */
    long healthCheckTimeOut();
    
    /**
     * get map of labels.
     *
     * @return labels.
     */
    Map<String, String> labels();
    
}
