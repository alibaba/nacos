/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;

/**
 * Client operation service.
 *
 * @author xiweng.yy
 */
public interface ClientOperationService {
    
    /**
     * Register instance to service.
     *
     * @param service  service
     * @param instance instance
     * @param clientId id of client
     */
    void registerInstance(Service service, Instance instance, String clientId);
    
    /**
     * Deregister instance from service.
     *
     * @param service  service
     * @param instance instance
     * @param clientId id of client
     */
    void deregisterInstance(Service service, Instance instance, String clientId);
    
    /**
     * Subscribe a service.
     *
     * @param service    service
     * @param subscriber subscribe
     * @param clientId   id of client
     */
    void subscribeService(Service service, Subscriber subscriber, String clientId);
    
    /**
     * Unsubscribe a service.
     *
     * @param service    service
     * @param subscriber subscribe
     * @param clientId   id of client
     */
    void unsubscribeService(Service service, Subscriber subscriber, String clientId);
}
